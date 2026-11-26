package cn.edu.qau.timetable.data.qz

/** 青农大教务系统入口及各同步目标的默认页面。 */
object QzEndpoints {

    const val BASE = "http://jwglxt.qau.edu.cn/jsxsd/"
    const val HOME = BASE

    const val TIMETABLE = BASE + "xskb/xskb_list.do"
    const val GRADES = BASE + "kscj/cjcx_list"

    /** 抓取目标。 */
    enum class Target(val key: String, val label: String) {
        TIMETABLE("kb", "课表"),
        GRADES("grade", "成绩"),
    }

    fun defaultUrl(target: Target): String = when (target) {
        Target.TIMETABLE -> TIMETABLE
        Target.GRADES -> GRADES
    }
}
