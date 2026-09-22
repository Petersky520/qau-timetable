package cn.edu.qau.timetable.domain

/** 考试安排条目。 */
data class ExamItem(
    val course: String,
    val date: String = "",
    val time: String = "",
    val room: String = "",
    val seat: String = "",
)

/** 成绩条目。 */
data class GradeItem(
    val term: String = "",
    val course: String,
    val credit: Double = 0.0,
    val score: Double = 0.0,
    val gpa: Double = 0.0,
    val kind: String = "",
)

/** 空闲教室条目。 */
data class ClassroomItem(
    val building: String = "",
    val room: String,
    val campus: String = "",
    val freeSlots: String = "",
    val capacity: Int = 0,
)
