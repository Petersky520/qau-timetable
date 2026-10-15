package cn.edu.qau.timetable.domain

import cn.edu.qau.timetable.core.WeekPattern

/**
 * UI / 业务层用的课程事件（与 Room 实体解耦）。
 * 一条记录 = 某门课在「星期几 + 第几节到第几节 + 哪些周」上课。
 */
data class CourseEvent(
    val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val pattern: WeekPattern = WeekPattern.ALWAYS,
) {
    fun occursIn(week: Int): Boolean = pattern.contains(week)

    val periodSpan: Int
        get() = (endPeriod - startPeriod + 1).coerceAtLeast(1)

    /** 同一格子里是否是同一门课（用于合并显示）。 */
    fun sameSlotAs(other: CourseEvent): Boolean =
        dayOfWeek == other.dayOfWeek &&
            startPeriod == other.startPeriod &&
            endPeriod == other.endPeriod

    val slotKey: String get() = "$dayOfWeek:$startPeriod:$endPeriod"
}
