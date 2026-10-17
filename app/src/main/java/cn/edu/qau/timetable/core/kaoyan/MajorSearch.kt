package cn.edu.qau.timetable.core.kaoyan

/**
 * 专业目录的关键词检索。
 *
 * ## 为什么要在 core 层而不在 UI 里
 *
 * 这段逻辑的输入输出都是纯数据（关键词 → 专业列表），没有任何 Compose 依赖，
 * 放在这里就能用普通 JVM 单测逐条钉住匹配语义。如果写进 composable 里，
 * 它只能靠手工点界面验证 —— 而这类「字符串匹配」的 bug 恰恰是点了也不容易发现的
 * （比如代码前缀少判一项，用户只会觉得"搜 081 怎么少了两项"）。
 *
 * ## 为什么是「四路任一命中」而不是打分排序
 *
 * 专业目录是**分类数据**，不是文档集合：用户输入「临床」时想要的是
 * 「所有名字里带临床的专业」，不是「相关度最高的三条」。打分排序会让
 * 每次多输一个字就整片重排，视觉上像在抽搐；四路并集的语义简单到可以写进
 * placeholder，用户能预期自己会看到什么。
 *
 * 四条匹配路径（任一命中即保留）：
 *  1. [Major.code] 前缀 —— 输入 `081` 命中 `0812` / `0811`；输入 `0101`
 *     会同时命中一级学科 `0101` 和它的二级学科 `010101`..`010106`。
 *  2. 专业中文名（[Major.label]）包含 —— 输入「临床」命中临床医学、临床医学硕士。
 *  3. 门类中文名（[MajorCategory.label]）包含 —— 输入「医学」把整个医学门类都捞出来，
 *     不用先点门类 chip 再翻。
 *  4. **枚举名**（[Major.name]）包含 —— 这一条专门给英文缩写用。
 *     `MBA` / `MPA` / `MPACC` 的中文名是「工商管理硕士」这类，
 *     用户心里想的却是「搜 mba」，只认中文名的话这三条永远搜不到。
 *
 * 大小写一律按 [String.lowercase] 归一（Kotlin 的无参版本走 Locale.ROOT，
 * 不受设备语言影响 —— 在土耳其语环境下 `I`.lowercase() 会变成 `ı`，
 * 那会让 `MBA` 之外的英文缩写全部失灵）。
 */
object MajorSearch {

    /**
     * 按关键词过滤专业。
     *
     * 返回**保持传入顺序**（默认就是 [Major.entries] 的声明顺序），
     * 满足契约里「结果顺序与枚举一致」的要求：搜索结果乱序会让用户
     * 每输入一个字就整片重排。
     *
     * 关键词首尾空白会被裁掉：手机输入法很容易带出一个尾随空格，
     * 不裁的话「0812 」会因为匹配不到而显示「没有匹配的专业」——
     * 这是最容易被当成"搜索坏了"的一类假故障。
     *
     * 空关键词（含纯空白）返回 [majors] 本身，不是副本 ——
     * 调用方据此显示完整的门类分组，同时避免每次重组都分配一个新 List。
     *
     * @param keyword 用户输入。允许为空。
     * @param majors 候选集合。默认全部专业；单测可以传子集来锁定匹配语义。
     */
    fun search(keyword: String, majors: List<Major> = Major.entries): List<Major> {
        val raw = keyword.trim()
        if (raw.isEmpty()) return majors
        val lower = raw.lowercase()
        return majors.filter { matches(it, raw, lower) }
    }

    /** 命中判定。拆出来是为了让 [search] 的循环保持一行，读起来像规格书。 */
    private fun matches(major: Major, raw: String, lower: String): Boolean =
        major.code.startsWith(raw) ||
            major.label.lowercase().contains(lower) ||
            major.category.label.lowercase().contains(lower) ||
            major.name.lowercase().contains(lower)

    /**
     * 关键词是否「有内容」。
     *
     * UI 需要区分「没搜」和「搜了但没结果」：前者显示门类分组，后者显示空提示。
     * 两边都用同一个判断，免得一边 `isEmpty()` 一边 `isBlank()`，
     * 用户敲一个空格就掉进空结果分支。
     */
    fun isBlank(keyword: String): Boolean = keyword.isBlank()
}
