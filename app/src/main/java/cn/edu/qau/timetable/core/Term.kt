package cn.edu.qau.timetable.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 学期。周次计算以「第 1 周周一」为基准，这是教务系统课表的通行约定。
 */
data class Term(
    val name: String,
    val startMonday: LocalDate,
    val totalWeeks: Int = DEFAULT_WEEKS,
    val campus: Campus = Campus.CHENGYANG,
) {
    /**
     * 给定日期是第几周。
     *
     * 早于开学、或已经上完 [totalWeeks] 周（放假了）都返回 **0**，
     * 调用方据此显示「不在学期周次内」。
     *
     * 这里原本写的是 `if (w > totalWeeks) w else w` —— 两个分支一样，
     * 于是寒假里会一路显示「第 21 周」「第 22 周」，App 里那个
     * 「不在学期周次内」的空状态永远走不到。
     */
    fun weekOf(date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(startMonday, date)
        if (days < 0) return 0
        val w = (days / 7).toInt() + 1
        return if (w > totalWeeks) 0 else w
    }

    fun mondayOfWeek(week: Int): LocalDate = startMonday.plusWeeks((week - 1).toLong())

    fun dateOf(week: Int, dayOfWeek: Int): LocalDate =
        mondayOfWeek(week).plusDays((dayOfWeek - 1).toLong())

    /** 是否还在学期内（按周次判断）。 */
    fun contains(week: Int): Boolean = week in 1..totalWeeks

    companion object {
        const val DEFAULT_WEEKS = 20

        /**
         * 从 "2026-2027-1" 这类学期名推断一个**粗略**的开学时间。
         * 仅在没有配置真实开学日期时兜底，精度到月；
         * 真正的第 1 周日期应由用户在「设置」里填写或从教务系统抓取。
         */
        fun guessStartMonday(termName: String): LocalDate? {
            val m = Regex("""(\d{4})\s*-\s*(\d{4})\s*-\s*(\d)""").find(termName) ?: return null
            val y1 = m.groupValues[1].toInt()
            val half = m.groupValues[3].toInt()
            // 秋季学期(1) 约 9 月第 1 个周一；春季学期(2) 约 3 月第 1 个周一
            val (year, month) = if (half == 1) y1 to 9 else (y1 + 1) to 3
            var d = LocalDate.of(year, month, 1)
            while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1)
            return d
        }
    }
}
