package cn.edu.qau.timetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.qau.timetable.core.PeriodTimes
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

        if (!settings.remindEnabled) return
        val termEntity = repo.activeTermEntity() ?: return
        val term = termEntity.toDomain()
        val courses = repo.coursesNow(termEntity.id)
        if (courses.isEmpty()) return

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val scheduled = ArrayList<Int>()

        for (offset in 0..6) {
            val date = today.plusDays(offset.toLong())
            val week = term.weekOf(date)
            if (week <= 0 || week > term.totalWeeks) continue
            val dow = date.dayOfWeek.value

            courses.filter { it.dayOfWeek == dow && it.occursIn(week) }.forEach { course ->
                val start = PeriodTimes.startOf(term.campus, course.startPeriod) ?: return@forEach
                val trigger = date.atTime(start)
                    .minusMinutes(settings.remindMinutesBefore.toLong())
                if (!trigger.isAfter(now)) return@forEach

                val requestCode = requestCodeOf(course.id, date)
                val intent = ReminderReceiver.intentFor(
                    context = context,
                    requestCode = requestCode,
                    courseName = course.name,
                    room = course.room,
                    teacher = course.teacher,
                    startPeriod = course.startPeriod,
                    endPeriod = course.endPeriod,
                    campusLabel = term.campus.label,
                    minutesBefore = settings.remindMinutesBefore,
                )
                val pending = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val millis = trigger.atZone(zone).toInstant().toEpochMilli()
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
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

        ScheduledAlarms.save(context, scheduled)
    }

    private fun cancelAll(alarmManager: AlarmManager) {
        for (code in ScheduledAlarms.load(context)) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        ScheduledAlarms.save(context, emptyList())
    }

    private fun requestCodeOf(courseId: Long, date: LocalDate): Int =
        ((courseId * 1000L + date.dayOfYear) % Int.MAX_VALUE).toInt()
}
