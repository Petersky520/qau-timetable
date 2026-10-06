package cn.edu.qau.timetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.qau.timetable.core.SilenceMode
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
 * 上课自动静音。
 *
 * 与 [ReminderScheduler] 一样走 AlarmManager，只排未来 7 天，
 * 在开机 / 改设置 / 导入课表后重排 —— 不一次性排几百个闹钟。
 *
 * 每个静音时段排两个闹钟：开始静音、下课还原。
 * 时段由 [SilencePlan] 算出（课间的短间隔会被合并，午休/晚饭不会），
 * 那部分是纯逻辑、有单测覆盖。
 *
 * 通知上那个「上课静音」按钮走的是同一个 [SilencePlan]，
 * 所以"点按钮提前静音"和"到点自动静音"恢复铃声的时刻是一致的。
 */
class SilenceScheduler(
    private val context: Context,
    private val repo: TimetableRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun reschedule() {
        scope.launch {
            runCatching { doReschedule() }
        }
    }

    /** 关掉功能时调用：撤销所有静音闹钟，并把铃声还原回去。 */
    fun stop() {
        scope.launch {
            runCatching {
                cancelAll(alarmManager())
                RingerModeController.restore(context)
            }
        }
    }

    private fun alarmManager() =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private suspend fun doReschedule() {
        val settings = repo.settingsFlow.first()
        val am = alarmManager()
        cancelAll(am)

        // 关掉功能、或用户撤销了「通知策略访问权限」时都不该继续排，
        // 而且要把可能残留的静音还原掉 —— 否则手机会一直哑着。
        if (!settings.silenceEnabled || !RingerModeController.isGranted(context)) {
            RingerModeController.restore(context)
            return
        }

        val termEntity = repo.activeTermEntity() ?: return
        val term = termEntity.toDomain()
        val courses = repo.coursesNow(termEntity.id)
        if (courses.isEmpty()) return

        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val mode = settings.silenceMode
        val scheduled = ArrayList<Int>()

        for (offset in 0..6) {
            val date = today.plusDays(offset.toLong())
            val week = term.weekOf(date)
            if (week <= 0 || week > term.totalWeeks) continue

            val spans = SilencePlan.spansOn(term.campus, courses, date, week)
            spans.forEach { span ->
                val startAt = date.atTime(span.start)
                val endAt = date.atTime(span.end)
                // 整段都过完了，跳过
                if (!endAt.isAfter(now)) return@forEach

                // 静音闹钟只给还没开始的时段排：刚打开开关时不该"突然静音"。
                if (startAt.isAfter(now)) {
                    scheduleOne(am, scheduled, startAt, mode, restore = false)
                }
                // 还原闹钟则**正在进行的时段也要排**。以前这里跟着静音闹钟一起跳过，
                // 于是任何一次重排（改设置 / 重启 / 重新导入课表）都会把还挂着的还原
                // 闹钟 cancelAll 掉又没人补上，正在上课的手机就一路哑到下次重排为止。
                scheduleOne(am, scheduled, endAt, mode, restore = true)
            }
        }

        ScheduledAlarms.save(context, ScheduledAlarms.KEY_SILENCE, scheduled)
    }

    private fun scheduleOne(
        am: AlarmManager,
        scheduled: MutableList<Int>,
        at: LocalDateTime,
        mode: SilenceMode,
        restore: Boolean,
    ) {
        val code = SilencePlan.codeOf(at.toLocalDate(), at.toLocalTime())
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            SilenceReceiver.intentFor(context, restore, mode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = at.atZone(zone()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, millis, pending)
            }
            scheduled += code
        } catch (t: Throwable) {
            // 精确闹钟被系统拒绝时降级为非精确，不让整个排程失败
            runCatching {
                am.set(AlarmManager.RTC_WAKEUP, millis, pending)
                scheduled += code
            }
        }
    }

    private fun cancelAll(am: AlarmManager) {
        for (code in ScheduledAlarms.load(context, ScheduledAlarms.KEY_SILENCE)) {
            // extra 不参与 PendingIntent 的相等判断，所以这里只给组件就能找回原 PendingIntent
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, SilenceReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                am.cancel(pending)
                pending.cancel()
            }
        }
        ScheduledAlarms.save(context, ScheduledAlarms.KEY_SILENCE, emptyList())
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()
}
