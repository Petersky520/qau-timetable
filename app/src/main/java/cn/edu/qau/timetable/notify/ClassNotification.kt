package cn.edu.qau.timetable.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.qau.timetable.MainActivity
import cn.edu.qau.timetable.QauApp
import cn.edu.qau.timetable.R
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.PeriodTimes
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 课前提醒通知的渲染与投递。
 *
 * 提醒闹钟（[ReminderReceiver]）和通知上那个「上课静音」按钮
 * （[SilenceActionReceiver]）产出的是**同一条**通知：按钮点下去之后是原地改写，
 * 而不是再弹一条。否则一节课的功夫通知栏里会同时挂着「15 分钟后上课」和
 * 「已静音」两条，说的还是同一节课。
 */
internal object ClassNotification {

    /** 一条提醒通知的全部内容。按钮回调时也要原样带回来，才能重新渲染。 */
    data class Spec(
        val courseName: String,
        val room: String,
        val teacher: String,
        val startPeriod: Int,
        val endPeriod: Int,
        val campusLabel: String,
        val minutesBefore: Int,
    ) {
        /**
         * 通知 id，同时也是按钮 PendingIntent 的 requestCode。
         * 一门课一个槽位 —— 同名的两门课会共用这条通知，这是既有行为。
         */
        val id: Int get() = courseName.hashCode() and 0x7fffffff
    }

    /**
     * 字段名保持在 [ReminderReceiver] 时期的样子（`course` / `room` …）。
     * 升级前系统里可能还挂着最多 7 天的旧闹钟，用同一套 extra 名字，
     * 那些闹钟到点弹出来的通知才不会变成空白。
     */
    private const val EXTRA_COURSE = "course"
    private const val EXTRA_ROOM = "room"
    private const val EXTRA_TEACHER = "teacher"
    private const val EXTRA_START = "start"
    private const val EXTRA_END = "end"
    private const val EXTRA_CAMPUS = "campus"
    private const val EXTRA_MINUTES = "minutes"

    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun putSpec(intent: Intent, spec: Spec): Intent = intent.apply {
        putExtra(EXTRA_COURSE, spec.courseName)
        putExtra(EXTRA_ROOM, spec.room)
        putExtra(EXTRA_TEACHER, spec.teacher)
        putExtra(EXTRA_START, spec.startPeriod)
        putExtra(EXTRA_END, spec.endPeriod)
        putExtra(EXTRA_CAMPUS, spec.campusLabel)
        putExtra(EXTRA_MINUTES, spec.minutesBefore)
    }

    fun specOf(intent: Intent): Spec? {
        val name = intent.getStringExtra(EXTRA_COURSE).orEmpty()
        if (name.isEmpty()) return null
        return Spec(
            courseName = name,
            room = intent.getStringExtra(EXTRA_ROOM).orEmpty(),
            teacher = intent.getStringExtra(EXTRA_TEACHER).orEmpty(),
            startPeriod = intent.getIntExtra(EXTRA_START, 0),
            endPeriod = intent.getIntExtra(EXTRA_END, 0),
            campusLabel = intent.getStringExtra(EXTRA_CAMPUS).orEmpty(),
            minutesBefore = intent.getIntExtra(EXTRA_MINUTES, 15),
        )
    }

    /** 按**当前真实铃声状态**渲染并投递。 */
    suspend fun post(context: Context, spec: Spec) {
        ReminderReceiver.ensureChannel(context)
        val withAction = silenceFeatureEnabled(context)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(spec.id, build(context, spec, silencedUntilText(context), withAction))
        }
        remember(context, spec)
    }

    /**
     * 铃声被别的路径改动后（课自动静音 / 到点自动还原），
     * 如果这条通知还挂在通知栏里，就把文案同步过来。
     *
     * 不这么做的话，课前那条写着「上课静音」的通知会一直挂到下课 ——
     * 点下去却是恢复铃声。
     */
    suspend fun refreshIfShowing(context: Context) {
        val spec = lastPosted(context) ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.activeNotifications.none { it.id == spec.id }) return
        post(context, spec)
    }

    /**
     * 按钮只在**开了上课静音**时才挂上去。
     *
     * 这个开关的语义就是「允许 App 动我的铃声」——关掉了就不该再在通知栏里
     * 递一个改铃声的按钮，更不该顺带催「通知策略访问权限」。
     * 读不到设置时按"没开"处理：宁可少一个按钮，也不要背着用户改铃声。
     */
    private suspend fun silenceFeatureEnabled(context: Context): Boolean {
        val repo = (context.applicationContext as? QauApp)?.container?.repo ?: return false
        return runCatching { repo.settingsFlow.first().silenceEnabled }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- 渲染

    /** 处于静音中则返回形如 `17:35` 的恢复时刻，否则 null。 */
    private fun silencedUntilText(context: Context): String? {
        val millis = RingerModeController.silencedUntil(context) ?: return null
        return runCatching {
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(HM)
        }.getOrNull()
    }

    private fun build(
        context: Context,
        spec: Spec,
        silencedUntil: String?,
        withSilenceAction: Boolean,
    ): Notification {
        val campus = Campus.fromName(spec.campusLabel)
        val detail = buildString {
            val timeText = PeriodTimes.rangeText(campus, spec.startPeriod, spec.endPeriod)
            if (timeText.isNotEmpty()) append(timeText).append("  ")
            if (spec.room.isNotEmpty()) append(spec.room)
            if (spec.teacher.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(spec.teacher)
            }
        }
        val body = if (silencedUntil == null) detail else "$detail　·　已静音，$silencedUntil 恢复"

        val contentIntent = PendingIntent.getActivity(
            context,
            spec.id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, ReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle("${spec.minutesBefore} 分钟后上课：${spec.courseName}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // 原地改写时不要再响一声 —— 按钮点一下响一次会很烦
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)

        if (withSilenceAction) {
            builder.addAction(action(context, spec, silenced = silencedUntil != null))
        }
        return builder.build()
    }

    /**
     * 通知上那个按钮。
     *
     * 没授「通知策略访问权限」时静音**根本不会生效**，所以这时候按钮不做静音，
     * 改成跳到系统里那个开关（设置页里也有同样的入口）。文案跟着变，
     * 免得用户按了一直没反应还不知道为什么。
     */
    private fun action(context: Context, spec: Spec, silenced: Boolean): NotificationCompat.Action {
        val pending = if (RingerModeController.isGranted(context)) {
            PendingIntent.getBroadcast(
                context,
                spec.id,
                SilenceActionReceiver.intentFor(context, spec),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                context,
                spec.id,
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Action(
            if (silenced) R.drawable.ic_notif_ring else R.drawable.ic_notif_silence,
            if (silenced) "恢复铃声" else "上课静音",
            pending,
        )
    }

    // ------------------------------------------------- 「最近一条」的存档
    //
    // 只为了让 refreshIfShowing 找得到要改哪条通知，不是数据源 ——
    // 丢了大不了就是文案停在旧状态。用 Intent 的 URI 序列化省得手写七八个键。

    private const val PREF = "last_reminder_notification"
    private const val KEY_SPEC = "spec"

    private fun remember(context: Context, spec: Spec) {
        runCatching {
            val uri = putSpec(Intent(), spec).toUri(Intent.URI_INTENT_SCHEME)
            prefs(context).edit().putString(KEY_SPEC, uri).apply()
        }
    }

    private fun lastPosted(context: Context): Spec? = runCatching {
        prefs(context).getString(KEY_SPEC, null)
            ?.let { specOf(Intent.parseUri(it, 0)) }
    }.getOrNull()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
