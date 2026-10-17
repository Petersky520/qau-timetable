package cn.edu.qau.timetable.core.kaoyan

/**
 * 教育部学科门类。选校页按门类分组展示专业，否则 90 个 chip 无法浏览。
 *
 * 顺序即 UI 上的分组顺序，按《学位授予和人才培养学科目录》的门类序排列，
 * 不要按项目里新增专业的先后重排 —— 那会让用户在两次升级之间找不到原来的位置。
 * 军事学、交叉学科排在最后：它们是真实门类，但招生单位少，分组放末尾不抢注意力。
 */
enum class MajorCategory(val label: String) {
    PHILOSOPHY("哲学"),
    ECONOMICS("经济学"),
    LAW("法学"),
    EDUCATION("教育学"),
    LITERATURE("文学"),
    HISTORY("历史学"),
    SCIENCE("理学"),
    ENGINEERING("工学"),
    AGRICULTURE("农学"),
    MEDICINE("医学"),
    MANAGEMENT("管理学"),
    ART("艺术学"),
    MILITARY("军事学"),
    CROSS("交叉学科"),
    ;

    companion object {
        /**
         * 从持久化值还原门类。
         *
         * 认不出时退到 [ENGINEERING]：专业枚举里绝大多数专业属于工学，
         * 落到「工学」分组至少能让用户在默认分组里找到它，
         * 比塞进一个不存在的 OTHER 分组（UI 里没有这个分组，等于消失）更安全。
         */
        fun fromName(name: String?): MajorCategory =
            entries.firstOrNull { it.name == name || it.label == name } ?: ENGINEERING
    }
}
