package cn.edu.qau.timetable.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "上课提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "上课前提醒你下一节课的教室和时间"
            }
            manager.createNotificationChannel(channel)
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
