package cn.edu.qau.timetable.notify

import android.content.Context

/** 记录已排的闹钟 requestCode，便于重排时精确取消。 */
internal object ScheduledAlarms {

    private const val PREF = "scheduled_alarms"
    private const val KEY = "codes"

    fun save(context: Context, codes: List<Int>) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, codes.joinToString(",")).apply()
    }

    fun load(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }
}
