package cn.edu.qau.timetable.core

/** 青岛农业大学校区。两个校区的上课时间不同，所以必须区分。 */
enum class Campus(val label: String) {
    CHENGYANG("城阳校区"),
    PINGDU("平度校区");

    companion object {
        /**
         * 既认枚举名（`PINGDU`，存库和 DataStore 用）也认展示名（`平度校区`，
         * 放进 Intent 传给通知用）。两者都认是必要的：只认枚举名时，
         * 通知里拿到的 "平度校区" 会静默落到 [CHENGYANG] 上，
         * 于是平度校区的人收到的提醒时间按城阳作息算。
         */
        fun fromName(name: String?): Campus =
            entries.firstOrNull { it.name == name || it.label == name } ?: CHENGYANG
    }
}
