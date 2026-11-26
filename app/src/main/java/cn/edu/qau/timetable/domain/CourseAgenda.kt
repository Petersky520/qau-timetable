package cn.edu.qau.timetable.domain

import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.Term
import java.time.LocalDate

internal fun Term.coursesOn(
    date: LocalDate,
    courses: List<CourseEvent>,
    overrides: List<DayOverride>,
): List<CourseEvent> {
    val week = weekOf(date)
    val day = DayOverrides.effectiveDayOfWeek(date, overrides)
    if (week == 0 || day == null) return emptyList()
    return courses.filter { it.dayOfWeek == day && it.occursIn(week) }
        .sortedWith(compareBy(CourseEvent::startPeriod, CourseEvent::endPeriod, CourseEvent::name))
}

internal fun Term.nextCourseDay(
    after: LocalDate,
    courses: List<CourseEvent>,
    overrides: List<DayOverride>,
): Pair<LocalDate, List<CourseEvent>>? {
    if (courses.isEmpty()) return null
    var date = maxOf(after.plusDays(1), startMonday)
    val end = startMonday.plusWeeks(totalWeeks.toLong())
    while (date < end) {
        val upcoming = coursesOn(date, courses, overrides)
        if (upcoming.isNotEmpty()) return date to upcoming
        date = date.plusDays(1)
    }
    return null
}
