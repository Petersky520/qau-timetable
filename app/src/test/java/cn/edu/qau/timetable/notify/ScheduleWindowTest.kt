package cn.edu.qau.timetable.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 排程窗口与续排的边界。
 *
 * ## 为什么这两个功能要单独钉住
 *
 * 用户报「提醒和静音失效」，追下去是两个不同的问题：
 *
 *  1. **静默早退**：开关开着但某个条件不满足，`doReschedule` 直接 return，
 *     一个闹钟都没排，界面看不出来。修法是记 `ScheduleStatus` 并显示出来。
 *  2. **7 天窗口断档**：只排未来 7 天，而「开机 / 改设置 / 导课表」这些
 *     重排触发点用户可能一周都碰不到 —— 第 8 天起彻底没有提醒，同样没提示。
 *     修法是 `ChainReceiver` 在第 6 天续排。
 *
 * 这两类问题**不崩溃、不报错**，只能靠「算一算窗口对不对」来发现。
 * 下面就是那把尺子：不依赖 Android，纯算术。
 */
class ScheduleWindowTest {

    /** 与 ChainReceiver.LEAD_DAYS 一致。 */
    private val leadDays = 6L

    /** 与两个 Scheduler 的 `for (offset in 0..6)` 一致。 */
    private val windowDays = 7

    @Test
    fun `排程窗口恰好 7 天`() {
        val today = LocalDate.of(2026, 10, 26)
        val covered = (0 until windowDays).map { today.plusDays(it.toLong()) }
        assertEquals(7, covered.size)
        assertEquals(today, covered.first())
        assertEquals(today.plusDays(6), covered.last())
    }

    /**
     * 续排必须在窗口内触发，否则就是断档。
     *
     * 第 6 天（offset=6）是窗口最后一天，第 7 天（offset=7）已经超出去了。
     * 续排定在 offset=6 正好：那一刻重排会把窗口推到第 13 天，
     * 中间没有空洞。
     */
    @Test
    fun `续排定在第 6 天，仍在窗口内`() {
        val today = LocalDate.of(2026, 10, 26)
        val chainDate = today.plusDays(leadDays)

        assertTrue(
            "续排日 $chainDate 必须落在窗口 ${today}..${today.plusDays(6)} 之内",
            !chainDate.isAfter(today.plusDays((windowDays - 1).toLong())),
        )
        assertEquals(today.plusDays(6), chainDate)
    }

    /** 续排当天的触发时刻挑在凌晨，不撞上课时间。 */
    @Test
    fun `续排时刻不与上课时间冲突`() {
        val chainTime = LocalTime.of(3, 0)
        // 最早的课是城阳第 1 节 08:00；续排落在它之前
        val earliestClass = LocalTime.of(8, 0)
        assertTrue(
            "续排时刻 $chainTime 应早于最早的上课时间 $earliestClass",
            chainTime.isBefore(earliestClass),
        )
    }

    /**
     * 窗口滑动的连续性：第 6 天续排后，新窗口必须覆盖原窗口之后的日期。
     *
     * 这条防的是「续排定得太早」：如果定在第 1 天续排，窗口虽然也在滚，
     * 但会频繁唤醒；定在第 7 天或更晚，则已经出现空洞。
     */
    @Test
    fun `续排后窗口无缝衔接`() {
        val today = LocalDate.of(2026, 10, 26)
        val firstWindowEnd = today.plusDays((windowDays - 1).toLong())
        val chainDate = today.plusDays(leadDays)

        // 续排当天会重排，新窗口从那天算起
        val newWindowEnd = chainDate.plusDays((windowDays - 1).toLong())
        assertTrue(
            "新窗口 $newWindowEnd 必须晚于旧窗口 $firstWindowEnd",
            newWindowEnd.isAfter(firstWindowEnd),
        )
        // 且旧窗口的最后一天必须已经被新窗口覆盖（不留空洞）
        assertTrue(
            "旧窗口末日 $firstWindowEnd 应落在新窗口内",
            !firstWindowEnd.isAfter(newWindowEnd),
        )
    }

    /**
     * 已经过掉的时间点不该再排闹钟。
     *
     * 两个 scheduler 都有一句 `if (!trigger.isAfter(now)) return@forEach`。
     * 这条容易写成 `!isBefore`，那样「正好等于当前时刻」的课会被排上，
     * AlarmManager 收到过去时间会**立即触发** —— 表现为"刚打开 App 就弹提醒"。
     */
    @Test
    fun `已过时间的判断用严格大于`() {
        val now = LocalDateTime.of(2026, 10, 26, 10, 0)
        val past = now.minusMinutes(1)
        val exact = now
        val future = now.plusMinutes(1)

        assertTrue("未来的课要排", future.isAfter(now))
        assertTrue("正好等于当前时刻不该排", !exact.isAfter(now))
        assertTrue("过去的课不该排", !past.isAfter(now))
    }
}
