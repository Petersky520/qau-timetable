package cn.edu.qau.timetable.core

/** 青岛农业大学校区。两个校区的上课时间不同，所以必须区分。 */
enum class Campus(val label: String) {
    CHENGYANG("城阳校区"),
    PINGDU("平度校区");

    companion object {
        fun fromName(name: String?): Campus =
            entries.firstOrNull { it.name == name } ?: CHENGYANG
    }
}
