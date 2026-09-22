package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.PeriodTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作息时间表 —— 课表左侧「节号 + 上课/下课时间」完全依赖它。
 *
 * 注意：TimetableGrid 原先根本没接收 campus，作息时间无从取用；
 * 这里的"每个节次都必须有可显示的时间"就是那件事的不变量。
 */
class PeriodTimesTest {

    private val campuses = listOf(Campus.CHENGYANG, Campus.PINGDU)

    // ---------------------------------------------------------- 基本正确性

    /** 数据来自青农大教务处《教学时间表》，两个校区第 5 节不同。 */
    @Test
    fun `城阳校区第五节`() {
        val p = PeriodTimes.get(Campus.CHENGYANG, 5)!!
        assertEquals("11:35", p.startText)
        assertEquals("12:00", p.endText)
    }

    @Test
    fun `平度校区第五节`() {
        val p = PeriodTimes.get(Campus.PINGDU, 5)!!
        assertEquals("12:00", p.startText)
        assertEquals("12:25", p.endText)
    }

    @Test
    fun `平度上午比城阳晚半小时`() {
        assertEquals("08:00", PeriodTimes.get(Campus.CHENGYANG, 1)!!.startText)
        assertEquals("08:30", PeriodTimes.get(Campus.PINGDU, 1)!!.startText)
    }

    @Test
    fun `晚间课两校区一致`() {
        val a = PeriodTimes.get(Campus.CHENGYANG, 11)!!
        val b = PeriodTimes.get(Campus.PINGDU, 11)!!
        assertEquals(a.startText, b.startText)
        assertEquals(a.endText, b.endText)
    }

    @Test
    fun `区间文本`() {
        assertEquals("08:00-09:40", PeriodTimes.rangeText(Campus.CHENGYANG, 1, 2))
    }

    @Test
    fun `每校区十一节`() {
        assertEquals(11, PeriodTimes.of(Campus.CHENGYANG).size)
        assertEquals(11, PeriodTimes.of(Campus.PINGDU).size)
    }

    // ------------------------------------------------- 左侧时间列的不变量

    /** 左侧每一格都要能显示上课和下课两个时刻，缺一个就是空格子。 */
    @Test
    fun `每个节次都有时间可显示`() {
        for (campus in campuses) {
            for (period in 1..PeriodTimes.PERIOD_COUNT) {
                val slot = PeriodTimes.get(campus, period)
                assertNotNull("$campus 第 $period 节没有作息时间", slot)
                assertEquals(period, slot!!.index)
            }
        }
    }

    @Test
    fun `上课时间必须早于下课时间`() {
        for (campus in campuses) {
            for (slot in PeriodTimes.of(campus)) {
                assertTrue(
                    "$campus 第 ${slot.index} 节时间倒挂：${slot.rangeText}",
                    slot.start.isBefore(slot.end),
                )
            }
        }
    }

    /** 节次越界时给空串，而不是抛异常或显示错时间。 */
    @Test
    fun `越界节次返回空时间文本`() {
        assertEquals("", PeriodTimes.rangeText(Campus.CHENGYANG, 99, 100))
    }

    /** 单节次查询时 start == end 用的都是同一节。 */
    @Test
    fun `单节次时间跨度等于该节自身`() {
        val slot = PeriodTimes.get(Campus.CHENGYANG, 3)!!
        assertEquals(slot.rangeText, PeriodTimes.rangeText(Campus.CHENGYANG, 3, 3))
    }

    /** 时间文本固定 5 字符（HH:mm），38dp 的左列才放得下不换行。 */
    @Test
    fun `时间文本固定为五位`() {
        for (campus in campuses) {
            for (slot in PeriodTimes.of(campus)) {
                assertEquals("${campus} 第 ${slot.index} 节", 5, slot.startText.length)
                assertEquals("${campus} 第 ${slot.index} 节", 5, slot.endText.length)
            }
        }
    }
}
