package cn.edu.qau.timetable

import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.Term
import cn.edu.qau.timetable.core.WeekPattern
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.domain.coursesOn
import cn.edu.qau.timetable.domain.nextCourseDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CourseAgendaTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private val term = Term("2026-2027-1", monday, totalWeeks = 3)
    private val mondayClass = CourseEvent(
        name = "植物学",
        dayOfWeek = 1,
        startPeriod = 3,
        endPeriod = 4,
        pattern = WeekPattern("", setOf(1, 3)),
    )

    @Test
    fun makeupDayUsesTheCourseWeekAndHolidayClearsTheDay() {
        val makeupSaturday = monday.plusDays(5)
        val overrides = listOf(DayOverride(monday, null), DayOverride(makeupSaturday, 1))
        assertEquals(emptyList<CourseEvent>(), term.coursesOn(monday, listOf(mondayClass), overrides))
        assertEquals(listOf(mondayClass), term.coursesOn(makeupSaturday, listOf(mondayClass), overrides))
        assertEquals(
            makeupSaturday to listOf(mondayClass),
            term.nextCourseDay(monday, listOf(mondayClass), overrides),
        )
    }

    @Test
    fun nextClassCanBeMoreThanSevenDaysAwayButNeverOutsideTheTerm() {
        assertEquals(
            monday.plusWeeks(2) to listOf(mondayClass),
            term.nextCourseDay(monday, listOf(mondayClass), emptyList()),
        )
        assertNull(term.nextCourseDay(monday.plusWeeks(2), listOf(mondayClass), emptyList()))
    }
}
