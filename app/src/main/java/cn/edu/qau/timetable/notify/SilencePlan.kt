package cn.edu.qau.timetable.notify

import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.SilenceWindow
import cn.edu.qau.timetable.core.TimeSpan
import cn.edu.qau.timetable.domain.CourseEvent
import java.time.LocalDate
import java.time.LocalTime

/**
 * 「某一天要静音哪几段」的规划，[SilenceScheduler] 和通知上的「上课静音」按钮共用。
 *
 * 两处必须走同一套算法。按钮如果按**单节课**的节次去算结束时间，就会比自动静音
 * 更早恢复铃声 —— 连堂课（1-2 节接 3-4 节）会被 [SilenceWindow.merge] 并成一段，
 * 于是同一天里出现两种行为，用户会觉得按钮"不管用"。
 */
internal object SilencePlan {

    /** 某一天（已合并）需要静音的时段，按开始时间升序。 */
    fun spansOn(
        campus: Campus,
        courses: List<CourseEvent>,
        date: LocalDate,
        week: Int,
    ): List<TimeSpan> {
        val ranges = courses
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.occursIn(week) }
            .map { it.startPeriod..it.endPeriod }
        if (ranges.isEmpty()) return emptyList()
        return SilenceWindow.merge(SilenceWindow.of(campus, ranges))
    }

    /**
     * 闹钟的 requestCode：以「(日期, 时刻)」唯一。
     *
     * 用时刻而不是「当天第几段」做 key，是因为通知按钮不知道自己是第几段 ——
     * 而两处算出的结束时刻相同时 requestCode 也就相同，[android.app.PendingIntent]
     * 会因为 `FLAG_UPDATE_CURRENT` 原地覆盖，而不是多排一个重复的还原闹钟。
     *
     * 同一天内开始时刻与结束时刻互不重复（[SilenceWindow.merge] 输出的是
     * 互不重叠的有序区间），所以不存在撞号。跨天撞号需要相隔约 1491 天，
     * 而闹钟只排未来 7 天，够不着。
     */
    fun codeOf(date: LocalDate, time: LocalTime): Int =
        ((date.toEpochDay() * MINUTES_PER_DAY + time.toSecondOfDay() / 60L) % Int.MAX_VALUE).toInt()

    private const val MINUTES_PER_DAY = 1440L
}
