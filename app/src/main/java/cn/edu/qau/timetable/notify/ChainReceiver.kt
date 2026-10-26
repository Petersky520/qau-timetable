package cn.edu.qau.timetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * 排程续期：解决「只排 7 天、之后没人续」的断档。
 *
 * ## 这是个真 bug（v3.6 修）
 *
 * 提醒和静音都只排未来 7 天（这是对的 —— 一次性排几百个闹钟既浪费
 * 又容易撞系统上限）。但排完之后**没有任何东西负责续上第 8 天**：
 *
 *   · 只有「开机 / 重装更新 / 改设置 / 导入课表」才会重排
 *   · 用户一周不开 App、也不重启手机，第 8 天起就彻底没有提醒了
 *
 * 而且这个断档**没有任何提示**：设置页的开关还开着，用户以为一切正常，
 * 直到某天发现"今天没提醒我"。
 *
 * 修法：每次排完顺带定一个「续排闹钟」，定在第 6 天 —— 那时 7 天窗口里
 * 还剩一天余量，够系统把这次唤醒延迟掉（非精确闹钟可能晚几分钟到几小时），
 * 也不会真的出现空档。
 *
 * ## 为什么用 `set` 而不是 `setExact`
 *
 * 续排不是时间敏感操作（早几小时晚几小时都无所谓，只要在第 7 天结束前跑到）。
 * 用非精确闹钟**不需要 SCHEDULE_EXACT_ALARM 权限**，
 * 也就不会被 Android 14 的权限默认拒绝卡住 —— 这恰好是断档最需要可靠的一环。
 */
class ChainReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? cn.edu.qau.timetable.QauApp ?: return
        // 续排就是"再排一次"：两个 scheduler 跑完都会各自重定下一次续排。
        runCatching { app.container.reminders.reschedule() }
        runCatching { app.container.silence.reschedule() }
    }

    companion object {
        private const val REQUEST_CODE = 0x0C4A1

        /** 第几天触发续排。7 天窗口留 1 天余量。 */
        private const val LEAD_DAYS = 6L

        /**
         * 定下一次续排。
         *
         * 幂等：同一个 requestCode + 同一份 Intent，重复调用只会替换时间。
         */
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val trigger = LocalDate.now()
                .plusDays(LEAD_DAYS)
                // 挑一个"安静"的时刻：凌晨 3 点。这时几乎不会有人正在用 App，
                // 也不会和上课时间撞上（最早的课 8:00）。
                .atTime(3, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ChainReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // 非精确：不需要精确闹钟权限，系统可以随意合并这次唤醒。
            runCatching {
                am.set(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }
}
