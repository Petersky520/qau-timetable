package cn.edu.qau.timetable.data.qz

/**
 * 青农大教务系统入口。
 *
 * ⚠️ 这些路径是强智 jsxsd 的常见默认值，但**各校部署可能不同**。
 * 因此 App 的抓取流程优先使用「在页面里按关键词找链接并跟随」，
 * 只有在找不到时才回退到这里的固定 URL；同时这三个 URL 在设置页里可改。
 */
object QzEndpoints {

    const val BASE = "http://jwglxt.qau.edu.cn/jsxsd/"
    const val HOME = BASE

    const val TIMETABLE = BASE + "xskb/xskb_list.do"
    const val EXAMS = BASE + "kwgl/kscx_ksxx"
    const val GRADES = BASE + "kscj/cjcx_list"
    const val CLASSROOMS = BASE + "kbcx/kbxx_classroom"

    /** 抓取目标。 */
    enum class Target(val key: String, val label: String) {
        TIMETABLE("kb", "课表"),
        EXAMS("exam", "考试安排"),
        GRADES("grade", "成绩"),
        CLASSROOMS("classroom", "空闲教室"),
    }

    /** 用于在教务系统页面里自动定位入口的关键词。 */
    fun keywords(target: Target): List<String> = when (target) {
        Target.TIMETABLE -> listOf("我的课表", "学生课表", "课表查询", "课程表", "课表")
        Target.EXAMS -> listOf("考试安排", "考试查询", "考试信息", "考试")
        Target.GRADES -> listOf("成绩查询", "成绩信息", "学生成绩", "成绩")
        Target.CLASSROOMS -> listOf("空闲教室", "空教室", "教室查询", "教室借用")
    }

    fun defaultUrl(target: Target): String = when (target) {
        Target.TIMETABLE -> TIMETABLE
        Target.EXAMS -> EXAMS
        Target.GRADES -> GRADES
        Target.CLASSROOMS -> CLASSROOMS
    }
}
