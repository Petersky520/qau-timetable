package cn.edu.qau.timetable.core

import java.time.LocalDate

/**
 * 一条调休安排：某个**具体日期**实际按哪一天的课表上课，或者放假。
 *
 * 国内高校的节假日安排常带「调休」：比如国庆放假一周，然后某个周六补上周四的课。
 * 那天课表上「周四」的课要真的再上一次。
 *
 * App 里所有「今天有什么课」的判断，原本都是拿 `date.dayOfWeek` 去匹配课程 ——
 * 不处理调休就会**在补课日漏课、在放假日上错课**，提醒和自动静音也会跟着错。
 */
data class DayOverride(
    val date: LocalDate,
    /** 这天按星期几的课表上课（1=周一 … 7=周日）；**null 表示放假、当天没有课**。 */
    val useDayOfWeek: Int?,
    val note: String = "",
) {
    /** 放假日（而不是补课日）。 */
    val isHoliday: Boolean get() = useDayOfWeek == null
}

/**
 * 调休安排的解析与换算。
 *
 * 纯 Kotlin、不依赖 Android，因此可以直接单测 —— 这里的判断错一天，
 * 用户就会在补课日收不到提醒、或者对着空课表发懵。
 */
object DayOverrides {

    private val WEEK_LABELS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 1..7 → 「周一」..「周日」；越界返回 `?`，不抛异常。 */
    fun labelOf(dayOfWeek: Int): String =
        WEEK_LABELS.getOrElse(dayOfWeek - 1) { "?" }

    /**
     * 某天实际该按星期几的课表上课。
     *
     * - 没有任何调休安排 → 就是它自己的星期
     * - 有补课安排       → 返回被借的那一天（如周六返回 4）
     * - 有放假安排       → 返回 **null**，表示当天没有课
     */
    fun effectiveDayOfWeek(date: LocalDate, overrides: List<DayOverride>): Int? {
        val hit = overrides.firstOrNull { it.date == date } ?: return date.dayOfWeek.value
        return hit.useDayOfWeek
    }

    /** 某天的调休安排（如果有）。 */
    fun of(date: LocalDate, overrides: List<DayOverride>): DayOverride? =
        overrides.firstOrNull { it.date == date }

    /**
     * 序列化：一行一条 `日期,星期,备注`；星期留空表示放假。
     *
     * 存在 DataStore 里而不是数据库表里 —— 一学期就这么几条，
     * 而给 Room 加表要升 schema 版本，这个 App 用的是
     * `fallbackToDestructiveMigration()`，升版本会把用户的课表和成绩清掉。
     */
    fun encode(list: List<DayOverride>): String =
        list.sortedBy { it.date }.joinToString("\n") { o ->
            listOf(
                o.date.toString(),
                o.useDayOfWeek?.toString().orEmpty(),
                o.note.replace('\n', ' ').replace(',', '，'),
            ).joinToString(",")
        }

    /** [encode] 的逆操作。读不出的行直接丢掉，不让一条脏数据毁掉整份安排。 */
    fun decode(raw: String): List<DayOverride> =
        raw.lineSequence()
            .mapNotNull { decodeLine(it) }
            .sortedBy { it.date }
            .toList()

    private fun decodeLine(line: String): DayOverride? {
        if (line.isBlank()) return null
        val parts = line.split(',')
        val date = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return null
        val day = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in 1..7 }
        val note = parts.getOrNull(2)?.trim().orEmpty()
        return DayOverride(date = date, useDayOfWeek = day, note = note)
    }
}
