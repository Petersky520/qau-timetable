package cn.edu.qau.timetable.core

/**
 * 强智教务系统课表里的"周次"字段。
 *
 * 实际见到的写法相当杂，以下都要能解析：
 * ```
 * 1-16周
 * 1-16周(单)
 * 1-16周（双）
 * 第3-15周
 * 1-8,10-16周
 * 1,3,5,7周
 * 5周
 * 1-16周;1-16周      // 同格多门课时可能拼接
 * ```
 */
data class WeekPattern(
    val raw: String,
    val weeks: Set<Int>,
    val oddOnly: Boolean = false,
    val evenOnly: Boolean = false,
) {
    fun contains(week: Int): Boolean = week in weeks

    val isEmpty: Boolean get() = weeks.isEmpty()

    /** 存库用的紧凑串，例如 "1,2,3,5"。 */
    fun toStorage(): String = weeks.sorted().joinToString(",")

    /** 人类可读回显。 */
    fun display(): String {
        if (raw.isNotBlank()) return raw
        return if (weeks.isEmpty()) "全周" else "${weeks.min()}-${weeks.max()}周"
    }

    companion object {
        const val MAX_WEEK = 30

        /** 无法解析时的兜底：不限制周次。 */
        val ALWAYS = WeekPattern("", (1..MAX_WEEK).toSet())

        fun fromStorage(storage: String): Set<Int> =
            storage.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..MAX_WEEK }
                .toSortedSet()
    }
}

object WeekPatternParser {

    private val RANGE = Regex("""(\d{1,2})\s*[-~～—–至]\s*(\d{1,2})""")
    private val SINGLE = Regex("""\d{1,2}""")

    fun parse(input: String?): WeekPattern {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return WeekPattern.ALWAYS

        val s = raw
            .replace('（', '(')
            .replace('）', ')')
            .replace('，', ',')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .replace('～', '-')

        val odd = s.contains('单')
        val even = s.contains('双')

        val collected = sortedSetOf<Int>()

        // 先吃区间，避免 "1-16" 被当成 1 和 16 两个孤立数字
        val rest = RANGE.replace(s) { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@replace " "
            val b = m.groupValues[2].toIntOrNull() ?: return@replace " "
            for (w in minOf(a, b)..maxOf(a, b)) {
                if (w in 1..WeekPattern.MAX_WEEK) collected.add(w)
            }
            " "
        }
        // 再吃孤立数字
        SINGLE.findAll(rest).forEach {
            val w = it.value.toIntOrNull() ?: return@forEach
            if (w in 1..WeekPattern.MAX_WEEK) collected.add(w)
        }

        // 什么都没解析出来 —— 当成不限制周次，而不是把整门课丢掉
        if (collected.isEmpty()) return WeekPattern(raw, WeekPattern.ALWAYS.weeks, odd, even)

        val filtered = when {
            odd && !even -> collected.filter { it % 2 == 1 }
            even && !odd -> collected.filter { it % 2 == 0 }
            else -> collected
        }.toSortedSet()

        return WeekPattern(raw, filtered, odd, even)
    }
}
