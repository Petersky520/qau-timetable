package cn.edu.qau.timetable.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import cn.edu.qau.timetable.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 上课提醒的闹钟落点：把提醒渲染成一条通知。
 *
 * 通知的具体内容（包括上面那个「上课静音」按钮）都在 [ClassNotification] 里，
 * 因为那个按钮的回调也要用同一套渲染去原地改写这条通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val spec = ClassNotification.specOf(intent) ?: return
        val appContext = context.applicationContext
        // 要读设置（决定这条通知挂不挂「上课静音」按钮），别在主线程上读 DataStore
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ClassNotification.post(appContext, spec) }
            pending.finish()
        }
    }

    companion object {
        const val CHANNEL_ID = "class_reminder"

        /**
         * 带「奶龙捧腹大笑」音效的渠道 id。
         *
         * ## 为什么要两个渠道，而不是给一个渠道换音效
         *
         * Android 8.0（API 26）起，通知的**音效、震动、重要性全部绑在渠道上**，
         * 而且渠道一旦创建，这些属性就**永久锁定** ——
         * 后续对同一个 id 再 `createNotificationChannel()` 只会改名字和描述，
         * 音效改动会被系统静默忽略（这是 Android 的硬规则）。
         *
         * 所以想换音效只有一条路：**换一个渠道 id**。
         * 用户切换开关时，我们建新渠道、删旧渠道，通知才会改用新声音。
         */
        const val CHANNEL_ID_FUNNY = "class_reminder_funny"

        /** 按当前设置算出该用哪个渠道。 */
        fun channelIdFor(funny: Boolean): String =
            if (funny) CHANNEL_ID_FUNNY else CHANNEL_ID

        fun intentFor(
            context: Context,
            courseName: String,
            room: String,
            teacher: String,
            startPeriod: Int,
            endPeriod: Int,
            campusLabel: String,
            minutesBefore: Int,
        ): Intent = ClassNotification.putSpec(
            Intent(context, ReminderReceiver::class.java),
            ClassNotification.Spec(
                courseName = courseName,
                room = room,
                teacher = teacher,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                campusLabel = campusLabel,
                minutesBefore = minutesBefore,
            ),
        )

        /**
         * 按当前音效设置准备好渠道，并把**另一个**渠道删掉。
         *
         * 删旧渠道是必要的：不删的话系统设置里会同时躺着「上课提醒」和
         * 「上课提醒（奶龙）」两条，用户根本分不清哪条是活的，
         * 还可能把声音调在一条已经没人用的渠道上。
         *
         * @return 当前生效的渠道 id
         */
        fun ensureChannel(context: Context, funny: Boolean = false): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return channelIdFor(funny)

            val wanted = channelIdFor(funny)
            val unwanted = channelIdFor(!funny)

            val channel = NotificationChannel(
                wanted,
                if (funny) "上课提醒（奶龙笑声）" else "上课提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = if (funny) {
                    "上课前提醒，使用「奶龙捧腹大笑」音效"
                } else {
                    "上课前提醒你下一节课的教室和时间"
                }
                if (funny) {
                    // 显式指定 raw 资源：不指定的话跟随系统默认提示音
                    setSound(
                        Uri.parse(
                            "android.resource://${context.packageName}/${R.raw.nailong_laugh}"
                        ),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
            runCatching { manager.deleteNotificationChannel(unwanted) }
            return wanted
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? cn.edu.qau.timetable.QauApp ?: return
        // 重启会把闹钟清空。先把可能残留的静音还原掉（关机时正好在上课的话，
        // 状态标志会留在"已静音"，不处理手机会一直哑着），再重排两套闹钟。
        runCatching { RingerModeController.restore(context) }
        app.container.reminders.reschedule()
        app.container.silence.reschedule()
        // reschedule() 是异步起协程的，两个 scheduler 各自会在跑完后重定续排闹钟；
        // 这里再显式定一次，覆盖「两个都因为早退条件没跑到最后一行」的情况 ——
        // 否则关掉再打开提醒的用户，重启后可能一直没有续排。
        runCatching { ChainReceiver.schedule(context) }
    }
}
