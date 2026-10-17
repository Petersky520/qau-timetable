package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.RecommendEngine
import cn.edu.qau.timetable.core.kaoyan.KaoyanProfile
import cn.edu.qau.timetable.data.kaoyan.parseCatalogJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 一次性集成探针：用真实的 assets/kaoyan/schools.json 走一遍
 * 「解析 -> 推荐」链路，确认种子数据不是摆设。
 *
 * 不放进正式套件：它依赖文件系统路径，属于本机验证用途。
 */
class SeedIntegrationProbeTest {

    private fun seedFile(): File {
        // 测试的 working dir 是 app/；向上找一层再拼 assets 路径
        val candidates = listOf(
            File("src/main/assets/kaoyan/schools.json"),
            File("app/src/main/assets/kaoyan/schools.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 schools.json，cwd=${File(".").absolutePath}")
    }

    @Test
    fun `真实种子文件能被完整解析`() {
        val raw = seedFile().readText(Charsets.UTF_8)
        val schools = parseCatalogJson(raw)

        println("=== 种子解析结果 ===")
        println("条目数: ${schools.size}")
        println("院校数: ${schools.map { it.name }.distinct().size}")
        println("专业代码数: ${schools.map { it.majorCode }.distinct().size}")
        println("省份数: ${schools.map { it.province }.distinct().size}")
        println("层次分布: ${schools.groupingBy { it.tier }.eachCount()}")
        println("id 唯一: ${schools.map { it.id }.distinct().size == schools.size}")
        println("quota 全>0: ${schools.all { it.quota > 0 }}")
        println("applicants 全>0: ${schools.all { it.applicants > 0 }}")
        println("分数线区间: ${schools.minOf { it.scoreLast }}..${schools.maxOf { it.scoreLast }}")

        assertTrue("解析出的条目数应 >= 40，实际 ${schools.size}", schools.size >= 40)
        assertEquals("id 必须唯一", schools.size, schools.map { it.id }.distinct().size)
        assertTrue("院校数应 >= 20", schools.map { it.name }.distinct().size >= 20)
        assertTrue("专业代码应 >= 12", schools.map { it.majorCode }.distinct().size >= 12)
        assertTrue("每条都该有院校名", schools.all { it.name.isNotBlank() })
        assertTrue("每条都该有专业名", schools.all { it.majorName.isNotBlank() })
    }

    @Test
    fun `真实种子数据能驱动推荐引擎产出三档`() {
        val schools = parseCatalogJson(seedFile().readText(Charsets.UTF_8))
        val profile = KaoyanProfile(major = Major.COMPUTER)
        val recs = RecommendEngine.recommend(profile, schools, limit = 1000)

        val byTier = recs.groupingBy { it.tier }.eachCount()
        println("=== 推荐结果（默认画像：计算机 / 稳妥 / 普通本科）===")
        println("总条目: ${recs.size}")
        println("分档: $byTier")
        println("分数区间: ${recs.minOf { it.score.score }}..${recs.maxOf { it.score.score }}")
        println("Top8:")
        recs.take(8).forEach { r ->
            println("  [${r.tier.label}] ${r.school.name} ${r.school.majorName} -> ${r.score.score}分")
            println("     理由: ${r.score.reasons.joinToString(" / ")}")
            if (r.score.warnings.isNotEmpty()) {
                println("     警告: ${r.score.warnings.joinToString(" / ")}")
            }
        }

        assertTrue("应产出推荐", recs.isNotEmpty())
        assertTrue("分数必须在 0..100", recs.all { it.score.score in 0..100 })
        assertTrue("每条推荐都该有院校", recs.all { it.school.name.isNotBlank() })

        // 关键回归：这个功能曾经完全坏过 —— 67 条推荐全落进 SAFE，三档形同虚设。
        // 现在要求至少「稳妥」档有结果，否则用户选了 MATCH 却一条稳妥都看不到。
        assertTrue(
            "稳妥档不应为空（曾因规则 3 方向写反而全部落进保底），实际 $byTier",
            (byTier[cn.edu.qau.timetable.core.kaoyan.Ambition.MATCH] ?: 0) > 0,
        )
    }

    @Test
    fun `冲刺意愿下应能产出冲刺档`() {
        val schools = parseCatalogJson(seedFile().readText(Charsets.UTF_8))
        val profile = KaoyanProfile(
            major = Major.COMPUTER,
            ambition = cn.edu.qau.timetable.core.kaoyan.Ambition.REACH,
        )
        val recs = RecommendEngine.recommend(profile, schools, limit = 1000)
        val reach = recs.count { it.tier == cn.edu.qau.timetable.core.kaoyan.Ambition.REACH }

        println("=== 冲刺意愿下的分档 ===")
        println("分档: ${recs.groupingBy { it.tier }.eachCount()}")
        println("冲刺档最高分: ${recs.filter { it.tier == cn.edu.qau.timetable.core.kaoyan.Ambition.REACH }.maxOfOrNull { it.score.score } ?: 0}")

        assertTrue("冲刺意愿下冲刺档不应为空，实际 $reach 条", reach > 0)
    }

    @Test
    fun `保底意愿下应能产出保底档`() {
        val schools = parseCatalogJson(seedFile().readText(Charsets.UTF_8))
        val profile = KaoyanProfile(
            major = Major.COMPUTER,
            ambition = cn.edu.qau.timetable.core.kaoyan.Ambition.SAFE,
        )
        val recs = RecommendEngine.recommend(profile, schools, limit = 1000)
        val safe = recs.count { it.tier == cn.edu.qau.timetable.core.kaoyan.Ambition.SAFE }

        println("=== 保底意愿下的分档 ===")
        println("分档: ${recs.groupingBy { it.tier }.eachCount()}")

        assertTrue("保底意愿下保底档不应为空，实际 $safe 条", safe > 0)
    }
}
