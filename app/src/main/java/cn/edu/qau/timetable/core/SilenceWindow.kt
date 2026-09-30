package cn.edu.qau.timetable.core

import java.time.Duration
import java.time.LocalTime

/** 一个连续的时间区间。 */
data class TimeSpan(val start: LocalTime, val end: LocalTime)

/**
 * 上课静音时段的计算。
 *
 * 纯 Kotlin、不依赖 Android，因此可以直接单测 —— 这里最容易出错的地方是
 * 「哪些课该算作同一段静音」，猜错会导致手机在课间反复切换铃声。
 */
object SilenceWindow {

    /**
     * 两个时段之间的间隔不超过这么多分钟时，合并成一段、中间不恢复铃声。
     *
     * 取 15 分钟的理由：
     *   · 青农大的课间是 10~15 分钟（如 8:45→8:55、9:40→9:55），
     *     不合并的话手机每个课间都要响一次，既吵又费电；
     *   · 午休是 12:00→14:00（120 分钟）、晚饭是 17:35→18:50（75 分钟），
     *     远大于 15，所以不会被误并成"整个白天都静音"。
     */
    const val DEFAULT_MERGE_GAP_MINUTES = 15

    /**
     * 把「节次区间」按校区作息换算成时间区间。
     *
     * 节次越界（读不出作息）的区间会被丢掉，而不是猜一个时间。
     */
    fun of(campus: Campus, periodRanges: List<IntRange>): List<TimeSpan> =
        periodRanges.mapNotNull { range ->
            val start = PeriodTimes.startOf(campus, range.first) ?: return@mapNotNull null
            val end = PeriodTimes.endOf(campus, range.last) ?: return@mapNotNull null
            if (end <= start) null else TimeSpan(start, end)
        }

    /**
     * 合并相互重叠的、以及间隔不超过 [maxGapMinutes] 的区间。
     *
     * 输入不要求有序；输出按开始时间升序。相邻两段首尾正好相接（间隔 0）
     * 也会合并，这样 1-2 节与 3-4 节连堂不会在中间闪一下。
     */
    fun merge(
        spans: List<TimeSpan>,
        maxGapMinutes: Int = DEFAULT_MERGE_GAP_MINUTES,
    ): List<TimeSpan> {
        if (spans.size <= 1) return spans
        val sorted = spans.sortedBy { it.start }
        val out = ArrayList<TimeSpan>(sorted.size)
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val gap = Duration.between(current.end, next.start).toMinutes()
            current = if (gap <= maxGapMinutes) {
                // 取更晚的结束时间：兼容器时段被别的课包住的情况
                TimeSpan(current.start, maxOf(current.end, next.end))
            } else {
                out += current
                next
            }
        }
        out += current
        return out
    }
}
