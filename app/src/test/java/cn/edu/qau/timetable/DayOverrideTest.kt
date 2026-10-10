package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 调休安排。
 *
 * 这里的判断错一天，用户就会在补课日收不到提醒、或者对着空课表发懵，
 * 所以「自己的星期 / 补课日 / 放假日」三种情况各自钉一条用例。
 */
class DayOverrideTest {

    // 真实校历里最常见的组合：国庆放假，节后那个周六补课。
    // 2026-10-01 是周四，2026-10-10 是周六。
    private val makeupSaturday = LocalDate.of(2026, 10, 10)
    private val holidayThursday = LocalDate.of(2026, 10, 1)
    private val normalFriday = LocalDate.of(2026, 10, 9)

    private fun eff(date: LocalDate, vararg o: DayOverride): Int? =
        DayOverrides.effectiveDayOfWeek(date, o.toList())

    // ---------------------------------------------------------- 三种情况

    /** 没有安排时就是日期本身的星期。 */
    @Test
    fun `没有调休安排时按自己的星期`() {
        assertEquals(6, eff(makeupSaturday))          // 周六
        assertEquals(4, eff(holidayThursday))         // 周四
        assertEquals(5, eff(normalFriday))            // 周五
    }

    /** 补课日：借哪一天的课表就返回那一天。 */
    @Test
    fun `补课日返回被借的那一天`() {
        val o = DayOverride(makeupSaturday, useDayOfWeek = 4, note = "补周四的课")
        assertEquals(4, eff(makeupSaturday, o))
    }

    /** 放假日返回 null —— 调用方据此判定「当天没有课」。 */
    @Test
    fun `放假日返回 null`() {
        val o = DayOverride(holidayThursday, useDayOfWeek = null)
        assertNull(eff(holidayThursday, o))
    }

    /** 安排只对那一天生效，不能串到别的日期上。 */
    @Test
    fun `安排只影响指定的那一天`() {
        val o = DayOverride(makeupSaturday, useDayOfWeek = 4)
        assertEquals(5, eff(normalFriday, o))
        assertEquals(6, eff(makeupSaturday.plusWeeks(1), o))
    }

    /** 同一天既有放假又有补课时取先出现的那条（UI 侧本来就按日期去重）。 */
    @Test
    fun `同一天只认第一条`() {
        val list = listOf(
            DayOverride(makeupSaturday, useDayOfWeek = 2),
            DayOverride(makeupSaturday, useDayOfWeek = 4),
        )
        assertEquals(2, DayOverrides.effectiveDayOfWeek(makeupSaturday, list))
    }

    @Test
    fun `of 能按日期取到安排`() {
        val o = DayOverride(makeupSaturday, useDayOfWeek = 4)
        assertEquals(o, DayOverrides.of(makeupSaturday, listOf(o)))
        assertNull(DayOverrides.of(normalFriday, listOf(o)))
    }

    // ---------------------------------------------------------- 存取

    /** 编解码往返：补课与放假两种都要能原样读回来。 */
    @Test
    fun `编解码往返`() {
        val list = listOf(
            DayOverride(makeupSaturday, useDayOfWeek = 4, note = "国庆后补课"),
            DayOverride(holidayThursday, useDayOfWeek = null),
        )
        assertEquals(list.sortedBy { it.date }, DayOverrides.decode(DayOverrides.encode(list)))
    }

    /** 空串 = 没有安排，不能炸。 */
    @Test
    fun `空串解出空列表`() {
        assertTrue(DayOverrides.decode("").isEmpty())
        assertTrue(DayOverrides.decode("   \n  ").isEmpty())
    }

    /** 一条脏数据不该毁掉整份安排：读不出的行直接丢掉。 */
    @Test
    fun `脏行被跳过`() {
        val raw = "不是日期,4\n2026-10-10,4\n坏行\n"
        val list = DayOverrides.decode(raw)
        assertEquals(1, list.size)
        assertEquals(LocalDate.of(2026, 10, 10), list[0].date)
    }

    /** 缺「星期」字段时按放假处理 —— 编码时空着就是放假。 */
    @Test
    fun `缺星期字段按放假`() {
        val list = DayOverrides.decode("2026-10-01,,")
        assertEquals(1, list.size)
        assertNull(list[0].useDayOfWeek)
    }

    /** 星期越界（0 或 8）等同于没写，也就是放假，不能算成周一/周日。 */
    @Test
    fun `星期越界按放假`() {
        assertNull(DayOverrides.decode("2026-10-01,0,")[0].useDayOfWeek)
        assertNull(DayOverrides.decode("2026-10-01,8,")[0].useDayOfWeek)
    }

    /** 解码结果按日期升序，方便直接显示。 */
    @Test
    fun `解码后按日期升序`() {
        val list = DayOverrides.decode("2026-10-10,4,\n2026-10-01,,\n2026-10-05,2,")
        assertEquals(
            listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10)),
            list.map { it.date },
        )
    }

    /** 备注里的逗号会破坏 CSV，编码时要换掉。 */
    @Test
    fun `备注里的逗号不影响解析`() {
        val o = DayOverride(makeupSaturday, useDayOfWeek = 4, note = "补课,连堂")
        val back = DayOverrides.decode(DayOverrides.encode(listOf(o)))
        assertEquals(1, back.size)
        assertEquals(4, back[0].useDayOfWeek)
        assertEquals(makeupSaturday, back[0].date)
    }

    // ---------------------------------------------------------- 标签

    @Test
    fun `星期标签`() {
        assertEquals("周一", DayOverrides.labelOf(1))
        assertEquals("周六", DayOverrides.labelOf(6))
        assertEquals("周日", DayOverrides.labelOf(7))
        // 越界不抛异常，返回占位符
        assertEquals("?", DayOverrides.labelOf(0))
        assertEquals("?", DayOverrides.labelOf(99))
    }
}
