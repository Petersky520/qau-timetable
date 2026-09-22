package cn.edu.qau.timetable.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.qau.timetable.MainActivity
import cn.edu.qau.timetable.R
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.core.Campus

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(EXTRA_COURSE).orEmpty()
        if (courseName.isEmpty()) return

        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        val teacher = intent.getStringExtra(EXTRA_TEACHER).orEmpty()
        val startPeriod = intent.getIntExtra(EXTRA_START, 0)
        val endPeriod = intent.getIntExtra(EXTRA_END, 0)
        val campus = Campus.fromName(intent.getStringExtra(EXTRA_CAMPUS))
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)

        ensureChannel(context)

        val timeText = PeriodTimes.rangeText(campus, startPeriod, endPeriod)
        val detail = buildString {
            if (timeText.isNotEmpty()) append(timeText).append("  ")
            if (room.isNotEmpty()) append(room)
            if (teacher.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(teacher)
            }
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            courseName.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle("$minutes 分钟后上课：$courseName")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((courseName.hashCode() and 0x7fffffff), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "class_reminder"

        private const val EXTRA_COURSE = "course"
        private const val EXTRA_ROOM = "room"
        private const val EXTRA_TEACHER = "teacher"
        private const val EXTRA_START = "start"
        private const val EXTRA_END = "end"
        private const val EXTRA_CAMPUS = "campus"
        private const val EXTRA_MINUTES = "minutes"

        fun intentFor(
            context: Context,
            requestCode: Int,
            courseName: String,
            room: String,
            teacher: String,
            startPeriod: Int,
            endPeriod: Int,
            campusLabel: String,
            minutesBefore: Int,
        ): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_COURSE, courseName)
            putExtra(EXTRA_ROOM, room)
            putExtra(EXTRA_TEACHER, teacher)
            putExtra(EXTRA_START, startPeriod)
            putExtra(EXTRA_END, endPeriod)
            putExtra(EXTRA_CAMPUS, campusLabel)
            putExtra(EXTRA_MINUTES, minutesBefore)
        }

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
        app.container.reminders.reschedule()
    }
}
