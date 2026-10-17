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

// 原先这里还有一个 ClassroomItem（空闲教室条目）。
// 学校教务系统禁止访问空闲教室界面，该功能已整体下线，模型一并移除。
