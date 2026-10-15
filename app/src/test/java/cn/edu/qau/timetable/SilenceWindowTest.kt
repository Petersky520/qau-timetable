package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.SilenceWindow
import cn.edu.qau.timetable.core.TimeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 上课静音时段的计算。
 *
 * 这里最容易出错、也最值得测的是**合并规则**：判错了手机就会在课间
 * 反复切换铃声（每 10 分钟响一次），或者反过来把整个白天都静音。
 * 所以下面既测"该合的合"，也测"不该合的不合"。
 */
class SilenceWindowTest {

    private fun t(h: Int, m: Int): LocalTime = LocalTime.of(h, m)

    private fun LocalTime.text(): String = "%02d:%02d".format(hour, minute)

    private fun List<TimeSpan>.texts(): List<String> = map { "${it.start.text()}-${it.end.text()}" }

    // ---------------------------------------------------------- 合并规则

    /** 15 分钟正好是青农大的课间，必须合并，否则每个课间都恢复铃声。 */
    @Test
    fun `间隔 15 分钟的课间要合并`() {
        val merged = SilenceWindow.merge(
            listOf(TimeSpan(t(8, 0), t(9, 40)), TimeSpan(t(9, 55), t(11, 35)))
        )
        assertEquals(listOf("08:00-11:35"), merged.texts())
    }

    /** 恰好超过阈值就不该合并 —— 边界值单独钉住。 */
    @Test
    fun `间隔 16 分钟不合并`() {
        val merged = SilenceWindow.merge(
            listOf(TimeSpan(t(8, 0), t(9, 40)), TimeSpan(t(9, 56), t(11, 35)))
        )
        assertEquals(listOf("08:00-09:40", "09:56-11:35"), merged.texts())
    }

    /** 1-2 节与 3-4 节连堂：首尾正好相接，中间不该闪一下。 */
    @Test
    fun `首尾相接视为同一段`() {
        val merged = SilenceWindow.merge(
            listOf(TimeSpan(t(11, 35), t(12, 0)), TimeSpan(t(8, 0), t(11, 35)))
        )
        assertEquals(listOf("08:00-12:00"), merged.texts())
    }

    /** 午休 12:00→14:00 有 120 分钟，绝不能被并成"静音一整天"。 */
    @Test
    fun `午休不合并`() {
        val merged = SilenceWindow.merge(
            listOf(TimeSpan(t(8, 0), t(12, 0)), TimeSpan(t(14, 0), t(17, 35)))
        )
        assertEquals(listOf("08:00-12:00", "14:00-17:35"), merged.texts())
    }

    /** 被完全包住的时段（比如同一时间有别的课）不能把结束时间缩短。 */
    @Test
    fun `被包住的时段不能缩短整体`() {
        val merged = SilenceWindow.merge(
            listOf(TimeSpan(t(8, 0), t(10, 0)), TimeSpan(t(9, 0), t(9, 30)))
        )
        assertEquals(listOf("08:00-10:00"), merged.texts())
    }

    /** 输入不保证有序，输出必须按开始时间升序。 */
    @Test
    fun `乱序输入也能正确处理`() {
        val merged = SilenceWindow.merge(
            listOf(
                TimeSpan(t(18, 50), t(20, 30)),
                TimeSpan(t(8, 0), t(9, 40)),
                TimeSpan(t(14, 0), t(15, 40)),
            )
        )
        assertEquals(listOf("08:00-09:40", "14:00-15:40", "18:50-20:30"), merged.texts())
    }

    @Test
    fun `空输入返回空`() {
        assertTrue(SilenceWindow.merge(emptyList()).isEmpty())
    }

    @Test
    fun `单段原样返回`() {
        val one = listOf(TimeSpan(t(8, 0), t(11, 35)))
        assertEquals(listOf("08:00-11:35"), SilenceWindow.merge(one).texts())
    }

    // ---------------------------------------------------------- 节次换算

    /** 真实的青农大一天：上午 1-2/3-4、下午 6-7/8-9 —— 应该只得到上午、下午两段。 */
    @Test
    fun `真实课表得到上午和下午两段`() {
        val spans = SilenceWindow.of(
            Campus.CHENGYANG,
            listOf(1..2, 3..4, 6..7, 8..9),
        )
        assertEquals(listOf("08:00-11:35", "14:00-17:35"), SilenceWindow.merge(spans).texts())
    }

    /** 4 节的实验课是一整块，不能被切开。 */
    @Test
    fun `四节连上的实验课是一段`() {
        val spans = SilenceWindow.of(Campus.CHENGYANG, listOf(1..4))
        assertEquals(listOf("08:00-11:35"), SilenceWindow.merge(spans).texts())
    }

    /** 两个校区作息不同，平度上午整体晚 30 分钟。 */
    @Test
    fun `平度校区用自己的作息`() {
        val spans = SilenceWindow.of(Campus.PINGDU, listOf(1..2))
        assertEquals(listOf("08:30-10:10"), SilenceWindow.merge(spans).texts())
    }

    /** 越界节次读不出作息，要整段丢掉，而不是猜一个时间。 */
    @Test
    fun `越界节次被丢弃`() {
        val spans = SilenceWindow.of(Campus.CHENGYANG, listOf(12..13, 1..1))
        assertEquals(listOf("08:00-08:45"), SilenceWindow.merge(spans).texts())
    }

    /** 阈值可覆盖：把 15 改成 0 时，普通的课间就该被拆开。 */
    @Test
    fun `阈值可以覆盖`() {
        val spans = SilenceWindow.of(Campus.CHENGYANG, listOf(1..2, 3..4))
        val merged = SilenceWindow.merge(spans, maxGapMinutes = 0)
        assertEquals(listOf("08:00-09:40", "09:55-11:35"), merged.texts())
    }

    // ---------------------------------------------------- 通知按钮要静到哪一段

    /** 上午 1-2/3-4 节连堂（课间 15 分钟会被合并），下午 6-7 节。 */
    private val day = SilenceWindow.merge(
        SilenceWindow.of(Campus.CHENGYANG, listOf(1..2, 3..4, 6..7))
    )

    /** 正在上课时点在时段中间：应该持续到这一段结束。 */
    @Test
    fun `正在进行的时段优先`() {
        assertEquals("08:00-11:35", SilenceWindow.spanFor(day, t(8, 30))?.text())
    }

    /**
     * 课前点按钮 —— 通知本来就在上课前 N 分钟弹，这一刻还不在任何时段里。
     * 这是最常见的一种，必须返回**接下来**那一段，而不是 null。
     */
    @Test
    fun `课前点按钮取接下来那一段`() {
        assertEquals("08:00-11:35", SilenceWindow.spanFor(day, t(7, 45))?.text())
    }

    /** 课间的 15 分钟本来就并入同一段，落在课间不该被算成"没在上课"。 */
    @Test
    fun `课间仍算在上午这一段里`() {
        assertEquals("08:00-11:35", SilenceWindow.spanFor(day, t(9, 48))?.text())
    }

    /** 午休（上午已过、下午还有课）：应落到下午那一段。 */
    @Test
    fun `午休取下午那一段`() {
        assertEquals("14:00-15:40", SilenceWindow.spanFor(day, t(12, 30))?.text())
        assertEquals("14:00-15:40", SilenceWindow.spanFor(day, t(13, 0))?.text())
    }

    /** 边界：正好落在开始/结束时刻上，算在这一段内。 */
    @Test
    fun `边界时刻算在段内`() {
        assertEquals("08:00-11:35", SilenceWindow.spanFor(day, t(8, 0))?.text())
        assertEquals("08:00-11:35", SilenceWindow.spanFor(day, t(11, 35))?.text())
    }

    /** 一天的课都上完了 —— 没有可用时段，调用方要自己去兜底。 */
    @Test
    fun `当天课程结束后没有可用时段`() {
        assertNull(SilenceWindow.spanFor(day, t(16, 0)))
        assertNull(SilenceWindow.spanFor(day, t(21, 0)))
        assertNull(SilenceWindow.spanFor(emptyList(), t(10, 0)))
    }

    private fun TimeSpan.text(): String = "${start.text()}-${end.text()}"
}
