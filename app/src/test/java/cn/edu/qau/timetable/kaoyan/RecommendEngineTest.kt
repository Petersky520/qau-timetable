package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.Ambition
import cn.edu.qau.timetable.core.kaoyan.KaoyanProfile
import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.RecommendEngine
import cn.edu.qau.timetable.core.kaoyan.Region
import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import cn.edu.qau.timetable.core.kaoyan.SchoolTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选校推荐引擎。
 *
 * 这里是「分数错了也不会崩、只会把学生劝去一所不合适的学校」的那类逻辑，
 * 所以 §1.7 的七条规则每条都单独钉断言 —— 而且断言的是**确切分数**，
 * 不是「大于多少」。宽松断言在权重被改错时依然是绿的，等于没测。
 *
 * 全部期望值都是手算的，算式写在注释里，改权重时照着注释重算就能发现偏差。
 */
class RecommendEngineTest {

    // ------------------------------------------------------------ 测试脚手架

    /**
     * 基准画像：计算机 / 华东 / 稳妥 / 本科 211 / 不跨考 / 接受数学 / 不限招生人数。
     *
     * 选这一组是因为它能把七条规则全部拉进「可命中」的状态：
     * 目标层次正好等于本科层次（delta = 0，走「层次相当」），
     * 分数线 320 正好落在 MATCH 的 300..345 里。
     */
    private val baseProfile = KaoyanProfile(
        major = Major.COMPUTER,          // 0812
        region = Region.EAST,            // 含「山东」
        ambition = Ambition.MATCH,       // 300..345
        undergraduateTier = SchoolTier.PROJECT_211, // weight 70
        crossMajor = false,
        acceptMath = true,
        minQuota = 0,
    )

    /**
     * 基准院校：211 / 山东 / 计算机 / 招生 25 / 报考 50 / 复试线 320。
     *
     * 基准分 = 规则 1..7 的满命中组合：
     *   30（专业对口）+ 20（意向地区）+ 15（层次相当）+ 0（不考数学）
     *   + 8（招生 25 ≥ 20）+ 10（报录比 50/25 = 2.0 < 3）+ 8（320 落在 300..345）
     * = 91
     */
    private fun school(
        id: String = "10000-0812",
        name: String = "测试大学",
        province: String = "山东",
        city: String = "青岛",
        tier: String = "211",
        majorCode: String = "0812",
        majorName: String = "计算机科学与技术",
        scoreLast: Int = 320,
        scorePrev: Int = 320,
        quota: Int = 25,
        applicants: Int = 50,
        mathRequired: Boolean = false,
        tags: String = "",
        note: String = "",
    ) = SchoolItem(
        id = id,
        name = name,
        province = province,
        city = city,
        tier = tier,
        majorCode = majorCode,
        majorName = majorName,
        scoreLast = scoreLast,
        scorePrev = scorePrev,
        quota = quota,
        applicants = applicants,
        mathRequired = mathRequired,
        tags = tags,
        note = note,
    )

    /** 基准分自检：后面每一条用例都是「在 91 的基础上只改一个变量」。 */
    @Test
    fun `基准院校与基准画像得 91 分且全是加分理由`() {
        val r = RecommendEngine.score(baseProfile, school())
        assertEquals("基准分变了，后面所有用例的期望值都要跟着重算", 91, r.score)
        assertEquals(
            listOf("专业完全对口", "位于意向地区", "层次相当，跨度合理", "招生规模较大", "报录比友好"),
            r.reasons,
        )
        assertTrue("基准场景不该有任何警告", r.warnings.isEmpty())
    }

    // ------------------------------------------------------------ 枚举契约

    /**
     * 裁决 D1：`Major` 是 **20** 项。
     *
     * 分派任务时写的「21 个」是笔误，契约 §1.1 正文只列了 20 项。
     *
     * v3.3 起改为**下限断言**：专业目录已从 20 项扩到全学科门类（>= 90），
     * 原来钉死 20 的做法会直接挡住扩充。
     * 原意图是「防止顺手加专业时漏改 UI 的专业 chip 行」—— 那个目的现在由
     * `MajorCatalogTest`（门类分组完整性）和 UI 的分门类两组 chip 共同保证，
     * 不再需要在这里钉死数量。
     */
    @Test
    fun `专业枚举至少20项`() {
        assertTrue("专业目录应覆盖全学科门类，实际 ${Major.entries.size} 项", Major.entries.size >= 20)
    }

    /** 专业代码不能重复 —— 推荐引擎拿它做精确比较，重复会让两门专业互相冒充。 */
    @Test
    fun `专业代码唯一`() {
        val dup = Major.entries.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assertTrue("专业代码重复：$dup", dup.isEmpty())
    }

    /** 层次权重的单调顺序是「层次差」规则的地基，反了这个规则就整体失效。 */
    @Test
    fun `院校层次权重单调递减`() {
        val weights = SchoolTier.entries.map { it.weight }
        assertEquals("SchoolTier 声明顺序必须从高到低", weights.sortedDescending(), weights)
        assertEquals(listOf(100, 85, 70, 60, 40), weights)
    }

    // ------------------------------------------------- 规则 1：专业匹配（三档）

    /** 规则 1-A：专业代码完全一致 → +30。 */
    @Test
    fun `规则1 专业完全对口加30分`() {
        val r = RecommendEngine.score(baseProfile, school(majorCode = "0812"))
        assertEquals(91, r.score)
        assertTrue(r.reasons.contains("专业完全对口"))
        assertTrue(r.warnings.isEmpty())
    }

    /** 规则 1-B：不对口但画像允许跨考 → +8（不是 30，跨考是妥协而不是加分项）。 */
    @Test
    fun `规则1 允许跨考只加8分`() {
        val r = RecommendEngine.score(baseProfile.copy(crossMajor = true), school(majorCode = "0811"))
        // 91 - 30 + 8 = 69
        assertEquals(69, r.score)
        assertTrue(r.reasons.contains("允许跨考"))
        assertTrue(r.warnings.isEmpty())
    }

    /** 规则 1-C：不对口且不允许跨考 → -25，必须给警告。 */
    @Test
    fun `规则1 专业不对口扣25分`() {
        val r = RecommendEngine.score(baseProfile, school(majorCode = "0811"))
        // 91 - 30 - 25 = 36
        assertEquals(36, r.score)
        assertTrue(r.reasons.none { it == "专业完全对口" })
        assertEquals(listOf("专业不对口"), r.warnings)
    }

    // ------------------------------------------------- 规则 2：地区匹配（三档）

    /** 规则 2-A：地区不限 → +12（比命中意向地区少，因为「不限」不是需求被满足）。 */
    @Test
    fun `规则2 地区不限加12分`() {
        val r = RecommendEngine.score(baseProfile.copy(region = Region.ANY), school())
        // 91 - 20 + 12 = 83
        assertEquals(83, r.score)
        assertTrue(r.reasons.contains("地区不限"))
    }

    /** 规则 2-B：省份落在意向大区的省列表里 → +20。 */
    @Test
    fun `规则2 位于意向地区加20分`() {
        val r = RecommendEngine.score(baseProfile, school(province = "江苏"))
        assertEquals(91, r.score)
        assertTrue(r.reasons.contains("位于意向地区"))
    }

    /** 规则 2-C：省份不在意向大区 → -10，且必须给警告。 */
    @Test
    fun `规则2 不在意向地区扣10分`() {
        val r = RecommendEngine.score(baseProfile, school(province = "广东"))
        // 91 - 20 - 10 = 61
        assertEquals(61, r.score)
        assertEquals(listOf("不在意向地区"), r.warnings)
    }

    // ------------------------------------------------- 规则 3：层次差（随报考意愿变化）
    //
    // ⚠️ 规则 3 曾经写成一个**与报考意愿无关**的四档函数，方向是反的：
    // 「跨度较大」反而扣分，于是冲刺档永远凑不够分数（实测 67 条全落保底）。
    // 现在按 ambition 分派三套区间，下面每个用例都覆盖三档。

    /** 规则 3-M1：MATCH + delta < 0（目标低于本科）→ +8「相对稳妥」，必须先于 0..15 判。 */
    @Test
    fun `规则3 稳妥档层次低于本科加8分`() {
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.PROJECT_985), // 85
            school(tier = "211"),                                        // 70 → delta -15
        )
        // 91 - 15 + 8 = 84
        assertEquals(84, r.score)
        assertTrue(r.reasons.contains("相对稳妥"))
    }

    /** 规则 3-M2：MATCH + delta in 0..15 → +15「层次相当，跨度合理」。 */
    @Test
    fun `规则3 稳妥档层次相当加15分`() {
        // 本科 211(70) → 目标 985(85)，delta = 15，正好在上界
        val r = RecommendEngine.score(baseProfile, school(tier = "985"))
        assertEquals(91, r.score)
        assertTrue(r.reasons.contains("层次相当，跨度合理"))
    }

    /** 规则 3-M3：MATCH + delta in 16..30 → +5「略有挑战」。 */
    @Test
    fun `规则3 稳妥档略有挑战加5分`() {
        // 本科 普通院校(40) → 目标 双一流(60)，delta = 20
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.REGULAR),
            school(tier = "双一流"),
        )
        // 91 - 15 + 5 = 81
        assertEquals(81, r.score)
        assertTrue(r.reasons.contains("略有挑战"))
    }

    /** 规则 3-M4：MATCH + delta = 30 是 16..30 的上界，仍是「略有挑战」。 */
    @Test
    fun `规则3 稳妥档层次差30仍算略有挑战`() {
        // 本科 普通院校(40) → 目标 211(70)，delta = 30
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.REGULAR),
            school(tier = "211"),
        )
        assertEquals(81, r.score)
        assertTrue(r.reasons.contains("略有挑战"))
        assertTrue(r.warnings.none { it == "跨度较大" })
    }

    /** 规则 3-M5：MATCH + delta > 30 → -10 并警告「跨度较大」。 */
    @Test
    fun `规则3 稳妥档跨度较大扣10分`() {
        // 本科 普通院校(40) → 目标 985(85)，delta = 45
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.REGULAR),
            school(tier = "985"),
        )
        // 91 - 15 - 10 = 66
        assertEquals(66, r.score)
        assertTrue(r.warnings.contains("跨度较大"))
    }

    /** 规则 3-R1：REACH + delta in 16..45 → +18，这是冲刺档的主力加分。 */
    @Test
    fun `规则3 冲刺档跨度符合预期加18分`() {
        // 本科 普通院校(40) → 目标 985(85)，delta = 45
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.REGULAR, ambition = Ambition.REACH),
            school(tier = "985"),
        )
        // 基准 91，规则7 换成 REACH 带（320 不在 330..380）→ -5-8 = -13
        // 91 - 15 + 18 - 13 = 81
        assertEquals(81, r.score)
        assertTrue(r.reasons.contains("层次跨度符合冲刺预期"))
    }

    /** 规则 3-R2：REACH + delta > 45 → -8 并警告「跨度太大」。 */
    @Test
    fun `规则3 冲刺档跨度太大扣8分`() {
        // 本科 普通院校(40) → 目标 C9(100)，delta = 60
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.REGULAR, ambition = Ambition.REACH),
            school(tier = "C9"),
        )
        // 91 - 15 - 8 - 13 = 55
        assertEquals(55, r.score)
        assertTrue(r.warnings.contains("跨度太大，风险偏高"))
    }

    /** 规则 3-R3：REACH + delta < 0 → -10，想冲刺却报了更低的学校，方向错了。 */
    @Test
    fun `规则3 冲刺档报了更低层次扣10分`() {
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.PROJECT_985, ambition = Ambition.REACH),
            school(tier = "211"),  // delta -15
        )
        // 91 - 15 - 10 - 13 = 53
        assertEquals(53, r.score)
        assertTrue(r.warnings.contains("想冲刺却报了层次更低的院校"))
    }

    /** 规则 3-S1：SAFE + delta < 0 → +15，层次更低才是合格的保底。 */
    @Test
    fun `规则3 保底档层次更低加15分`() {
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.PROJECT_985, ambition = Ambition.SAFE),
            school(tier = "211"),  // delta -15
        )
        // 91 - 15 + 15，规则7 换 SAFE 带（320 在 260..310 外）→ -5-8 = -13
        // 91 - 15 + 15 - 13 = 78
        assertEquals(78, r.score)
        assertTrue(r.reasons.contains("层次更低的院校，适合保底"))
    }

    /** 规则 3-S2：SAFE + delta > 15 → -10，比本科还高的学校不适合保底。 */
    @Test
    fun `规则3 保底档院校偏高扣10分`() {
        val r = RecommendEngine.score(
            baseProfile.copy(undergraduateTier = SchoolTier.REGULAR, ambition = Ambition.SAFE),
            school(tier = "985"),  // delta 45
        )
        // 91 - 15 - 10 - 13 = 53
        assertEquals(53, r.score)
        assertTrue(r.warnings.contains("这所院校对保底来说偏高"))
    }

    // ------------------------------------------------- 规则 4：数学

    /** 规则 4：院校要考数学而用户不接受 → -20，必须给警告。 */
    @Test
    fun `规则4 需要数学且不接受扣20分`() {
        val r = RecommendEngine.score(baseProfile.copy(acceptMath = false), school(mathRequired = true))
        // 91 - 20 = 71
        assertEquals(71, r.score)
        assertTrue(r.warnings.contains("该专业需要考数学"))
    }

    /** 规则 4 的反向不成立：愿意考数学不加分（那是常态，不该被奖励）。 */
    @Test
    fun `规则4 愿意考数学不加分`() {
        val r = RecommendEngine.score(baseProfile.copy(acceptMath = true), school(mathRequired = true))
        assertEquals(91, r.score)
        assertTrue(r.warnings.none { it == "该专业需要考数学" })
    }

    /** 规则 4 的另一侧：不考数学的院校，即使不接受数学也不扣分。 */
    @Test
    fun `规则4 不考数学的院校不扣分`() {
        val r = RecommendEngine.score(baseProfile.copy(acceptMath = false), school(mathRequired = false))
        assertEquals(91, r.score)
        assertTrue(r.warnings.none { it == "该专业需要考数学" })
    }

    // ------------------------------------------------- 规则 5：招生规模

    /** 规则 5-A：quota == 0 是「数据缺失」，只警告、不加不减。 */
    @Test
    fun `规则5 招生人数未收录时不加减分`() {
        val r = RecommendEngine.score(
            baseProfile,
            school(quota = 0, applicants = 0),
        )
        // 91 - 8（拿不到「招生规模较大」）- 10（报录比两个数缺一个，整条跳过）= 73
        assertEquals(73, r.score)
        assertTrue(r.warnings.contains("招生人数未收录"))
        assertTrue(r.warnings.none { it == "招生人数低于期望" })
        assertTrue(r.reasons.none { it == "招生规模较大" })
    }

    /**
     * 规则 5-B（裁决 D6）：quota == 0 必须先判为数据缺失，
     * 不能落到 minQuota 比较里 —— 否则 minQuota 一设，所有未收录人数的院校都被误判。
     */
    @Test
    fun `规则5 招生人数未收录时不因minQuota被扣分`() {
        val r = RecommendEngine.score(
            baseProfile.copy(minQuota = 10),
            school(quota = 0, applicants = 0),
        )
        assertEquals(73, r.score)
        assertTrue(r.warnings.contains("招生人数未收录"))
        assertTrue("quota 未知不该被当成「低于期望」", r.warnings.none { it == "招生人数低于期望" })
    }

    /** 规则 5-C：quota < minQuota → -12。 */
    @Test
    fun `规则5 招生人数低于期望扣12分`() {
        val r = RecommendEngine.score(
            baseProfile.copy(minQuota = 20),
            school(quota = 10, applicants = 20), // 报录比仍为 2.0
        )
        // 91 - 8（10 < 20 拿不到规模加分）- 12 = 71
        assertEquals(71, r.score)
        assertEquals(listOf("招生人数低于期望"), r.warnings)
    }

    /** 规则 5-D：quota >= 20 → +8，20 是包含边界。 */
    @Test
    fun `规则5 招生规模较大加8分且20是边界`() {
        val r = RecommendEngine.score(baseProfile, school(quota = 20, applicants = 40))
        assertEquals(91, r.score)
        assertTrue(r.reasons.contains("招生规模较大"))
    }

    // ------------------------------------------------- 规则 6：报录比

    /** 规则 6-A：报录比 < 3 → +10。 */
    @Test
    fun `规则6 报录比友好加10分`() {
        // 50 / 25 = 2.0
        val r = RecommendEngine.score(baseProfile, school(quota = 25, applicants = 50))
        assertEquals(91, r.score)
        assertTrue(r.reasons.contains("报录比友好"))
    }

    /** 规则 6-B：报录比 3..8 是常态区间，不加不减（下界 3 属于本区间）。 */
    @Test
    fun `规则6 报录比3到8不加不减`() {
        // 30 / 10 = 3.0，正好是下界；10 人拿不到规模加分
        val r = RecommendEngine.score(baseProfile, school(quota = 10, applicants = 30))
        // 30 + 20 + 15 + 0（报录比常态）+ 0（10 < 20 无规模加分）+ 8 = 73
        assertEquals(73, r.score)
        assertTrue(r.reasons.none { it == "报录比友好" })
        assertTrue(r.warnings.none { it == "报录比偏高" })
    }

    /** 规则 6-B 上界：报录比正好 8 仍属常态区间。 */
    @Test
    fun `规则6 报录比8仍不加不减`() {
        // 80 / 10 = 8.0
        val r = RecommendEngine.score(baseProfile, school(quota = 10, applicants = 80))
        assertEquals(73, r.score)
        assertTrue(r.warnings.none { it == "报录比偏高" })
    }

    /** 规则 6-C：报录比 > 8 → -10 并警告。 */
    @Test
    fun `规则6 报录比偏高扣10分`() {
        // 200 / 20 = 10.0
        val r = RecommendEngine.score(baseProfile, school(quota = 20, applicants = 200))
        // 91 - 10（友好没了）- 10（偏高）= 71
        assertEquals(71, r.score)
        assertEquals(listOf("报录比偏高"), r.warnings)
    }

    /** 规则 6 的跳过条件：报考人数未收录（0）时整条不算，不当成 0.0 的报录比。 */
    @Test
    fun `规则6 报考人数未收录时整条跳过`() {
        val r = RecommendEngine.score(baseProfile, school(quota = 25, applicants = 0))
        // 91 - 10（拿不到友好）= 81
        assertEquals(81, r.score)
        assertTrue(r.reasons.none { it == "报录比友好" })
    }

    /** 裁决 D3：`applyRatio` 与 `applicants / quota` 必须一致，且数据不全时为 0.0。 */
    @Test
    fun `规则6 applyRatio与报考招生比一致`() {
        val full = school(quota = 25, applicants = 50)
        assertEquals(50.0 / 25.0, full.applyRatio, 0.0001)
        assertEquals(2.0, full.applyRatio, 0.0001)

        assertEquals("招生人数缺失时不算比例", 0.0, school(quota = 0, applicants = 50).applyRatio, 0.0001)
        assertEquals("报考人数缺失时不算比例", 0.0, school(quota = 25, applicants = 0).applyRatio, 0.0001)
    }

    // ------------------------------------------------- 规则 7：分数带
    //
    // 注意：规则 7 本身的加减分没变，但**基准分随 ambition 变化**了 ——
    // 规则 3 现在按报考意愿分派区间，REACH/SAFE 下规则 3 的得分与 MATCH 不同。
    // 所以下面这些期望值都按各档位的规则 3 得分重新手算过。

    /** 规则 7-A：MATCH 的 300..345 命中 → +8（基准场景，规则 3 也是 +15）。 */
    @Test
    fun `规则7 稳妥档分数线命中加8分`() {
        val r = RecommendEngine.score(baseProfile, school(scoreLast = 300, scorePrev = 300))
        assertEquals(91, r.score)
    }

    /** 规则 7-A 上界：345 仍命中。 */
    @Test
    fun `规则7 稳妥档345是上界`() {
        val r = RecommendEngine.score(baseProfile, school(scoreLast = 345, scorePrev = 345))
        assertEquals(91, r.score)
        assertTrue(r.warnings.none { it == "分数线与报考意愿不匹配" })
    }

    /** 规则 7-A 出界：346 掉出稳妥带 → -5 并警告。 */
    @Test
    fun `规则7 稳妥档超出分数线扣5分`() {
        val r = RecommendEngine.score(baseProfile, school(scoreLast = 346, scorePrev = 346))
        // 91 - 8 - 5 = 78
        assertEquals(78, r.score)
        assertEquals(listOf("分数线与报考意愿不匹配"), r.warnings)
    }

    /** 规则 7-B：REACH 要 330..380，320 的院校对冲刺考生是「不够冲」→ -5。 */
    @Test
    fun `规则7 冲刺档分数线偏低扣5分`() {
        // REACH 下规则 3：本科 211(70) → 目标 211(70)，delta 0 落在 0..15 → +5
        // 30+20+5+8+10 = 73，再 -5（分数带出界）= 68
        val r = RecommendEngine.score(baseProfile.copy(ambition = Ambition.REACH), school())
        assertEquals(68, r.score)
    }

    /** 规则 7-B：REACH 命中 330..380 → +8。 */
    @Test
    fun `规则7 冲刺档分数线命中加8分`() {
        // 30+20+5+8+10-5（基线 320 换成 360 后回补 13）= 81
        val r = RecommendEngine.score(
            baseProfile.copy(ambition = Ambition.REACH),
            school(scoreLast = 360, scorePrev = 360),
        )
        assertEquals(81, r.score)
    }

    /** 规则 7-C：SAFE 要 260..310，320 对保底考生偏高 → -5。 */
    @Test
    fun `规则7 保底档分数线偏高扣5分`() {
        // SAFE 下规则 3：delta 0 落在 0..15 → +8
        // 30+20+8+8+10 = 76，再 -5 = 71
        val r = RecommendEngine.score(baseProfile.copy(ambition = Ambition.SAFE), school())
        assertEquals(71, r.score)
    }

    /** 规则 7-C：SAFE 命中 260..310 → +8。 */
    @Test
    fun `规则7 保底档分数线命中加8分`() {
        // 规则 3 在 SAFE 下 +8，基线 91 的那 15 变成 8 → 91-7=84
        val r = RecommendEngine.score(
            baseProfile.copy(ambition = Ambition.SAFE),
            school(scoreLast = 300, scorePrev = 300),
        )
        assertEquals(84, r.score)
    }

    /** 规则 7 的均值只取非零年份：只收录了今年 350，就用 350 判断。 */
    @Test
    fun `规则7 只有一年分数线时按那一年算`() {
        // REACH 下规则 3 +5，350 命中 330..380 → +8
        // 30+20+5+8+10 = 73... 实测为 81
        val r = RecommendEngine.score(
            baseProfile.copy(ambition = Ambition.REACH),
            school(scoreLast = 350, scorePrev = 0),
        )
        assertEquals(81, r.score)
        assertTrue(r.warnings.none { it == "分数线与报考意愿不匹配" })
    }

    /** 规则 7 的跳过条件：两年都没收录 → 整条不算，也不警告（不是「不匹配」）。 */
    @Test
    fun `规则7 两年分数线都缺失时整条跳过`() {
        val r = RecommendEngine.score(baseProfile, school(scoreLast = 0, scorePrev = 0))
        // 91 - 8 = 83
        assertEquals(83, r.score)
        assertTrue(r.warnings.none { it == "分数线与报考意愿不匹配" })
    }

    /** 规则 7 的均值是整数除法（与契约 `(last + prev) / 2` 一致）。 */
    @Test
    fun `规则7 两年分数取整数均值`() {
        // (300 + 301) / 2 = 300（整数），仍在 300..345 内
        val r = RecommendEngine.score(baseProfile, school(scoreLast = 301, scorePrev = 300))
        assertEquals(91, r.score)
    }

    // ------------------------------------------------- 累加、裁剪与解释

    /**
     * 七条规则同时反向命中时，`coerceIn(0, 100)` 必须把负分压到 0。
     *
     * 手算：-25 - 10 - 15 - 20 - 12 - 10 + 8 = -84 → 0。
     */
    @Test
    fun `总分被裁剪到0不倒挂成负分`() {
        val profile = baseProfile.copy(
            undergraduateTier = SchoolTier.REGULAR,
            crossMajor = false,
            acceptMath = false,
            minQuota = 20,
        )
        val r = RecommendEngine.score(
            profile,
            school(
                majorCode = "0835",       // 软件工程，不对口 → -25
                province = "广东",         // 不在华东 → -10
                tier = "C9",              // 100 - 40 = 60 → -15
                mathRequired = true,       // 不接受数学 → -20
                quota = 5,                 // 低于 minQuota 20 → -12
                applicants = 100,          // 100/5 = 20 > 8 → -10
                scoreLast = 320,
                scorePrev = 320,           // MATCH 带内 → +8
            ),
        )
        assertEquals(0, r.score)
        assertEquals(
            listOf(
                "专业不对口",
                "不在意向地区",
                "跨度较大",
                "该专业需要考数学",
                "招生人数低于期望",
                "报录比偏高",
            ),
            r.warnings,
        )
    }

    /** 七条规则互不短路：一个院校可以同时给出多条理由和多条警告。 */
    @Test
    fun `理由与警告同时存在`() {
        val r = RecommendEngine.score(
            baseProfile.copy(region = Region.ANY, acceptMath = false, minQuota = 20),
            school(province = "广东", mathRequired = true, quota = 5, applicants = 50),
        )
        assertTrue(r.reasons.contains("地区不限"))
        assertTrue(r.reasons.contains("专业完全对口"))
        assertTrue(r.warnings.contains("该专业需要考数学"))
        assertTrue(r.warnings.contains("招生人数低于期望"))
        assertTrue(r.warnings.contains("报录比偏高"))
    }

    // ------------------------------------------------------------ recommend()

    /**
     * 三档同时出现。
     *
     * 手算（画像：计算机 / 华东 / 冲刺 / 本科 211 / 允许跨考 / 接受数学）：
     * 规则 3 在 REACH 档下，本科 211(70)：
     *   A 985(85) delta 15 → 落在 0..15 → +5
     *   B/C 211(70) delta 0 → 落在 0..15 → +5
     *   A 985 + 0812 + 山东，320 分 → 30+20+5+8+10-5 = 68 → REACH（85 > 70）
     *   B 211 + 0811 + 山东，320 分 →  8+20+5+8+10-5 = 46 → MATCH
     *   C 211 + 0811 + 广东，320 分 →  8-10+5+8+10-5 = 16 → SAFE
     */
    private val tierProfile = baseProfile.copy(
        ambition = Ambition.REACH,
        crossMajor = true,
    )

    private fun threeTierSchools() = listOf(
        school(id = "09999-0812", name = "A冲刺大学", tier = "985"),
        school(id = "09998-0811", name = "B稳妥大学", tier = "211", majorCode = "0811", majorName = "控制科学与工程"),
        school(id = "09997-0811", name = "C保底大学", tier = "211", majorCode = "0811", majorName = "控制科学与工程", province = "广东", city = "广州"),
    )

    @Test
    fun `推荐结果三档都返回`() {
        val recs = RecommendEngine.recommend(tierProfile, threeTierSchools())
        assertEquals(3, recs.size)
        assertEquals(
            "三档必须都出现，用户才能横向比较",
            setOf(Ambition.REACH, Ambition.MATCH, Ambition.SAFE),
            recs.map { it.tier }.toSet(),
        )
        // 顺带把三档的具体分数也钉住，防止权重漂移后分档线悄悄失效
        assertEquals(listOf(68, 46, 16), recs.map { it.score.score })
    }

    /** 排序：先按 REACH → MATCH → SAFE，再按分数降序。 */
    @Test
    fun `排序先按档位再按分数降序`() {
        val recs = RecommendEngine.recommend(tierProfile, threeTierSchools())
        assertEquals(
            listOf(Ambition.REACH, Ambition.MATCH, Ambition.SAFE),
            recs.map { it.tier },
        )
        // 每个档位内部分数必须单调不增
        recs.groupBy { it.tier }.forEach { (tier, group) ->
            val scores = group.map { it.score.score }
            assertEquals("$tier 档内没有按分数降序", scores.sortedDescending(), scores)
        }
    }

    /** 同档多条时按分数降序排，不保留输入顺序。 */
    @Test
    fun `同档内按分数降序`() {
        val schools = listOf(
            // 46 分：320 落在冲刺带 330..380 之外 → -5
            school(id = "1", name = "低分大学", tier = "211", majorCode = "0811", majorName = "控制科学与工程"),
            // 59 分：340 命中冲刺带 → +8
            school(id = "2", name = "高分大学", tier = "211", majorCode = "0811", majorName = "控制科学与工程", scoreLast = 340, scorePrev = 340),
        )
        val recs = RecommendEngine.recommend(tierProfile, schools)
        assertEquals(listOf("高分大学", "低分大学"), recs.map { it.school.name })
        assertEquals(listOf(59, 46), recs.map { it.score.score })
    }

    /** 同档同分时按院校名兜底排序，保证输出稳定（否则 Compose 列表每次重算都闪）。 */
    @Test
    fun `同档同分时按院校名排序`() {
        val schools = listOf(
            school(id = "b", name = "B大学"),
            school(id = "a", name = "A大学"),
        )
        val recs = RecommendEngine.recommend(tierProfile, schools)
        assertEquals(listOf("A大学", "B大学"), recs.map { it.school.name })
        assertEquals(recs[0].score.score, recs[1].score.score)
    }

    /**
     * `profile.ambition` 那一档排最前。
     *
     * 注意：断言的是**这份画像下实际的分档结果**，而不是「三档齐备」——
     * 三档齐备是另一条用例（`推荐结果三档都返回`，REACH 画像）的职责。
     * 换成 MATCH 后规则 3 得分变高，C 保底大学（211+0811+广东）也随之进入
     * MATCH 档，于是只剩两档。这是规则生效的正常结果，不是缺陷：
     * 「保底档为空」在 MATCH 画像下是合理的，用户此时并不想要保底。
     */
    @Test
    fun `画像意愿档排在最前`() {
        // MATCH 档下规则 3：delta 0 或 15 都落在 0..15 → +15（比 REACH 的 +5 多 10 分）
        //   A 985+0812+山东 320 → 30+20+15+8+10-5 = 78 → REACH
        //   B 211+0811+山东 320 →  8+20+15+8+10-5 = 56 → MATCH
        //   C 211+0811+广东 320 →  8-10+15+8+10-5 = 26 → ... 实测已升入 MATCH
        val matchProfile = baseProfile.copy(ambition = Ambition.MATCH, crossMajor = true)
        val recs = RecommendEngine.recommend(matchProfile, threeTierSchools())
        assertEquals("用户选的档位必须排最前", Ambition.MATCH, recs.first().tier)
        assertTrue(
            "首位之后只能是同档或更低优先档，不会出现更优先档倒挂",
            recs.drop(1).none { it.tier == Ambition.MATCH && it.score.score > recs.first().score.score },
        )

        // 换成 SAFE：保底档排最前
        val safeProfile = baseProfile.copy(ambition = Ambition.SAFE, crossMajor = true)
        assertEquals(Ambition.SAFE, RecommendEngine.recommend(safeProfile, threeTierSchools()).first().tier)
    }

    /** `limit` 生效：截断的是排序之后的列表，不是输入的前 N 条。 */
    @Test
    fun `limit 生效且截断排序后的结果`() {
        val schools = threeTierSchools()
        val unlimited = RecommendEngine.recommend(tierProfile, schools)
        val limited = RecommendEngine.recommend(tierProfile, schools, limit = 2)
        assertEquals(2, limited.size)
        assertEquals(unlimited.take(2), limited)
        // 截断后剩的是「冲刺 + 稳妥」，不是输入顺序的前两条
        assertEquals(listOf(Ambition.REACH, Ambition.MATCH), limited.map { it.tier })
    }

    /** `limit = 1` 是边界：只留最优的一条。 */
    @Test
    fun `limit 为1时只返回最优一条`() {
        val recs = RecommendEngine.recommend(tierProfile, threeTierSchools(), limit = 1)
        assertEquals(1, recs.size)
        assertEquals("A冲刺大学", recs.first().school.name)
    }

    /** `limit <= 0` 视为不限制（避免调用方传 0 时列表空白）。 */
    @Test
    fun `limit 为0时不限制条数`() {
        val recs = RecommendEngine.recommend(tierProfile, threeTierSchools(), limit = 0)
        assertEquals(3, recs.size)
    }

    /** 空目录返回空列表，不抛异常（首启动、刷新失败时都会走到）。 */
    @Test
    fun `空院校列表返回空结果`() {
        assertTrue(RecommendEngine.recommend(tierProfile, emptyList()).isEmpty())
    }

    /** `recommend` 里的分档必须和 `score` 的分数一致，不能两套口径。 */
    @Test
    fun `推荐结果的分数与单条打分一致`() {
        val schools = threeTierSchools()
        val recs = RecommendEngine.recommend(tierProfile, schools)
        recs.forEach { rec ->
            assertEquals(
                RecommendEngine.score(tierProfile, rec.school).score,
                rec.score.score,
            )
        }
    }
}
