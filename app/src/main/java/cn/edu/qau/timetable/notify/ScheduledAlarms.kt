package cn.edu.qau.timetable.notify

import android.content.Context

/** 记录已排的闹钟 requestCode，便于重排时精确取消。 */
internal object ScheduledAlarms {

    private const val PREF = "scheduled_alarms"

    /** 上课提醒的闹钟码（沿用历史键名，升级后不会丢）。 */
    const val KEY_REMINDER = "codes"

    /** 上课静音的闹钟码。与提醒分开存，两边重排互不影响。 */
    const val KEY_SILENCE = "codes_silence"

    fun save(context: Context, key: String, codes: List<Int>) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(key, codes.joinToString(",")).apply()
    }

    fun load(context: Context, key: String): List<Int> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }
}
