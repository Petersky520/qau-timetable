package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.Term
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 「按日期推算当前是第几周」的纯逻辑部分。
 *
 * 之前那次失效不是这个函数算错了（是设置页把开学日期写到了没人读的地方），
 * 但这些用例把输入输出的契约钉死 —— 以后再动这块，跑一遍就知道有没有破坏约定。
 */
class TermTest {

    /** 2026-09-07 是周一（2026-09-01 是周二）。 */
    private val term = Term(
        name = "2026-2027-1",
        startMonday = LocalDate.of(2026, 9, 7),
        totalWeeks = 20,
        campus = Campus.CHENGYANG,
    )

    // ------------------------------------------------------------ 周次主逻辑

    @Test
    fun `开学当天是第 1 周`() {
        assertEquals(1, term.weekOf(LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun `第 1 周周日仍算第 1 周`() {
        assertEquals(1, term.weekOf(LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun `第 2 周周一是第 2 周`() {
        assertEquals(2, term.weekOf(LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun `开学之前返回 0`() {
        assertEquals(0, term.weekOf(LocalDate.of(2026, 9, 6)))
        assertEquals(0, term.weekOf(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `最后一整周都算第 20 周`() {
        // 第 20 周周一 = 09-07 + 19*7 = 2027-01-18
        assertEquals(20, term.weekOf(LocalDate.of(2027, 1, 18)))
        assertEquals(20, term.weekOf(LocalDate.of(2027, 1, 24)))
    }

    /**
     * 超过总周数（放假）返回 0。
     *
     * 原来写的是 `if (w > totalWeeks) w else w` —— 两个分支一模一样，
     * 于是寒假里会一路显示「第 21 周」「第 22 周」，
     * App 里那个「不在学期周次内」的空状态永远走不到。
     */
    @Test
    fun `超出总周数返回 0 而不是继续往上数`() {
        assertEquals(0, term.weekOf(LocalDate.of(2027, 1, 25)))
        assertEquals(0, term.weekOf(LocalDate.of(2027, 3, 1)))
    }

    // ------------------------------------------------------------ 日期换算

    @Test
    fun `mondayOfWeek 与 dateOf 互相对得上`() {
        assertEquals(LocalDate.of(2026, 9, 7), term.mondayOfWeek(1))
        assertEquals(LocalDate.of(2026, 9, 14), term.mondayOfWeek(2))
        // 第 1 周周四
        assertEquals(LocalDate.of(2026, 9, 10), term.dateOf(1, 4))
        // dateOf 造出来的日期，回过头算周次必须落回同一周
        assertEquals(3, term.weekOf(term.dateOf(3, 7)))
        assertEquals(11, term.weekOf(term.dateOf(11, 1)))
    }

    @Test
    fun `contains 只认学期内的周次`() {
        assertEquals(true, term.contains(1))
        assertEquals(true, term.contains(20))
        assertEquals(false, term.contains(0))
        assertEquals(false, term.contains(21))
    }

    // ------------------------------------------------------------ 学期名兜底

    @Test
    fun `按学期名粗推的开学日期落在周一`() {
        val autumn = Term.guessStartMonday("2026-2027-1")!!
        assertEquals(DayOfWeek.MONDAY, autumn.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 7), autumn)

        val spring = Term.guessStartMonday("2026-2027-2")!!
        assertEquals(DayOfWeek.MONDAY, spring.dayOfWeek)
        assertEquals(LocalDate.of(2027, 3, 1), spring)
    }

    @Test
    fun `学期名认不出来时宁可返回 null 也不要瞎猜`() {
        assertEquals(null, Term.guessStartMonday("随便写的"))
        assertEquals(null, Term.guessStartMonday(""))
    }
}
