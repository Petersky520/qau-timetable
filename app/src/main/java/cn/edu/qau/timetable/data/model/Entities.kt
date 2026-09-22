package cn.edu.qau.timetable.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.Term
import cn.edu.qau.timetable.core.WeekPattern
import cn.edu.qau.timetable.core.WeekPatternParser
import cn.edu.qau.timetable.domain.CourseEvent
import java.time.LocalDate

@Entity(tableName = "terms")
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 形如 2026-2027-1 */
    val name: String,
    /** 第 1 周周一，ISO yyyy-MM-dd */
    val startDate: String,
    val totalWeeks: Int = Term.DEFAULT_WEEKS,
    val campus: String = Campus.CHENGYANG.name,
    val active: Boolean = false,
) {
    fun toDomain(): Term = Term(
        name = name,
        startMonday = runCatching { LocalDate.parse(startDate) }.getOrElse { LocalDate.now() },
        totalWeeks = totalWeeks,
        campus = Campus.fromName(campus),
    )
}

@Entity(tableName = "courses", indices = [Index("termId")])
data class CourseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 1 = 周一 … 7 = 周日 */
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    /** 原始周次文本，例如 "1-16周(单)" */
    val weekRaw: String = "",
    /** 解析后的周次，形如 "1,3,5,7" */
    val weeks: String = "",
    val colorSeed: Int = 0,
) {
    fun toDomain(): CourseEvent = CourseEvent(
        id = id,
        name = name,
        teacher = teacher,
        room = room,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        pattern = if (weeks.isBlank()) {
            WeekPatternParser.parse(weekRaw)
        } else {
            WeekPattern(weekRaw, WeekPattern.fromStorage(weeks))
        },
    )

    companion object {
        fun fromDomain(termId: Long, e: CourseEvent, colorSeed: Int = 0): CourseEventEntity =
            CourseEventEntity(
                termId = termId,
                name = e.name,
                teacher = e.teacher,
                room = e.room,
                dayOfWeek = e.dayOfWeek,
                startPeriod = e.startPeriod,
                endPeriod = e.endPeriod,
                weekRaw = e.pattern.raw,
                weeks = e.pattern.toStorage(),
                colorSeed = colorSeed,
            )
    }
}

@Entity(tableName = "exams", indices = [Index("termId")])
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val course: String,
    /** yyyy-MM-dd */
    val date: String = "",
    /** 例如 14:00-16:00 */
    val time: String = "",
    val room: String = "",
    val seat: String = "",
    val note: String = "",
)

@Entity(tableName = "grades", indices = [Index("termId")])
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val termName: String = "",
    val course: String,
    val credit: Double = 0.0,
    val score: Double = 0.0,
    val gpa: Double = 0.0,
    val kind: String = "",
)

@Entity(tableName = "classrooms")
data class ClassroomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val building: String = "",
    val room: String,
    val campus: String = "",
    /** 空闲节次，形如 "1-2,5-6" */
    val freeSlots: String = "",
    val capacity: Int = 0,
)
