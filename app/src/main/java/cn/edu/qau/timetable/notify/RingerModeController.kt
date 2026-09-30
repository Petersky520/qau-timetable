package cn.edu.qau.timetable.notify

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import cn.edu.qau.timetable.core.SilenceMode

/**
 * 真正去改铃声，并记住「原来是什么模式」以便下课时还原。
 *
 * ## 权限
 * 从 Android 6.0（API 23）起，改铃声需要用户授予**通知策略访问权限**
 * （设置里的「勿扰 / 允许通知打扰」）。在 manifest 里声明
 * `ACCESS_NOTIFICATION_POLICY` 只算完成一半，必须由用户手动去系统设置里打开，
 * 所以这里每次动手前都要用 [isGranted] 复核一遍。
 *
 * ## 还原策略
 * 只有当前铃声**仍然是当初我们设的那一个**时才还原。
 * 如果用户在上课期间自己把声音调回来了，说明他并不想要静音，
 * 这时还原就会反过来把他的选择覆盖掉 —— 所以那种情况下只清状态、不动铃声。
 *
 * ## 关于「比目标更安静就不动」
 * [AudioManager] 的三个模式值恰好是按安静程度递增的：
 * SILENT(0) < VIBRATE(1) < NORMAL(2)。
 * 如果用户本来就自己开了完全静音，而我们要设的是「仅震动」，
 * 那就没必要去动它 —— 动了反而会在下课时把他的静音"还原"成响铃。
 */
internal object RingerModeController {

    private const val PREF = "ringer_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PREVIOUS = "previous_mode"
    private const val KEY_APPLIED = "applied_mode"

    /** 通知策略访问权限是否已授予。 */
    fun isGranted(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        nm.isNotificationPolicyAccessGranted
    }.getOrDefault(false)

    /**
     * 进入静音。返回是否**真的改动了**铃声 ——
     * 已经足够安静、或没权限时返回 false（没有改动就没有需要还原的东西）。
     */
    fun silence(context: Context, mode: SilenceMode): Boolean {
        if (!isGranted(context)) return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val target = when (mode) {
            SilenceMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            SilenceMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
        }
        val current = runCatching { am.ringerMode }.getOrNull() ?: return false
        if (current <= target) return false

        val ok = runCatching { am.ringerMode = target }.isSuccess
        if (ok) {
            prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_PREVIOUS, current)
                .putInt(KEY_APPLIED, target)
                .apply()
        }
        return ok
    }

    /**
     * 还原铃声。返回是否真的改动了。
     *
     * 幂等：没有处于「我们设的静音」状态时什么都不做，可以安全地在
     * 开机、关功能、撤销权限等场景无条件调用。
     */
    fun restore(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        val previous = p.getInt(KEY_PREVIOUS, AudioManager.RINGER_MODE_NORMAL)
        val applied = p.getInt(KEY_APPLIED, AudioManager.RINGER_MODE_SILENT)
        // 先清状态：不管还原成不成功，这一轮静音都结束了
        p.edit().clear().apply()

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val current = runCatching { am.ringerMode }.getOrNull() ?: return false
        // 用户自己调过声音了，尊重他的选择
        if (current != applied) return false
        return runCatching { am.ringerMode = previous }.isSuccess
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
