package cn.edu.qau.timetable.core.kaoyan

/**
 * 用户的报考意向画像。
 *
 * 这是推荐引擎的唯一输入，刻意做成不可变 data class + 默认值，
 * 这样 UI 侧只要 `copy()`，不用维护一堆分散的 mutableState。
 *
 * 默认值取「计算机 / 地区不限 / 稳妥 / 普通本科」——
 * 即第一次打开就有结果可看，而不是逼用户先填完表单。
 */
data class KaoyanProfile(
    val major: Major = Major.COMPUTER,
    val region: Region = Region.ANY,
    val ambition: Ambition = Ambition.MATCH,
    /** 本科院校层次，用来估算「冲」的跨度是否合理。 */
    val undergraduateTier: SchoolTier = SchoolTier.REGULAR,
    /** 是否接受跨专业。跨考时推荐会放宽专业匹配权重。 */
    val crossMajor: Boolean = false,
    /** 是否愿意接受考数学。false 时过滤掉强依赖数学的院校专业。 */
    val acceptMath: Boolean = true,
    /** 期望的最低招生人数（太少的话调剂风险高）。 */
    val minQuota: Int = 0,
)
