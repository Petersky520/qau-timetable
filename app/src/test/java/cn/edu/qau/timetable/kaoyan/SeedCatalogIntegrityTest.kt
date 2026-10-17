package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.KaoyanProfile
import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.RecommendEngine
import cn.edu.qau.timetable.core.kaoyan.SchoolTier
import cn.edu.qau.timetable.data.kaoyan.parseCatalogJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 种子目录 `assets/kaoyan/schools.json` 的**数据完整性**测试。
 *
 * 和 `SeedIntegrationProbeTest` 的区别：那条探针只证明「文件能解析、能跑推荐」，
 * 用的是 `>= 40` 这种下限。本套件测的是**扩到 240 条之后的数据质量**，
 * 核心是那条防孤儿数据的断言：
 *
 * > 每一条 `majorCode` 都必须能在 `Major` 枚举里找到。
 *
 * 孤儿 code 是这个模块最阴的坑：解析不报错、推荐不报错、编译也不报错，
 * 表现只是「目录里有 240 条，选校页只出 180 条」——
 * 那 60 条永远匹配不到任何专业，用户永远看不到。
 * 这条断言就是拿 `Major.entries.map { it.code }.toSet()` 做全量比对。
 *
 * 读的是**真实的** assets 文件（不是夹具）：种子里写错一个数字，
 * 只有真文件能暴露。路径兼容 Gradle 的两种 cwd（项目根 / app/）。
 */
class SeedCatalogIntegrityTest {

    // ------------------------------------------------------------ 读文件

    /**
     * 定位真实的种子文件。
     *
     * Gradle 单测的 cwd 可能是项目根，也可能是 `app/`（取决于 AGP 版本和
     * 是否开了配置缓存），所以两个候选都试。找不到就**直接抛**，
     * 不能让测试静默跳过 —— 跳过等于假装通过。
     */
    private fun seedFile(): File {
        val candidates = listOf(
            File("src/main/assets/kaoyan/schools.json"),
            File("app/src/main/assets/kaoyan/schools.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 assets/kaoyan/schools.json，cwd=${File(".").absolutePath}")
    }

    /** 每次用例都重新解析：单文件 50KB 量级，解析开销可以忽略，换来用例互不干扰。 */
    private fun schools() = parseCatalogJson(seedFile().readText(Charsets.UTF_8))

    /** `Major` 里所有合法的学科代码。孤儿检测、覆盖检测都靠它。 */
    private val knownCodes: Set<String> by lazy { Major.entries.map { it.code }.toSet() }

    // ------------------------------------------------------------ 规模

    /** 契约 §1.3：不少于 240 条（扩到 260 也合法，所以用 `>=`）。 */
    @Test
    fun `种子条目不少于240条`() {
        val n = schools().size
        assertTrue("种子目录只有 $n 条，契约要求不少于 240 条（§1.3）", n >= 240)
    }

    /** 解析会静默丢掉缺 id / 缺院校名的条目（见 `catalogOrNull`），这里确认没被丢掉。 */
    @Test
    fun `解析不丢条目`() {
        val raw = seedFile().readText(Charsets.UTF_8)
        val declared = Regex("\"id\"\\s*:").findAll(raw).count()
        val parsed = parseCatalogJson(raw).size
        assertEquals("JSON 里有 $declared 个 id，只解析出 $parsed 条 —— 有条目被静默丢弃", declared, parsed)
    }

    // ------------------------------------------------------- 防孤儿数据（核心）

    /**
     * **每一条 `majorCode` 都必须在 `Major` 枚举里存在** —— 本套件最重要的一条。
     *
     * 用 `Major.entries.map { it.code }.toSet()` 全量比对，而不是「抽查几个热门专业」：
     * 孤儿 code 通常来自专业改名/合并（比如 0901 被拆成 0901/0903），
     * 恰好是抽查最容易漏掉的地方。
     */
    @Test
    fun `每条majorCode都能在Major枚举里找到`() {
        val orphans = schools()
            .filter { it.majorCode !in knownCodes }
            .groupBy { it.majorCode }
            .map { (code, list) ->
                "$code（${list.map { it.name }.distinct().joinToString("/")}，共 ${list.size} 条）"
            }
        assertTrue(
            "以下 majorCode 在 Major 枚举里不存在，这些条目永远匹配不到专业：" +
                orphans.joinToString("；"),
            orphans.isEmpty(),
        )
    }

    /**
     * 反向覆盖：`Major` 里的每个专业都至少要有 **2 所院校**（契约 §1.3）。
     *
     * 只有 1 所时，选校页选了该专业只出一张卡，用户会以为功能坏了；
     * 一条都没有的更糟 —— 专业 chip 点下去是空白页。
     */
    @Test
    fun `每个专业至少2所院校`() {
        val byCode = schools().groupBy { it.majorCode }
        val thin = Major.entries.map { m ->
            val list = byCode[m.code].orEmpty()
            Triple(m, list.size, list.map { it.name }.distinct().size)
        }.filter { it.third < 2 }

        assertTrue(
            "以下专业不足 2 所院校（条目数 / 院校数）：" +
                thin.joinToString { "${it.first.label}(${it.first.code})=${it.second}/${it.third}" },
            thin.isEmpty(),
        )
    }

    /** 该断言的反面：不能出现「枚举里有、种子里一条都没有」的专业。 */
    @Test
    fun `种子覆盖全部专业`() {
        val byCode = schools().groupBy { it.majorCode }
        val missing = Major.entries.filter { byCode[it.code].isNullOrEmpty() }
        assertTrue(
            "以下专业在种子里一条院校都没有：" +
                missing.joinToString { "${it.label}(${it.code})" },
            missing.isEmpty(),
        )
    }

    // ------------------------------------------------------------ 主键与字段

    /** `id` 唯一 —— 它是 Room 的主键，重复会让 `insertAll` 静默覆盖，目录悄悄变少。 */
    @Test
    fun `id唯一`() {
        val all = schools()
        val dup = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("id 重复：$dup", dup.isEmpty())
        assertEquals("id 唯一性校验失败", all.size, all.map { it.id }.distinct().size)
    }

    /**
     * 契约 §1.3：id 格式是 `<院校代码>-<专业代码>`，且后缀必须等于 `majorCode`。
     *
     * 后缀允许 4 位或 6 位：`Major.code` 里有 10 个二级学科代码是 6 位
     * （010102 中国哲学这类），种子跟着用 6 位后缀才能保持「后缀 == majorCode」。
     * 院校代码固定 5 位（教育部院校代码），这里放宽到 4~5 位是给早期 4 位老编码留余地。
     */
    @Test
    fun `id格式为院校代码加专业代码`() {
        val bad = schools().filter { s ->
            val m = Regex("^(\\d{4,5})-(\\d{4}|\\d{6})$").matchEntire(s.id)
            m == null || m.groupValues[2] != s.majorCode
        }
        assertTrue(
            "以下条目的 id 与格式/专业代码不符：" +
                bad.take(10).joinToString { "${it.id}→${it.majorCode}(${it.name})" },
            bad.isEmpty(),
        )
    }

    /**
     * 同一所院校的同一个专业只能有一条。
     *
     * `id` 唯一拦不住这种重复：v3.2 的种子里就有一条 `10435-0857`（majorCode 却是 0854），
     * 和正常的 `10435-0854` 指向同一所学校的同一个专业 —— id 不同，所以主键没报错，
     * 但选校页会出两张一模一样的卡，分数还差一分。这条断言就是抓它的。
     */
    @Test
    fun `院校加专业组合不重复`() {
        val dup = schools()
            .groupBy { it.name to it.majorCode }
            .filterValues { it.size > 1 }
            .map { (k, v) -> "${k.first}/${k.second} → ${v.map { it.id }}" }
        assertTrue("以下院校+专业组合重复出现：${dup.joinToString("；")}", dup.isEmpty())
    }

    /** 展示字段不能为空：空院校名 / 空专业名在卡片上是两个空白标签。 */
    @Test
    fun `院校名专业名省份层次非空`() {
        val bad = schools().filter {
            it.name.isBlank() || it.majorName.isBlank() || it.province.isBlank() || it.tier.isBlank()
        }
        assertTrue(
            "以下条目有空字段：" + bad.take(10).joinToString { it.id },
            bad.isEmpty(),
        )
    }

    /**
     * `majorName` 与专业标签一致 —— 但只钉**旧 20 项**。
     *
     * v3.2 的种子是 `majorName = Major.label` 一一对应生成的，这 20 项不该漂移：
     * 漂了说明数据被按旧名字手工补过，UI 卡片上和 chip 上会显示两个不同的专业名。
     *
     * 新增专业不做强制：学硕/专硕共用 4 位 code 时，种子里写「电子信息（专硕）」
     * 这类更细的展示名是合理的，卡死在这里会误判。
     */
    @Test
    fun `旧20项专业名与Major标签一致`() {
        val legacyCodes = setOf(
            "0812", "0835", "0854", "0811", "0802", "0814", "0817", "0832",
            "0901", "0904", "0905", "0906", "0902", "0953",
            "0202", "1203", "0502", "0351", "0451", "0552",
        )
        val labelByCode = Major.entries.associate { it.code to it.label }
        val bad = schools().filter { s ->
            s.majorCode in legacyCodes && labelByCode[s.majorCode]?.let { it != s.majorName } == true
        }
        assertTrue(
            "以下旧专业条目的 majorName 与 Major.label 不一致：" +
                bad.take(10).joinToString { "${it.id}: ${it.majorName} ≠ ${labelByCode[it.majorCode]}" },
            bad.isEmpty(),
        )
    }

    // ------------------------------------------------------------ 数字自洽

    /**
     * `quota > 0` 且 `applicants > 0`（契约 §1.3）。
     *
     * 0 在本模块里是「未收录」的哨兵值，会触发「招生人数未收录」警告并让
     * 报录比整条跳过 —— 种子数据不该滥用这个哨兵。
     */
    @Test
    fun `招生人数与报考人数都大于0`() {
        val bad = schools().filter { it.quota <= 0 || it.applicants <= 0 }
        assertTrue(
            "以下条目的 quota/applicants 不是正数：" +
                bad.take(10).joinToString { "${it.id}(q=${it.quota},a=${it.applicants})" },
            bad.isEmpty(),
        )
    }

    /** 报考人数少于招生人数在现实中会出现（缺考），但成批出现就是数据填反了。 */
    @Test
    fun `报录比在合理范围`() {
        val bad = schools().filter { it.applicants < it.quota }
        assertTrue(
            "以下条目报考人数少于招生人数，疑似填写颠倒：" +
                bad.take(10).joinToString { "${it.id}(q=${it.quota},a=${it.applicants})" },
            bad.isEmpty(),
        )
        // 报录比上限：热门专业 20:1 已经很夸张，超过说明数量级错了
        val extreme = schools().filter { it.applyRatio > 30.0 }
        assertTrue(
            "以下条目报录比超过 30:1，疑似数量级错误：" +
                extreme.take(10).joinToString { "${it.id}=${"%.1f".format(it.applyRatio)}" },
            extreme.isEmpty(),
        )
    }

    /**
     * 分数线在合理区间（契约 §1.3：学硕 260~400，农学可低至 250）。
     *
     * 这里放成 `250..400` 的统一区间：低于 250 的是国家线以下（不存在），
     * 高于 400 的是把「总分 500」和「单科分」记混了。
     */
    @Test
    fun `分数线在250到400之间`() {
        val all = schools()
        val allScores = all.flatMap { listOf(it.scoreLast, it.scorePrev) }
        val bad = all.filter { it.scoreLast !in 250..400 || it.scorePrev !in 250..400 }
        assertTrue(
            "以下条目的分数线越界：" +
                bad.take(10).joinToString { "${it.id}(${it.scoreLast}/${it.scorePrev})" },
            bad.isEmpty(),
        )
        assertTrue("分数线全为空", allScores.isNotEmpty())
    }

    /**
     * 相邻两年的分数线不该剧烈跳动：正常 ±20 分以内。
     *
     * 收紧到 ±20 而不是 ±50，是因为 `scorePrev` 常被误填成「上一年的国家线」
     * 或另一所学校的分数，差值会一下跳到几十分。
     */
    @Test
    fun `相邻两年分数线波动不超过20分`() {
        val bad = schools().filter { kotlin.math.abs(it.scoreLast - it.scorePrev) > 20 }
        assertTrue(
            "以下条目两年分数线相差超过 20 分：" +
                bad.take(10).joinToString { "${it.id}(${it.scoreLast}/${it.scorePrev})" },
            bad.isEmpty(),
        )
    }

    // ------------------------------------------------------------ 层次梯度

    /**
     * 契约 §1.3：C9 / 985 / 211 / 双一流 / 普通院校 都要有。
     *
     * 用 `SchoolTier.fromName` 解析而不是直接比字符串 —— 种子里的 `tier`
     * 既有枚举名（"985"）也有中文标签（"双一流"），fromName 是唯一的正确口径。
     */
    @Test
    fun `层次梯度齐全`() {
        val present = schools().map { SchoolTier.fromName(it.tier) }.toSet()
        val missing = SchoolTier.entries.filter { it !in present }
        assertTrue(
            "以下层次在种子里完全缺失：" + missing.joinToString { "${it.label}(${it.name})" },
            missing.isEmpty(),
        )
    }

    /** 普通院校不该绝迹：目录全是 985/211 的话，「保底档」永远推不出学校。 */
    @Test
    fun `普通院校占比不低于10%`() {
        val all = schools()
        val regular = all.count { SchoolTier.fromName(it.tier) == SchoolTier.REGULAR }
        val ratio = regular.toDouble() / all.size
        assertTrue(
            "普通院校只有 $regular/${all.size}（${"%.0f".format(ratio * 100)}%），保底档会没得推",
            ratio >= 0.10,
        )
    }

    // ------------------------------------------------------------ 一致性抽查

    /**
     * 同一专业的 `mathRequired` 在所有院校上必须一致。
     *
     * 这一段是逐条录入出来的，「复制上一条忘了改」会让同一个专业在 A 校要考数学、
     * B 校不考 —— 推荐引擎按专业算难度，两所学校会给出互相矛盾的警告。
     *
     * 只校验**一致性**、不校验与 `Major.mathRequired` 是否相同：新增专业的
     * 「通常是否考数学」是人工判断，学硕/专硕在同一专业下并不统一（契约 §1.2 原话），
     * 卡死在这里会把合法的数据判红。旧 20 项那部分由 `MajorCatalogTest` 的
     * 农学/人文类断言从枚举侧兜住。
     */
    @Test
    fun `同一专业的mathRequired处处一致`() {
        val bad = schools()
            .groupBy { it.majorCode }
            .filterValues { list -> list.map { it.mathRequired }.distinct().size > 1 }
            .map { (code, list) ->
                "$code → " + list.map { "${it.name}=${it.mathRequired}" }.toString()
            }
        assertTrue("以下专业的 mathRequired 在不同院校间不一致：${bad.joinToString("；")}", bad.isEmpty())
    }

    /** 旧 20 项专业的 `mathRequired` 已在 v3.2 定稿，种子里不允许被改写。 */
    @Test
    fun `旧20项专业的mathRequired未被改写`() {
        val legacy = mapOf(
            "0812" to true, "0835" to true, "0854" to true, "0811" to true, "0802" to true,
            "0814" to true, "0817" to true, "0832" to true,
            "0901" to false, "0904" to false, "0905" to false, "0906" to false,
            "0902" to false, "0953" to false,
            "0202" to true, "1203" to true, "0502" to false, "0351" to false,
            "0451" to false, "0552" to false,
        )
        val bad = schools().filter { s ->
            legacy[s.majorCode]?.let { it != s.mathRequired } ?: false
        }
        assertTrue(
            "以下旧专业条目的 mathRequired 被改写：" +
                bad.take(10).joinToString { "${it.id}(${it.majorName}=${it.mathRequired})" },
            bad.isEmpty(),
        )
    }

    /** 省份集合要有梯度：全挤在三四个省的话「意向地区」筛选就失去意义。 */
    @Test
    fun `省份覆盖不少于15个`() {
        val provinces = schools().map { it.province }.distinct()
        assertTrue("种子只覆盖 ${provinces.size} 个省份，地区筛选形同虚设", provinces.size >= 15)
    }

    // ------------------------------------------- 端到端：推荐结果必须听用户的话

    /**
     * **未勾「接受跨专业」时，推荐结果里不能出现别的专业。**
     *
     * 这条是 v3.3 交付过程中发现的真缺陷的回归护栏。种子从 108 扩到 463 之后，
     * 不对口的院校靠「地区不限 +12 / 招生规模 +8 / 报录比友好 +10 / 层次相当 +15」
     * 能堆到 28 分左右，而分数被报录比拖低的对口 985 只有 17 分 ——
     * 实测默认画像（计算机 / 稳妥 / 普通本科）下 UI 展示的前 60 条里
     * **只有 15 条对口，45 条是别的专业**（作物学、植物保护、兽医学…）。
     *
     * 用户没勾跨考的意思是「我不接受跨专业」，而不是「跨专业的排后面一点」，
     * 所以引擎现在把专业做成硬筛选。这里断言的是**行为契约**而不是分数：
     * 只要专业过滤被删掉 / 被改成软扣分，这条立刻红。
     */
    @Test
    fun `不接受跨考时推荐结果全部专业对口`() {
        val profile = KaoyanProfile(major = Major.COMPUTER, crossMajor = false)
        val recs = RecommendEngine.recommend(profile, schools(), limit = 60)
        val wrong = recs.filter { it.school.majorCode != profile.major.code }

        assertTrue("不接受跨考却没结果，专业过滤删过头了", recs.isNotEmpty())
        assertTrue(
            "不接受跨考时出现了 ${wrong.size}/${recs.size} 条不对口结果：" +
                wrong.take(8).joinToString { "${it.school.name}/${it.school.majorName}(${it.score.score}分)" },
            wrong.isEmpty(),
        )
    }

    /** 反过来：勾了跨考，别的专业必须能进来 —— 别把过滤写成无条件的。 */
    @Test
    fun `接受跨考时其他专业能进推荐结果`() {
        val profile = KaoyanProfile(major = Major.COMPUTER, crossMajor = true)
        val recs = RecommendEngine.recommend(profile, schools(), limit = 60)
        val other = recs.count { it.school.majorCode != profile.major.code }

        assertTrue("勾了跨考却一条别的专业都没有，过滤条件写反了", other > 0)
    }

    /**
     * 每个专业的推荐结果都要有内容 —— 过滤后不能有专业变成空白页。
     *
     * 这条和上面两条一起把「硬筛选」的边界钉住：过滤太严会空，
     * 过滤太松（或根本不过滤）会脏，两头都有断言。
     */
    @Test
    fun `每个专业在默认画像下都能选出院校`() {
        val all = schools()
        val empty = Major.entries.filter { m ->
            RecommendEngine.recommend(KaoyanProfile(major = m), all, limit = 0).isEmpty()
        }
        assertTrue(
            "以下专业在默认画像下选不出任何院校（过滤后空白页）：" +
                empty.joinToString { "${it.label}(${it.code})" },
            empty.isEmpty(),
        )
    }
}
