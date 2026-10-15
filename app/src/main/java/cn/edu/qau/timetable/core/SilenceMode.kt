package cn.edu.qau.timetable.core

/** 上课自动静音时，把铃声调成哪一种。 */
enum class SilenceMode(val label: String) {
    /** 完全静音（RINGER_MODE_SILENT）。 */
    SILENT("完全静音"),

    /** 只震动、不响铃（RINGER_MODE_VIBRATE）。 */
    VIBRATE("仅震动");

    companion object {
        fun fromName(name: String?): SilenceMode =
            entries.firstOrNull { it.name == name } ?: SILENT
    }
}
