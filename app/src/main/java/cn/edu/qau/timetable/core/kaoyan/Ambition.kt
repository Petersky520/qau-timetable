package cn.edu.qau.timetable.core.kaoyan

/**
 * 报考意愿：冲刺 / 稳妥 / 保底。决定推荐的分数带，而不是「难度」。
 *
 * 刻意和 [SchoolTier] 分开：层次是院校的客观属性，意愿是用户的主观策略。
 * 同一条院校，对本科 985 的人是「保底」，对普通本科的人就是「冲刺」。
 */
enum class Ambition(val label: String, val hint: String) {
    REACH("冲刺", "比自己当前水平高一些，值得一冲"),
    MATCH("稳妥", "和当前水平相当，正常发挥就能上"),
    SAFE("保底", "明显低于当前水平，用来兜底"),
    ;

    companion object {
        /**
         * 从持久化值还原报考意愿。
         *
         * 认不出时落到 [MATCH]：三档里只有「稳妥」是中性档，
         * 落到冲刺会让用户看到一堆够不着的学校，落到保底又会把好学校藏起来。
         */
        fun fromName(name: String?): Ambition =
            entries.firstOrNull { it.name == name || it.label == name } ?: MATCH
    }
}
