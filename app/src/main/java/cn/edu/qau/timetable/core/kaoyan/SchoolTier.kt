package cn.edu.qau.timetable.core.kaoyan

/**
 * 院校层次。
 *
 * `weight` 是打分用的粗粒度刻度（0..100），不是排名 —— 推荐引擎只关心
 * 「本校 vs 目标院校」的**差**，把层次压成一条整数轴，层次差才能直接用减法算。
 * 层次越细分越容易造成「同一所院校两个标签权重打架」，所以这里只保留五档。
 */
enum class SchoolTier(val label: String, val weight: Int) {
    C9("C9 / 顶尖", 100),
    PROJECT_985("985", 85),
    PROJECT_211("211", 70),
    DOUBLE_FIRST_CLASS("双一流", 60),
    REGULAR("普通院校", 40),
    ;

    companion object {
        /**
         * 从院校条目的 `tier` 文本还原层次。
         *
         * 数据可能来自内置 JSON、远端刷新或用户手改，文本不保证规范，
         * 所以同时认枚举名和中文标签，认不出就当「普通院校」——
         * 宁可少加分，也不要把一所没收录层次的学校误判成 985。
         */
        fun fromName(name: String?): SchoolTier =
            entries.firstOrNull { it.name == name || it.label == name } ?: REGULAR
    }
}
