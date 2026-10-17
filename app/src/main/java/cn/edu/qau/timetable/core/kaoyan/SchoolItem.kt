package cn.edu.qau.timetable.core.kaoyan

/** 院校招生条目：一个 (院校, 专业) 组合就是一条。 */
data class SchoolItem(
    /** 稳定主键，形如 "10423-0812"，院校代码 + 学科代码。 */
    val id: String,
    val name: String,
    val province: String,
    val city: String,
    /** 院校层次的原文本（如 "985"），展示用；打分时用 [SchoolTier.fromName] 解析。 */
    val tier: String,
    val majorCode: String,
    val majorName: String,
    /** 近两年复试线，0 表示未收录。 */
    val scoreLast: Int = 0,
    val scorePrev: Int = 0,
    /** 招生人数 / 报考人数，0 表示未收录。 */
    val quota: Int = 0,
    val applicants: Int = 0,
    val mathRequired: Boolean = false,
    /** 标签，逗号分隔，如 "自划线,保护一志愿"。 */
    val tags: String = "",
    val note: String = "",
) {
    /** 报录比；数据不全时返回 0，调用方据此显示「未收录」而不是显示 0.0。 */
    val applyRatio: Double
        get() = if (quota > 0 && applicants > 0) applicants.toDouble() / quota else 0.0
}
