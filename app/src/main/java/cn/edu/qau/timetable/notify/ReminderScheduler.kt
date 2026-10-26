package cn.edu.qau.timetable.notify

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.repo.TimetableRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 上课提醒。
 *
 * 采用 AlarmManager（而不是 WorkManager）：课程提醒对时间精度敏感，
 * WorkManager 的 15 分钟最小间隔和 deferrable 语义不适合。
 *
 * 只排未来 7 天，并在开机 / 改设置 / 导入课表后重排 —— 避免一次性排几百个闹钟。
 *
 * ## 为什么要记「为什么没排上」（v3.6）
 *
 * 排程有多达 6 个早退条件（开关关了 / 没有学期行 / 第 1 周周一没填 /
 * 一条课都没有 / 7 天内没课 / 精确闹钟权限没有）。**每一个都是静默 return**：
 * 界面上的开关是开着的，用户却收不到提醒，而且没有任何地方能看出原因。
 *
 * 用户报「提醒失效」时，从代码里根本分不清是没排、排了没响、还是权限问题。
 * 所以排程结束统一落一条 [ScheduleStatus]，设置页把它显示出来 ——
 * 这比在 issue 里来回猜要省事得多。
 */
class ReminderScheduler(
    private val context: Context,
    private val repo: TimetableRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun reschedule() {
        scope.launch {
            runCatching { doReschedule() }
        }
    }

    private suspend fun doReschedule() {
        val settings = repo.settingsFlow.first()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(alarmManager)

        if (!settings.remindEnabled) {
            ScheduleStatus.save(context, ScheduleStatus.Kind.REMINDER, "提醒开关是关的")
            return
        }
        val termEntity = repo.activeTermEntity()
        if (termEntity == null) {
            ScheduleStatus.save(context, ScheduleStatus.Kind.REMINDER, "还没有课表：先在「同步」页抓一次")
            return
        }
        // 学期行可能还没填「第 1 周周一」（算不出周次），这时排不了提醒
        val term = termEntity.toDomain()
        if (term == null) {
            ScheduleStatus.save(
                context, ScheduleStatus.Kind.REMINDER,
                "第 1 周周一没填对，算不出现在第几周 —— 去「学期」卡里补上",
            )
            return
        }
        val courses = repo.coursesNow(termEntity.id)
        if (courses.isEmpty()) {
            ScheduleStatus.save(context, ScheduleStatus.Kind.REMINDER, "本学期课表是空的")
            return
        }

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val scheduled = ArrayList<Int>()
        var exact = false

        for (offset in 0..6) {
            val date = today.plusDays(offset.toLong())
            val week = term.weekOf(date)
            if (week <= 0 || week > term.totalWeeks) continue
            // 调休：补课日按被借的那天的课表排提醒；放假日不排
            val dow = cn.edu.qau.timetable.core.DayOverrides
                .effectiveDayOfWeek(date, settings.dayOverrides) ?: continue

            courses.filter { it.dayOfWeek == dow && it.occursIn(week) }.forEach { course ->
                val start = PeriodTimes.startOf(term.campus, course.startPeriod) ?: return@forEach
                val trigger = date.atTime(start)
                    .minusMinutes(settings.remindMinutesBefore.toLong())
                if (!trigger.isAfter(now)) return@forEach

                val requestCode = requestCodeOf(course.id, date)
                val intent = ReminderReceiver.intentFor(
                    context = context,
                    courseName = course.name,
                    room = course.room,
                    teacher = course.teacher,
                    startPeriod = course.startPeriod,
                    endPeriod = course.endPeriod,
                    campusLabel = term.campus.label,
                    minutesBefore = settings.remindMinutesBefore,
                )
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                )
                val millis = trigger.atZone(zone).toInstant().toEpochMilli()
                try {
                    if (canScheduleExact(alarmManager)) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                        exact = true
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pending)
                    }
                    scheduled += requestCode
                } catch (t: Throwable) {
                    // 精确闹钟被系统拒绝时降级为非精确，不让整个排程失败
                    runCatching {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, millis, pending)
                        scheduled += requestCode
                    }
                }
            }
        }

        ScheduledAlarms.save(context, ScheduledAlarms.KEY_REMINDER, scheduled)
        // 不管这次排了几个，都要把下一次续排定上：
        // 只排 7 天，而「开机 / 改设置 / 导课表」这些触发点用户可能一周都碰不到。
        // 没这一步的话，第 8 天起就没有任何提醒了（静默断档，见 ChainReceiver 的说明）。
        runCatching { ChainReceiver.schedule(context) }
        ScheduleStatus.save(
            context, ScheduleStatus.Kind.REMINDER,
            if (scheduled.isEmpty()) {
                "未来 7 天没有可提醒的课（放假、或课都在今天更早的时间）"
            } else {
                buildString {
                    append("已排 ")
                    append(scheduled.size)
                    append(" 个提醒")
                    if (!exact) append("（非精确：系统未授予精确闹钟权限，可能晚几分钟）")
                }
            },
        )
    }

    /**
     * 能不能用精确闹钟。
     *
     * Android 12+ 需要 `canScheduleExactAlarms()`；但 Android 14 起
     * `SCHEDULE_EXACT_ALARM` 对**新安装**默认是拒绝的，用户得去
     * 「设置 → 应用 → 特殊权限 → 闹钟和提醒」手动开。
     * 所以这里不能只看 SDK 版本就假定能用。
     */
    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || runCatching {
            am.canScheduleExactAlarms()
        }.getOrDefault(false)

    private fun cancelAll(alarmManager: AlarmManager) {
        for (code in ScheduledAlarms.load(context, ScheduledAlarms.KEY_REMINDER)) {
            val intent = android.content.Intent(context, ReminderReceiver::class.java)
            val pending = android.app.PendingIntent.getBroadcast(
                context, code, intent,
                android.app.PendingIntent.FLAG_NO_CREATE or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        ScheduledAlarms.save(context, ScheduledAlarms.KEY_REMINDER, emptyList())
    }

    private fun requestCodeOf(courseId: Long, date: LocalDate): Int =
        ((courseId * 1000L + date.dayOfYear) % Int.MAX_VALUE).toInt()
}
