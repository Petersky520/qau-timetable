package cn.edu.qau.timetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.qau.timetable.QauApp
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.SilenceWindow
import cn.edu.qau.timetable.data.repo.TimetableRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 通知上「上课静音 / 恢复铃声」按钮的落点。
 *
 * 按下去要回答两个问题：
 *
 * 1. **该静音还是该恢复** —— 看 [RingerModeController.isActive]。
 *    按钮文案也是照它渲染的，所以这里不用额外传状态，也就永远不会和文案打架。
 * 2. **静到什么时候** —— 必须自己补一个还原闹钟。这是重点：
 *    [SilenceScheduler] 只给**还没开始**的时段排还原闹钟，课前点按钮时
 *    它多半已经排好了；但如果用户是在时段中间点的（通知还挂在通知栏里），
 *    就一个兜底的都没有，不补一个手机会一直哑着到下次重排为止。
 */
class SilenceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val spec = ClassNotification.specOf(intent) ?: return
        val appContext = context.applicationContext
        // 要读数据库（靠课程表算静音时段），onReceive 里不能阻塞主线程
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (RingerModeController.isActive(appContext)) {
                    RingerModeController.restore(appContext)
                } else {
                    silenceUntilClassEnd(appContext, spec)
                }
            } catch (_: Throwable) {
                // 按钮点了没反应，也好过崩一次
            } finally {
                // 成败都同步一次文案，让它和真实铃声状态一致
                runCatching { ClassNotification.post(appContext, spec) }
                pending.finish()
            }
        }
    }

    private suspend fun silenceUntilClassEnd(context: Context, spec: ClassNotification.Spec) {
        val app = context.applicationContext as? QauApp ?: return
        val repo = app.container.repo
        val today = LocalDate.now()
        val endAt = resolveEndTime(repo, spec, today) ?: return
        val mode = repo.settingsFlow.first().silenceMode

        // 还原闹钟在 silence() 成功之后才排 —— 什么都没改就排一个到点执行的"还原"，
        // 会在下课时把用户自己设的静音当成我们的、反过来给他开铃声。
        if (!RingerModeController.silence(context, mode, untilMillis = millisOf(today, endAt))) return
        scheduleRestore(context, today, endAt, mode)
    }

    /**
     * 静音持续到几点。
     *
     * 首选「课表算出的、当前或接下来那一段静音时段的结束时刻」—— 连堂课会一直
     * 静到最后一节下课，和自动静音表现一致。课表还没导入、或今天不在学期内时
     * 退回这条通知自带的节次，至少保证铃声会被还原。
     */
    private suspend fun resolveEndTime(
        repo: TimetableRepository,
        spec: ClassNotification.Spec,
        today: LocalDate,
    ): LocalTime? {
        val fromTimetable = runCatching {
            val entity = repo.activeTermEntity() ?: return@runCatching null
            val term = entity.toDomain()
            val week = term.weekOf(today)
            if (week !in 1..term.totalWeeks) return@runCatching null
            val spans = SilencePlan.spansOn(
                term.campus,
                repo.coursesNow(entity.id),
                today,
                week,
                repo.settingsFlow.first().dayOverrides,
            )
            SilenceWindow.spanFor(spans, LocalTime.now())?.end
        }.getOrNull()

        return fromTimetable ?: PeriodTimes.endOf(Campus.fromName(spec.campusLabel), spec.endPeriod)
    }

    private fun scheduleRestore(context: Context, date: LocalDate, at: LocalTime, mode: SilenceMode) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val code = SilencePlan.codeOf(date, at)
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            SilenceReceiver.intentFor(context, restore = true, mode = mode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = millisOf(date, at)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        } catch (_: Throwable) {
            // 精确闹钟被系统拒绝时降级为非精确，别让整个按钮失效
            runCatching { manager.set(AlarmManager.RTC_WAKEUP, millis, pending) }
        }

        // 登记进静音闹钟名单：以后重排（改设置 / 重启 / 重新导入课表）能一并取消，
        // 不会留下一个没人管、也不会被取消的还原闹钟。
        val codes = ScheduledAlarms.load(context, ScheduledAlarms.KEY_SILENCE)
        if (code !in codes) {
            ScheduledAlarms.save(context, ScheduledAlarms.KEY_SILENCE, codes + code)
        }
    }

    private fun millisOf(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        /** 只在模块内用（[ClassNotification] 里造 PendingIntent），所以是 internal。 */
        internal fun intentFor(context: Context, spec: ClassNotification.Spec): Intent =
            ClassNotification.putSpec(Intent(context, SilenceActionReceiver::class.java), spec)
    }
}
