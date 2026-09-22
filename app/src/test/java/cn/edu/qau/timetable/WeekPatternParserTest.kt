package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.WeekPatternParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPatternParserTest {

    @Test
    fun `连续周次区间`() {
        val p = WeekPatternParser.parse("1-16周")
        assertEquals((1..16).toSet(), p.weeks)
        assertTrue(p.contains(1))
        assertTrue(p.contains(16))
        assertFalse(p.contains(17))
    }

    @Test
    fun `单周`() {
        val p = WeekPatternParser.parse("1-16周(单)")
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), p.weeks)
        assertTrue(p.oddOnly)
    }

    @Test
    fun `双周 全角括号`() {
        val p = WeekPatternParser.parse("1-16周（双）")
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), p.weeks)
        assertTrue(p.evenOnly)
    }

    @Test
    fun `多段区间`() {
        val p = WeekPatternParser.parse("1-8,10-16周")
        assertEquals((1..8).toSet() + (10..16).toSet(), p.weeks)
    }

    @Test
    fun `散点周次`() {
        val p = WeekPatternParser.parse("1,3,5,7周")
        assertEquals(setOf(1, 3, 5, 7), p.weeks)
    }

    @Test
    fun `带第字和中文破折号`() {
        val p = WeekPatternParser.parse("第3—15周")
        assertEquals((3..15).toSet(), p.weeks)
    }

    @Test
    fun `单周次`() {
        val p = WeekPatternParser.parse("5周")
        assertEquals(setOf(5), p.weeks)
    }

    @Test
    fun `解析不出来时退回全周而不是丢课`() {
        val p = WeekPatternParser.parse("周次待定")
        assertTrue(p.weeks.size >= 16)
        assertTrue(p.contains(1))
    }

    @Test
    fun `空串表示全周`() {
        assertTrue(WeekPatternParser.parse("").contains(20))
    }

    @Test
    fun `存库往返`() {
        val p = WeekPatternParser.parse("1-8,10-12周")
        val restored = cn.edu.qau.timetable.core.WeekPattern("", cn.edu.qau.timetable.core.WeekPattern.fromStorage(p.toStorage()))
        assertEquals(p.weeks, restored.weeks)
    }
}
