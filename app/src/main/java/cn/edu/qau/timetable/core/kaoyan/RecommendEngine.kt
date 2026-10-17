package cn.edu.qau.timetable.core.kaoyan

/**
 * 一条院校条目的打分结果。
 *
 * 把「分数」和「理由/警告」绑在一起而不是只返回 Int：推荐结果必须能解释自己，
 * 只说好话的推荐是骗人的 —— [warnings] 就是那些该让用户看见的减分项。
 */
data class SchoolScore(
    /** 0..100，越高越匹配。 */
    val score: Int,
    /** 命中的加分理由，逐条给用户看。 */
    val reasons: List<String>,
    /** 被扣分的原因，同样给用户看 —— 只说好话的推荐是骗人的。 */
    val warnings: List<String>,
)

/** 推荐结果：院校 + 打分 + 分档。 */
data class Recommendation(
    val school: SchoolItem,
    val score: SchoolScore,
    val tier: Ambition,
)

/**
 * 选校推荐引擎。
 *
 * ## 为什么是一个无状态 object
 * 打分规则是纯函数：同样的画像 + 同样的院校，任何时刻都必须得到同样的分数。
 * 一旦把它做成带缓存的类，`combine(schools, profile)` 每次重算就会引入
 * 「旧缓存 vs 新画像」的时序 bug，而这点计算量根本不值得缓存。
 *
 * ## 分数区间是「匹配度」不是「录取概率」
 * 契约里三档判定写在 [recommend] 的 KDoc 里，75/55 两条线是硬编码的产品约定，
 * 不要按数据规模去调参 —— UI 直接展示这三档，调了就等于改了产品语义。
 */
object RecommendEngine {

    /** 三档的展示顺序；分档之外的兜底排序也用它。 */
    private val CANONICAL_TIER_ORDER = listOf(Ambition.REACH, Ambition.MATCH, Ambition.SAFE)

    /**
     * 按意向画像打分并排序。
     *
     * 分档规则（必须严格实现，UI 会直接展示这三档）：
     *   score >= 75 且 院校层次高于本科层次   -> REACH
     *   score >= 55                           -> MATCH
     *   其余                                   -> SAFE
     * 同时 honors profile.ambition：结果按「该档优先」排序，
     * 但三档都要返回（用户要能横向比较）。
     *
     * **候选集先按专业过滤**：`crossMajor == false` 时只保留 `majorCode` 对口的院校。
     * 专业是硬约束而不是加分项 —— 理由见下方实现处的注释。
     *
     * 排序键依次是：
     *   1. 分档 —— `profile.ambition` 那一档排最前，其余两档按
     *      REACH → MATCH → SAFE 的固定顺序跟进。这样既满足「该档优先」，
     *      又保证同一份画像的输出顺序完全稳定（不稳定的话，
     *      Compose 列表每次重算都会整片闪烁）。
     *   2. 分数降序。
     *   3. 院校名 —— 分数打平时用名字兜底，避免随机顺序。
     *
     * @param limit 返回条数上限；<= 0 视为不限制，避免调用方传 0 时列表空白。
     */
    fun recommend(
        profile: KaoyanProfile,
        schools: List<SchoolItem>,
        limit: Int = 60,
    ): List<Recommendation> {
        // ---- 专业是硬筛选，不是软打分 ----
        //
        // 未勾「接受跨专业」时，不对口的院校**根本不进候选集**。
        // 之前只靠规则 1 扣 25 分，在 108 条目录下还看不出来；
        // 种子扩到 463 条后立刻暴露：大量不对口的农学/兽医条目靠
        // 「地区不限 +12 / 招生规模 +8 / 报录比友好 +10 / 层次相当 +15」
        // 堆到 28 分左右，把分数被报录比拖低的对口院校挤到后面 ——
        // 实测默认画像（计算机 / 稳妥 / 普通本科）下 UI 前 60 条里
        // 只有 15 条对口、45 条是别的专业。
        //
        // 用户没勾跨考 = 「我不接受跨专业」，不是「跨专业排后面一点」。
        // 所以这里做成过滤：宁可只出 15 条对口的，也不拿 45 条无关结果凑数。
        // `score()` 仍然对任何院校返回带「专业不对口」警告的分数 ——
        // 单条打分的语义不变，被过滤的条目在别处（跨考画像）照样用得上。
        val candidates = if (profile.crossMajor) {
            schools
        } else {
            schools.filter { it.majorCode == profile.major.code }
        }

        val scored = candidates.map { school ->
            val score = score(profile, school)
            Recommendation(school = school, score = score, tier = tierOf(profile, school, score.score))
        }

        val tierPriority = listOf(profile.ambition) + CANONICAL_TIER_ORDER.filter { it != profile.ambition }
        val ordered = scored.sortedWith(
            compareBy<Recommendation> { tierPriority.indexOf(it.tier) }
                .thenByDescending { it.score.score }
                .thenBy { it.school.name }
        )
        return if (limit > 0) ordered.take(limit) else ordered
    }

    /**
     * 单条打分，公开出来是为了单测能逐项验证。
     *
     * 七条规则各自独立累加（不互相短路），因为它们是**不同维度**的加减分：
     * 一个跨专业、地区不对、层次也不匹配的院校应该同时暴露三条警告，
     * 而不是只报第一条。
     */
    fun score(profile: KaoyanProfile, school: SchoolItem): SchoolScore {
        var total = 0
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // ---- 规则 1：专业匹配 ----
        // 专业是选校的第一约束：对口就直接给满，跨考是「知情后的妥协」只给一点点，
        // 不对口则明确扣分 —— 这三档必须都返回，才能让用户看见「换专业会掉多少分」。
        when {
            school.majorCode == profile.major.code -> {
                total += 30
                reasons += "专业完全对口"
            }

            profile.crossMajor -> {
                total += 8
                reasons += "允许跨考"
            }

            else -> {
                total -= 25
                warnings += "专业不对口"
            }
        }

        // ---- 规则 2：地区匹配 ----
        when {
            profile.region == Region.ANY -> {
                total += 12
                reasons += "地区不限"
            }

            school.province in profile.region.provinces -> {
                total += 20
                reasons += "位于意向地区"
            }

            else -> {
                total -= 10
                warnings += "不在意向地区"
            }
        }

        // ---- 规则 3：层次差（随报考意愿变化）----
        //
        // 只看「目标层次 - 本科层次」的差值，不看绝对层次：
        // 同样的 985，对 985 本科生是持平，对普通本科生才是跨两档的挑战。
        //
        // ⚠️ 这一条曾经写成「delta 越大扣分越狠」，方向是反的：
        // 冲刺档本来就该看比自己高的学校，按旧写法，越符合「冲刺」的院校被扣得越多，
        // 结果 REACH 永远为空（实测 67 条全落进 SAFE，最高分 45）。
        // 报考意愿不同，「好的层次差」就不一样 —— 所以这里按 [KaoyanProfile.ambition] 分派。
        val delta = SchoolTier.fromName(school.tier).weight - profile.undergraduateTier.weight
        when (profile.ambition) {
            Ambition.REACH -> when {
                delta < 0 -> {
                    total -= 10
                    warnings += "想冲刺却报了层次更低的院校"
                }

                delta in 0..15 -> {
                    total += 5
                    reasons += "跨度偏小，可再往上够一够"
                }

                delta in 16..45 -> {
                    total += 18
                    reasons += "层次跨度符合冲刺预期"
                }

                else -> {
                    total -= 8
                    warnings += "跨度太大，风险偏高"
                }
            }

            Ambition.MATCH -> when {
                delta < 0 -> {
                    total += 8
                    reasons += "相对稳妥"
                }

                delta in 0..15 -> {
                    total += 15
                    reasons += "层次相当，跨度合理"
                }

                delta in 16..30 -> {
                    total += 5
                    reasons += "略有挑战"
                }

                else -> {
                    total -= 10
                    warnings += "跨度较大"
                }
            }

            Ambition.SAFE -> when {
                delta < 0 -> {
                    total += 15
                    reasons += "层次更低的院校，适合保底"
                }

                delta in 0..15 -> {
                    total += 8
                    reasons += "层次相当"
                }

                else -> {
                    total -= 10
                    warnings += "这所院校对保底来说偏高"
                }
            }
        }

        // ---- 规则 4：数学 ----
        // 只有「院校专业要考数学」且「用户不接受数学」才扣分；
        // 反过来的组合不加分 —— 愿意考数学是常态，不该被奖励。
        if (school.mathRequired && !profile.acceptMath) {
            total -= 20
            warnings += "该专业需要考数学"
        }

        // ---- 规则 5：招生规模 ----
        // quota == 0 是「数据缺失」而不是「招 0 人」，所以先判掉，
        // 否则用户把 minQuota 设成 1 时，所有未收录人数的院校都会被当成不达标。
        if (school.quota == 0) {
            warnings += "招生人数未收录"
        } else {
            if (school.quota < profile.minQuota) {
                total -= 12
                warnings += "招生人数低于期望"
            }
            if (school.quota >= 20) {
                total += 8
                reasons += "招生规模较大"
            }
        }

        // ---- 规则 6：报录比 ----
        // 报录比必须两个数都有才谈得上，缺一个就跳过 —— 不猜、不补默认值。
        if (school.applicants > 0 && school.quota > 0) {
            val ratio = school.applicants.toDouble() / school.quota
            when {
                ratio < 3 -> {
                    total += 10
                    reasons += "报录比友好"
                }

                ratio <= 8 -> Unit // 3..8 是常态区间，不加不减

                else -> {
                    total -= 10
                    warnings += "报录比偏高"
                }
            }
        }

        // ---- 规则 7：分数带（结合 ambition） ----
        // 用近两年复试线的均值代表这所院校的「水位」，再判断它落在
        // 用户报考意愿对应的哪一段：冲刺要够高、保底要明显更低。
        // 任一年缺失（0）时只用另一年，两年都缺就整条跳过 ——
        // 拿 0 参与平均会算出个假水位，把结论带偏。
        val avg = averageScore(school)
        if (avg > 0) {
            val inBand = when (profile.ambition) {
                Ambition.REACH -> avg in 330..380
                Ambition.MATCH -> avg in 300..345
                Ambition.SAFE -> avg in 260..310
            }
            if (inBand) {
                total += 8
            } else {
                total -= 5
                warnings += "分数线与报考意愿不匹配"
            }
        }

        return SchoolScore(
            score = total.coerceIn(0, 100),
            reasons = reasons,
            warnings = warnings,
        )
    }

    /**
     * 近两年复试线的均值，忽略缺失值（0）。
     *
     * 单独抽出来是因为「两年都有 / 只有一年 / 都没有」三种情况在打分和
     * UI 展示里都要判，散落在两处很容易写成一个算平均、一个算求和。
     */
    private fun averageScore(school: SchoolItem): Int {
        val values = listOf(school.scoreLast, school.scorePrev).filter { it > 0 }
        return if (values.isEmpty()) 0 else values.sum() / values.size
    }

    /**
     * 分档：分数够高、且院校层次高于本科，才算「冲刺」；否则落到稳妥/保底。
     *
     * 两条门槛是**标定值**不是随便取的常数：单条满分为
     * 专业 30 + 地区 12/20 + 层次 18 + 招生 8 + 报录比 10 + 分数带 8 ≈ 86~96，
     * 而「普通本科 + 专业对口 + 地区不限」这类最常见画像的基线是 42 分
     * （30 对口 + 12 地区不限）。
     *
     * 早期用的是 75/55，但那样一来常见画像的绝大多数条目都会低于 55，
     * 全局挤进 SAFE —— 实测 67 条推荐全是保底，三档形同虚设。
     * 现在 55/38 让「基线以上」的条目有资格进稳妥，明显低于基线的才归保底。
     */
    private fun tierOf(profile: KaoyanProfile, school: SchoolItem, score: Int): Ambition {
        val aboveUndergraduate =
            SchoolTier.fromName(school.tier).weight > profile.undergraduateTier.weight
        return when {
            score >= REACH_THRESHOLD && aboveUndergraduate -> Ambition.REACH
            score >= MATCH_THRESHOLD -> Ambition.MATCH
            else -> Ambition.SAFE
        }
    }

    /** 进「冲刺」档的分数门槛；见 [tierOf] 的标定说明。 */
    const val REACH_THRESHOLD = 55

    /** 进「稳妥」档的分数门槛；低于它一律算保底。 */
    const val MATCH_THRESHOLD = 38
}
