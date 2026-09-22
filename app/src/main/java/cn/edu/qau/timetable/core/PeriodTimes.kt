package cn.edu.qau.timetable.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 一节课（节次）的时间段。 */
data class ClassPeriod(
    val index: Int,
    val start: LocalTime,
    val end: LocalTime,
) {
    val startText: String get() = start.format(HM)
    val endText: String get() = end.format(HM)
    val rangeText: String get() = "$startText-$endText"

    companion object {
        private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * 青岛农业大学作息时间表。
 *
 * 数据来源：青岛农业大学教务处《青岛农业大学教学时间表》
 *   https://jw.qau.edu.cn/content/wdxz/ed8334513205406e94eb41c305440aca
 *
 * 注意：城阳校区与平度校区**上课时间不同**（平度上午整体晚 30 分钟，
 * 且第 5 节为 12:00-12:25，城阳为 11:35-12:00），所以必须按校区取用。
 */
object PeriodTimes {

    private val CHENGYANG: List<ClassPeriod> = listOf(
        ClassPeriod(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        ClassPeriod(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
        ClassPeriod(3, LocalTime.of(9, 55), LocalTime.of(10, 40)),
        ClassPeriod(4, LocalTime.of(10, 50), LocalTime.of(11, 35)),
        ClassPeriod(5, LocalTime.of(11, 35), LocalTime.of(12, 0)),
        ClassPeriod(6, LocalTime.of(14, 0), LocalTime.of(14, 45)),
        ClassPeriod(7, LocalTime.of(14, 55), LocalTime.of(15, 40)),
        ClassPeriod(8, LocalTime.of(15, 55), LocalTime.of(16, 40)),
        ClassPeriod(9, LocalTime.of(16, 50), LocalTime.of(17, 35)),
        ClassPeriod(10, LocalTime.of(18, 50), LocalTime.of(19, 35)),
        ClassPeriod(11, LocalTime.of(19, 45), LocalTime.of(20, 30)),
    )

    private val PINGDU: List<ClassPeriod> = listOf(
        ClassPeriod(1, LocalTime.of(8, 30), LocalTime.of(9, 15)),
        ClassPeriod(2, LocalTime.of(9, 25), LocalTime.of(10, 10)),
        ClassPeriod(3, LocalTime.of(10, 20), LocalTime.of(11, 5)),
        ClassPeriod(4, LocalTime.of(11, 15), LocalTime.of(12, 0)),
        ClassPeriod(5, LocalTime.of(12, 0), LocalTime.of(12, 25)),
        ClassPeriod(6, LocalTime.of(14, 0), LocalTime.of(14, 45)),
        ClassPeriod(7, LocalTime.of(14, 55), LocalTime.of(15, 40)),
        ClassPeriod(8, LocalTime.of(15, 50), LocalTime.of(16, 35)),
        ClassPeriod(9, LocalTime.of(16, 45), LocalTime.of(17, 30)),
        ClassPeriod(10, LocalTime.of(18, 50), LocalTime.of(19, 35)),
        ClassPeriod(11, LocalTime.of(19, 45), LocalTime.of(20, 30)),
    )

    /** 上午/下午/晚上 的预备铃时间，用于提前提醒。 */
    private val CHENGYANG_PREP = listOf(LocalTime.of(7, 50), LocalTime.of(13, 50), LocalTime.of(18, 40))
    private val PINGDU_PREP = listOf(LocalTime.of(8, 20), LocalTime.of(13, 50), LocalTime.of(18, 40))

    const val PERIOD_COUNT = 11

    fun of(campus: Campus): List<ClassPeriod> = when (campus) {
        Campus.CHENGYANG -> CHENGYANG
        Campus.PINGDU -> PINGDU
    }

    fun prepTimes(campus: Campus): List<LocalTime> = when (campus) {
        Campus.CHENGYANG -> CHENGYANG_PREP
        Campus.PINGDU -> PINGDU_PREP
    }

    fun get(campus: Campus, index: Int): ClassPeriod? = of(campus).firstOrNull { it.index == index }

    /** 形如 "08:00-11:35" 的时间跨度文本；节次越界时返回空串。 */
    fun rangeText(campus: Campus, startPeriod: Int, endPeriod: Int): String {
        val s = get(campus, startPeriod) ?: return ""
        val e = get(campus, endPeriod) ?: s
        return "${s.startText}-${e.endText}"
    }

    /** 该节次的上课时刻（用于提醒）。 */
    fun startOf(campus: Campus, period: Int): LocalTime? = get(campus, period)?.start
}
