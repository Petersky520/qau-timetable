package cn.edu.qau.timetable.ui.kaoyan

import androidx.compose.runtime.Immutable
import cn.edu.qau.timetable.core.kaoyan.Ambition
import cn.edu.qau.timetable.core.kaoyan.Recommendation
import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import cn.edu.qau.timetable.core.kaoyan.SchoolScore

/**
 * 推荐卡片的展示模型。
 *
 * ## 为什么要在 UI 层再投影一份
 *
 * [Recommendation] / [SchoolScore] / [SchoolItem] 都在 `core/kaoyan/` 里，
 * 而那一层是**纯 Kotlin 零依赖**的（单测在纯 JVM 里跑，不能拖进 Android/Compose，
 * 见 `docs/v33-spec.md` §4）。不标 `@Immutable` 的话 Compose 会把它们当成不稳定类型：
 * 只要父级重组，视口里所有卡片一起重组 —— 60 张卡滚动时每帧都在白算。
 *
 * 两条路：
 *   - 往 core 塞 `androidx.compose.runtime.Immutable` —— 破坏零依赖契约。
 *   - 在 UI 层做一份**扁平、全 final、全基本类型**的投影，标上 `@Immutable`。
 *
 * 选第二条。映射只在 ViewModel 里做一次（`KaoyanViewModel.recommendCards`），
 * **不在 composable 里做** —— 在 composable 里做的话，映射本身又变成每次重组的工作量。
 *
 * ## 为什么字段里有"预格式化好的字符串"
 *
 * 卡片的每一行都是固定文案（`"$a · $b"`、`ifZero("-")`、`String.format("%.1f")`）。
 * 拼在 composable 里，每次重组都要重新分配一批 String；放在映射里拼一次，
 * 卡片就只剩 `Text(ui.metricsLine)`。
 *
 * ## 为什么**不**保留指向 core 对象的引用
 *
 * 曾经想把 `rec: Recommendation` 一起带进来当"溯源锚点"。放弃了：
 * 一个标了 `@Immutable` 的类里塞一个编译器认为不稳定的字段，是在骗编译器。
 * 卡片上的**动作**其实只需要一个 id（收藏要用它），所以这里只留 [id] 和 [tier]。
 * 要原始数据请回 ViewModel 的 `recommendations`，那才是唯一真源。
 */
@Immutable
data class KaoyanCardUi(
    /** 稳定主键：列表 `key` 与收藏比对都用它（院校代码-专业代码）。 */
    val id: String,
    /** 分档。列表分组要用它，不重新去 core 里查。 */
    val tier: Ambition,
    val schoolName: String,
    /** 层次徽标文本，空串表示不画（[TierBadge] 自己会 return）。 */
    val tierBadge: String,
    /** 「省 · 市」。 */
    val locationLine: String,
    /** 「专业名（专业代码）」。 */
    val majorLine: String,
    /** 复试线 / 招生 / 报录比合成的一行。 */
    val metricsLine: String,
    /** 0..100。 */
    val score: Int,
    /** 0f..1f，进度条填充比例 —— 映射时算好，composable 里不再做除法。 */
    val scoreFraction: Float,
    val reasons: List<String>,
    val warnings: List<String>,
    /** 「标签：xxx」，空串表示不画。 */
    val tagsLine: String,
    /** 备注原文，空串表示不画。 */
    val noteLine: String,
)

/**
 * 进度条比例。
 *
 * 单独抽出来是为了「夹一下再除 100」只写一次：引擎已经把 score 夹在 0..100，
 * 但展示层不该信任上游的这个不变量 —— 越界值会让 `fillMaxWidth(fraction)` 直接抛异常。
 */
private fun fractionOf(score: Int): Float = score.coerceIn(0, 100) / 100f

/** 0 表示「未收录」，直接印 0 会被当成真实数据（和原 `ifZero` 同一套语义）。 */
private fun Int.orDash(): String = if (this > 0) toString() else "-"

/**
 * [Recommendation] → [KaoyanCardUi]。
 *
 * 这里**只做投影**，不做任何业务判断：文案里的「未收录 / 报录比」等规则必须和
 * 原来 `RecommendCard` 里逐字一致，否则就是借着性能优化偷偷改产品行为。
 * 原有的判断（`applyRatio > 0`、`quota > 0`）一条不少地搬过来。
 */
fun Recommendation.toCardUi(): KaoyanCardUi {
    val s: SchoolItem = school
    val sc: SchoolScore = score
    return KaoyanCardUi(
        id = s.id,
        tier = tier,
        schoolName = s.name,
        tierBadge = s.tier,
        locationLine = listOf(s.province, s.city)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        majorLine = "${s.majorName}（${s.majorCode}）",
        metricsLine = buildString {
            append("复试线 ${s.scoreLast.orDash()} / ${s.scorePrev.orDash()}")
            append(" · 招生 ${s.quota.orDash()} / 报考 ${s.applicants.orDash()}")
            append(
                if (s.applyRatio > 0) {
                    " · 报录比 ${String.format("%.1f", s.applyRatio)}"
                } else {
                    " · 报录比未收录"
                }
            )
        },
        score = sc.score,
        scoreFraction = fractionOf(sc.score),
        // 复制成新 List：引擎里这两份是 mutableList 传上来的，留着同一个引用
        // 会让 Compose 的相等性判断依赖"调用方有没有偷偷改它"。
        reasons = sc.reasons.toList(),
        warnings = sc.warnings.toList(),
        tagsLine = if (s.tags.isNotBlank()) "标签：${s.tags}" else "",
        noteLine = s.note,
    )
}

/**
 * 结果区的一行：分组小标题，或一条院校卡片。
 *
 * 放在 UI 层（原来是 `KaoyanSchoolScreen` 里内联的 private sealed interface），
 * 因为 [Item] 现在装的是 [KaoyanCardUi] —— 这个类型不允许出现在 core 里。
 */
@Immutable
sealed interface RecommendRow {
    data class Header(val tier: Ambition, val count: Int) : RecommendRow

    data class Item(val card: KaoyanCardUi) : RecommendRow
}

/**
 * 按档分组：顺序固定 冲刺 → 稳妥 → 保底。
 *
 * 即使 `profile.ambition` 是「保底」，展示顺序也保持这个叙事逻辑，
 * 只在推荐引擎的排序里做「该档优先」。
 *
 * ## 为什么它是顶层函数而不是画在 composable 里
 *
 * 这原本是 `KaoyanSchoolScreen` 里内联的 `buildList { Ambition.entries.forEach { recs.filter } }`：
 * **每次重组**都跑一遍 O(3N) 过滤、分配三份临时 List 和一份结果 List。
 * 抽成顶层函数之后它可以被 `remember(recs)` 缓存 —— 同一份 recs 引用不再重算，
 * 也就能在单测里直接验证分组顺序（画在 composable 里只能靠看）。
 *
 * 不是 @Composable：它要能在 `remember { }` 的 lambda 里被调用。
 */
fun buildRecommendRows(cards: List<KaoyanCardUi>): List<RecommendRow> = buildList {
    Ambition.entries.forEach { tier ->
        val group = cards.filter { it.tier == tier }
        if (group.isNotEmpty()) {
            add(RecommendRow.Header(tier, group.size))
            group.forEach { add(RecommendRow.Item(it)) }
        }
    }
}
