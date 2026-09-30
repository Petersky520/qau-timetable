package cn.edu.qau.timetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.SilenceWindow
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
 * 时段由 [SilenceWindow] 算出（课间的短间隔会被合并，午休/晚饭不会），
 * 那部分是纯逻辑、有单测覆盖。
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
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val mode = settings.silenceMode
        val scheduled = ArrayList<Int>()

        for (offset in 0..6) {
            val date = today.plusDays(offset.toLong())
            val week = term.weekOf(date)
            if (week <= 0 || week > term.totalWeeks) continue
            val dow = date.dayOfWeek.value

            val ranges = courses
                .filter { it.dayOfWeek == dow && it.occursIn(week) }
                .map { it.startPeriod..it.endPeriod }
            if (ranges.isEmpty()) continue

            val spans = SilenceWindow.merge(SilenceWindow.of(term.campus, ranges))
            spans.forEachIndexed { index, span ->
                val startAt = date.atTime(span.start)
                // 已经开始的时段不补：免得刚打开开关就"突然静音"，
                // 也免得留下一个没有配对开始、毫无意义的还原闹钟。
                if (!startAt.isAfter(now)) return@forEachIndexed

                scheduleOne(am, scheduled, date, index, false, startAt, mode, zone)
                scheduleOne(am, scheduled, date, index, true, date.atTime(span.end), mode, zone)
            }
        }

        ScheduledAlarms.save(context, ScheduledAlarms.KEY_SILENCE, scheduled)
    }

    private fun scheduleOne(
        am: AlarmManager,
        scheduled: MutableList<Int>,
        date: LocalDate,
        index: Int,
        restore: Boolean,
        at: LocalDateTime,
        mode: SilenceMode,
        zone: ZoneId,
    ) {
        val code = codeOf(date, index, restore)
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            SilenceReceiver.intentFor(context, restore, mode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = at.atZone(zone).toInstant().toEpochMilli()
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

    /**
     * 一天之内 (日期, 第几段, 开始/结束) 唯一即可。
     * 乘 100 留出足够的段数空间 —— 一天不可能有 50 个静音时段。
     */
    private fun codeOf(date: LocalDate, index: Int, restore: Boolean): Int =
        ((date.toEpochDay() * 100L + index * 2L + if (restore) 1L else 0L) % Int.MAX_VALUE).toInt()
}
