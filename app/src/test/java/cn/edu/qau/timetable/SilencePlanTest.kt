package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.TimeSpan
import cn.edu.qau.timetable.core.WeekPattern
import cn.edu.qau.timetable.core.WeekPatternParser
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.notify.SilencePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「某一天该静音哪几段」的规划 —— 通知上的「上课静音」按钮和自动静音共用它。
 *
 * 重点钉两件事：
 *   · 同一时段的连堂课要算成**一整段**。算短了，按钮就会比自动静音提前恢复
 *     铃声，用户会觉得"按钮不管用"；
 *   · requestCode 的算法。算重了会互相覆盖还原闹钟，算漏了会留下没人取消的闹钟。
 */
class SilencePlanTest {

    private val monday = LocalDate.of(2026, 9, 28)
    private val tuesday = LocalDate.of(2026, 9, 29)

    private fun course(
        name: String,
        day: Int,
        from: Int,
        to: Int,
        patternRaw: String = "",
    ) = CourseEvent(
        name = name,
        dayOfWeek = day,
        startPeriod = from,
        endPeriod = to,
        pattern = if (patternRaw.isEmpty()) {
            WeekPattern.ALWAYS
        } else {
            WeekPatternParser.parse(patternRaw)
        },
    )

    private fun List<TimeSpan>.texts(): List<String> = map { "${it.start}-${it.end}" }

    // ---------------------------------------------------------------- 时段

    /** 1-2 节接 3-4 节：按钮要一直静到 3-4 节下课，而不是 1-2 节下课。 */
    @Test
    fun `连堂课合并成一整段`() {
        val spans = SilencePlan.spansOn(
            Campus.CHENGYANG,
            listOf(course("高数", 1, 1, 2), course("高数", 1, 3, 4)),
            monday,
            week = 4,
        )
        assertEquals(listOf("08:00-11:35"), spans.texts())
    }

    @Test
    fun `不是当天的课不参与`() {
        val spans = SilencePlan.spansOn(
            Campus.CHENGYANG,
            listOf(course("高数", 1, 1, 2), course("英语", 2, 1, 2)),
            monday,
            week = 4,
        )
        assertEquals(listOf("08:00-09:40"), spans.texts())
    }

    @Test
    fun `单周课在双周不参与`() {
        val courses = listOf(course("形策", 1, 1, 2, patternRaw = "1-16周(单)"))
        assertTrue(SilencePlan.spansOn(Campus.CHENGYANG, courses, monday, week = 4).isEmpty())
        assertEquals(
            listOf("08:00-09:40"),
            SilencePlan.spansOn(Campus.CHENGYANG, courses, monday, week = 5).texts(),
        )
    }

    /** 作息按校区取 —— 选错校区静音时段会整体错半小时。 */
    @Test
    fun `平度校区用自己的作息`() {
        val spans = SilencePlan.spansOn(
            Campus.PINGDU,
            listOf(course("高数", 1, 1, 2)),
            monday,
            week = 4,
        )
        assertEquals(listOf("08:30-10:10"), spans.texts())
    }

    @Test
    fun `没有课的日子返回空`() {
        assertTrue(SilencePlan.spansOn(Campus.CHENGYANG, emptyList(), monday, week = 4).isEmpty())
    }

    // ---------------------------------------------------------- requestCode

    /** 两处算出的时刻一致时必须得到同一个码 —— PendingIntent 才会被原地覆盖。 */
    @Test
    fun `同一日期同一时刻得到同一个码`() {
        assertEquals(
            SilencePlan.codeOf(monday, LocalTime.of(11, 35)),
            SilencePlan.codeOf(monday, LocalTime.of(11, 35)),
        )
    }

    /** 静音闹钟与还原闹钟用同一段的开始/结束时刻，必须区分得开。 */
    @Test
    fun `同一段静音的开始与结束是不同的码`() {
        assertNotEquals(
            SilencePlan.codeOf(monday, LocalTime.of(8, 0)),
            SilencePlan.codeOf(monday, LocalTime.of(11, 35)),
        )
    }

    @Test
    fun `不同日期得到不同的码`() {
        assertNotEquals(
            SilencePlan.codeOf(monday, LocalTime.of(8, 0)),
            SilencePlan.codeOf(tuesday, LocalTime.of(8, 0)),
        )
    }

    /** 排程窗口是未来 7 天，这 7 天里每一分钟都不能撞号。 */
    @Test
    fun `未来七天的码互不重复`() {
        val codes = ArrayList<Int>()
        for (offset in 0..6) {
            val date = monday.plusDays(offset.toLong())
            for (minute in 0 until 24 * 60) {
                val code = SilencePlan.codeOf(date, LocalTime.ofSecondOfDay(minute * 60L))
                assertTrue("requestCode 必须非负：$code", code >= 0)
                codes += code
            }
        }
        assertEquals(codes.size, codes.toSet().size)
    }
}
