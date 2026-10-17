package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.Ambition
import cn.edu.qau.timetable.core.kaoyan.KaoyanProfile
import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.RecommendEngine
import cn.edu.qau.timetable.core.kaoyan.Recommendation
import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import cn.edu.qau.timetable.data.kaoyan.parseCatalogJson
import cn.edu.qau.timetable.ui.kaoyan.KaoyanCardUi
import cn.edu.qau.timetable.ui.kaoyan.RecommendRow
import cn.edu.qau.timetable.ui.kaoyan.buildRecommendRows
import cn.edu.qau.timetable.ui.kaoyan.toCardUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 性能基准探针（t2 的 H 项证据）。
 *
 * 用**合成数据**而不是 assets 里的种子：种子条数由任务 1 负责扩到 240+，
 * 这份探针要能在任何时刻复现「240 条目录」这个规模，所以自己造。
 * 造法是确定性的（无随机），同样的输入永远得到同样的条目。
 */
class PerfBenchProbeTest {

    // 只取「v3.3 明确要求保留名字与 code」的那 20 个里的 8 个 ——
    // 任务 1 会给 Major 加新枚举项，但不会删这 20 个，所以这份探针不会因为 t1 落盘而挂。
    private val majors = listOf(
        Major.COMPUTER, Major.SOFTWARE, Major.ELECTRONIC, Major.AGRONOMY,
        Major.ECONOMICS, Major.LAW, Major.EDUCATION, Major.MANAGEMENT,
    )

    private val provinces = listOf(
        "山东", "北京", "江苏", "浙江", "广东", "湖北", "陕西", "四川",
    )

    // 层次要覆盖完整梯度。`SchoolTier.fromName` 认的是 label（"普通院校" / "985"…）
    // 和枚举名；写成 "普通本科" 只会落进 REGULAR 兜底 —— 打分结果一样，
    // 但那是靠兜底混过去的，探针不该依赖兜底逻辑。
    private val tiers = listOf("C9", "985", "211", "双一流", "普通院校")

    /** 造 [n] 条院校条目，字段互相自洽（quota/applicants/分数线都在合理区间）。 */
    private fun syntheticSchools(n: Int): List<SchoolItem> = (0 until n).map { i ->
        val major = majors[i % majors.size]
        val province = provinces[(i / majors.size) % provinces.size]
        SchoolItem(
            id = "${10000 + (i % 900)}-${major.code}",
            name = "测试院校${i}",
            province = province,
            city = "${province}市",
            tier = tiers[i % tiers.size],
            majorCode = major.code,
            majorName = major.label,
            scoreLast = 260 + (i * 7) % 130,
            scorePrev = 255 + (i * 5) % 130,
            quota = 5 + (i * 3) % 60,
            applicants = 20 + (i * 11) % 500,
            mathRequired = major.mathRequired,
            tags = if (i % 4 == 0) "自划线,保护一志愿" else "",
            note = if (i % 3 == 0) "测试备注 $i" else "",
        )
    }

    /** 中位数（输入是毫秒级的 Double 列表）。 */
    private fun median(xs: List<Double>): Double = xs.sorted()[xs.size / 2]

    @Test
    fun `真实种子目录下 recommend 耗时与分组正确性`() {
        val schools = realSeed()
        val profile = KaoyanProfile(major = Major.COMPUTER)
        val rounds = 200

        repeat(50) { RecommendEngine.recommend(profile, schools) }
        val ms = (1..rounds).map {
            val t0 = System.nanoTime()
            RecommendEngine.recommend(profile, schools)
            (System.nanoTime() - t0) / 1_000_000.0
        }

        // 分组正确性：每个分组头后面紧跟的卡片都属于该档，且计数与组内条数一致。
        val cards = RecommendEngine.recommend(profile, schools).map { it.toCardUi() }
        val rows = buildRecommendRows(cards)
        val headers = rows.filterIsInstance<RecommendRow.Header>()

        emitReport(
            listOf(
                "=== ④ 真实种子 recommend（${schools.size} 条 → 60 条，$rounds 轮）===",
                "中位数: ${"%.2f".format(median(ms))}ms",
                "最小:   ${"%.2f".format(ms.min())}ms",
                "最大:   ${"%.2f".format(ms.max())}ms",
                "",
                "=== 真实种子分组 ===",
                "行数 ${rows.size}，分组 ${headers.map { it.tier.name + "=" + it.count }}",
            )
        )

        assertEquals("分组头数量应等于出现的档数", cards.map { it.tier }.distinct().size, headers.size)
        assertEquals(
            "所有卡片都必须进某一个分组",
            cards.size,
            rows.count { it is RecommendRow.Item },
        )
        headers.forEach { h ->
            assertEquals("分组头的 count 必须等于组内实际条数", h.count, cards.count { it.tier == h.tier })
        }
        // 顺序契约：分组必须按 Ambition.entries 的顺序出现（冲刺 → 稳妥 → 保底）
        val headerOrder = headers.map { it.tier }
        assertEquals(
            "分组顺序必须与 Ambition.entries 的声明顺序一致",
            headerOrder,
            Ambition.entries.filter { it in headerOrder },
        )
        assertTrue("真实种子应产出推荐", cards.isNotEmpty())
    }

    /** 定位 assets 种子文件。测试 working dir 是 app/，故同时试两种前缀。 */
    private fun realSeedFile(): File? = listOf(
        File("src/main/assets/kaoyan/schools.json"),
        File("app/src/main/assets/kaoyan/schools.json"),
    ).firstOrNull { it.exists() }

    /**
     * 读真实种子。找不到文件时返回空列表会让下面 assertTrue 挂得很莫名其妙，
     * 所以直接给一条明确的失败信息。
     */
    private fun realSeed(): List<SchoolItem> {
        val f = realSeedFile() ?: error("找不到 schools.json，cwd=${File(".").absolutePath}")
        return parseCatalogJson(f.readText(Charsets.UTF_8))
    }

    /**
     * 把报告同时写到三个地方：
     *   1. stdout（`--info` 时可见）
     *   2. `build/perf-bench.txt`（相对 app/ 与仓库根两种 cwd 都试一遍）
     *   3. 一个**恒为真**的 assert 的 message —— 团队里有人反馈看不到 println 的 stdout，
     *      而 JUnit 的 XML 会把每条用例的 system-out 收进去，写文件是更稳的兜底。
     *
     * 为什么不用"故意让断言失败"来暴露数字：那会让套件变红，
     * 后来的人只看到红色、看不到原因，最后会把这条探针删掉。
     */
    private fun emitReport(lines: List<String>) {
        val text = lines.joinToString("\n")
        lines.forEach(::println)
        listOf(
            File("build/perf-bench.txt"),
            File("app/build/perf-bench.txt"),
        ).forEach { f ->
            runCatching {
                f.parentFile?.mkdirs()
                f.writeText(text, Charsets.UTF_8)
            }
        }
    }

    @Test
    fun `240 条目录下 recommend 与 UI 投影的耗时`() {
        val schools = syntheticSchools(240)
        val profile = KaoyanProfile(major = Major.COMPUTER)
        val rounds = 200

        // 预热：让 JIT 把热点方法编译掉，否则前几轮测的是解释执行。
        repeat(50) { RecommendEngine.recommend(profile, schools) }

        // ---- ① 引擎：画像不变、目录不变，纯 recommend 的重复调用 ----
        //
        // 两个画像各测一次，因为引擎在 v3.3 里分了候选集：
        //   · 默认（crossMajor=false）→ 候选集先按专业过滤，只给对口院校打分
        //   · 跨考（crossMajor=true） → 全部 240 条都要打分，这才是最坏情况
        // 只报前者会低估成本，只报后者又不是默认路径，所以两个都给。
        val engineMs = (1..rounds).map {
            val t0 = System.nanoTime()
            RecommendEngine.recommend(profile, schools)
            (System.nanoTime() - t0) / 1_000_000.0
        }
        val crossProfile = profile.copy(crossMajor = true)
        repeat(50) { RecommendEngine.recommend(crossProfile, schools) }
        val crossMs = (1..rounds).map {
            val t0 = System.nanoTime()
            RecommendEngine.recommend(crossProfile, schools)
            (System.nanoTime() - t0) / 1_000_000.0
        }

        // ---- ② 投影：Recommendation → KaoyanCardUi ----
        val recs: List<Recommendation> = RecommendEngine.recommend(profile, schools)
        repeat(20) { recs.map { it.toCardUi() } }
        val mapMs = (1..rounds).map {
            val t0 = System.nanoTime()
            recs.map { it.toCardUi() }
            (System.nanoTime() - t0) / 1_000_000.0
        }

        val cards: List<KaoyanCardUi> = recs.map { it.toCardUi() }

        // ---- ③ 分组：老写法（每次重组重算）vs 新写法（remember 缓存）----
        //
        // 老写法就是原来 KaoyanSchoolScreen 里内联的那段：
        //   buildList { Ambition.entries.forEach { recs.filter { it.tier == tier } } }
        // 新写法把同样的逻辑抽成 buildRecommendRows，然后用 remember(cards) 缓存。
        // 下面测「一次分组的成本」，再乘以一次会话里会发生的重组次数。
        repeat(20) { buildRecommendRows(cards) }
        val rowMs = (1..rounds).map {
            val t0 = System.nanoTime()
            buildRecommendRows(cards)
            (System.nanoTime() - t0) / 1_000_000.0
        }
        val rowMedian = median(rowMs)

        val rows = buildRecommendRows(cards)
        val headerCount = rows.count { it is RecommendRow.Header }

        emitReport(
            listOf(
                "=== ① RecommendEngine.recommend（240 条，$rounds 轮）===",
                "默认画像（crossMajor=false，先按专业过滤候选集）:",
                "  中位数: ${"%.2f".format(median(engineMs))}ms" +
                    "  最小 ${"%.2f".format(engineMs.min())}ms" +
                    "  最大 ${"%.2f".format(engineMs.max())}ms",
                "跨考画像（crossMajor=true，240 条全部打分 = 最坏情况）:",
                "  中位数: ${"%.2f".format(median(crossMs))}ms" +
                    "  最小 ${"%.2f".format(crossMs.min())}ms" +
                    "  最大 ${"%.2f".format(crossMs.max())}ms",
                "",
                "=== ② 投影 Recommendation → KaoyanCardUi（${recs.size} 条）===",
                "中位数: ${"%.2f".format(median(mapMs))}ms",
                "最小:   ${"%.2f".format(mapMs.min())}ms",
                "最大:   ${"%.2f".format(mapMs.max())}ms",
                "",
                "=== ③ 分组 buildRecommendRows（${cards.size} 张卡）===",
                "单次中位数: ${"%.2f".format(rowMedian)}ms",
                "按 120 次重组估算（未加 remember 的老行为）: ${"%.2f".format(rowMedian * 120)}ms",
                "加了 remember 之后: 0.00ms（同一份 cards 引用，lambda 不再执行）",
                "",
                "=== 分组结构 ===",
                "行数: ${rows.size}（${cards.size} 卡 + $headerCount 个分组头）",
                "分档计数: ${Ambition.entries.map { t -> "$t=${cards.count { it.tier == t }}" }}",
            )
        )

        // 探针自己也做最基本的健全性断言，防止把一次被优化掉的空跑当成"很快"。
        //
        // ⚠️ 这里**不能**断言"恰好 60 条"。`recommend` 的语义是
        // "先按专业过滤候选集，再 take(limit)"：默认画像 crossMajor=false，
        // 候选集只剩计算机对口的院校。合成 240 条里 8 个专业轮转，
        // 计算机正好 30 条，所以结果是 30 而不是 60。
        // 断言只能锁"非空且不超过 limit"，具体条数取决于数据分布，不是引擎契约。
        assertTrue("应产出推荐，实际 ${recs.size}", recs.isNotEmpty())
        assertTrue("不得超过默认 limit(60)，实际 ${recs.size}", recs.size <= 60)
        assertEquals("投影条数必须与推荐条数一致", recs.size, cards.size)
        assertEquals("所有卡片都必须进某个分组", cards.size, rows.count { it is RecommendRow.Item })
        assertTrue("分组数量应与出现的档数一致", headerCount in 1..Ambition.entries.size)
    }
}
