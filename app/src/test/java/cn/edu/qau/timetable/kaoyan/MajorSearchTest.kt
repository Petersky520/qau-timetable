package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.MajorCategory
import cn.edu.qau.timetable.core.kaoyan.MajorSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 专业搜索的匹配语义。
 *
 * 这类"字符串匹配"的 bug 有个共同特点：**点了界面也不容易发现**。
 * 少命中两项，用户只会觉得"怎么没搜到"，不会怀疑代码。
 * 所以这里把四条匹配路径逐条钉死，并且专门钉两件容易被改坏的事：
 *
 * 1. **顺序稳定** —— 结果必须是 [Major.entries] 的子序列，不能重排。
 * 2. **代码前缀而非包含** —— `"12"` 不该命中 `0812`，否则"输入 12 开头"就没有意义了。
 */
class MajorSearchTest {

    private val all = Major.entries.toList()

    /** 断言 [actual] 是 [expected] 的子序列（保序，不要求连续）。 */
    private fun assertSubsequenceOf(expected: List<Major>, actual: List<Major>) {
        var i = 0
        actual.forEach { m ->
            while (i < expected.size && expected[i] != m) i++
            assertTrue(
                "结果 $actual 不是 ${expected.size} 项原始顺序的子序列（卡在 $m 上）",
                i < expected.size,
            )
            i++
        }
    }

    // ---------------------------------------------------------------- 中文名

    @Test
    fun `中文名精确匹配命中临床医学`() {
        val r = MajorSearch.search("临床医学")
        assertTrue("应命中 CLINICAL_MEDICINE", Major.CLINICAL_MEDICINE in r)
        // 「临床医学硕士」的名字里也含「临床医学」，是合理的连带命中
        assertTrue("应连带命中 CLINICAL_MEDICINE_MASTER", Major.CLINICAL_MEDICINE_MASTER in r)
    }

    @Test
    fun `中文名部分匹配 临床 命中两个临床专业`() {
        val r = MajorSearch.search("临床")
        assertTrue(Major.CLINICAL_MEDICINE in r)
        assertTrue(Major.CLINICAL_MEDICINE_MASTER in r)
        // 反向确认没有把不相干的捞进来
        assertTrue("不该命中 COMPUTER", Major.COMPUTER !in r)
    }

    // ---------------------------------------------------------------- 代码

    @Test
    fun `代码精确 0812 命中的都是 0812 前缀专业`() {
        val r = MajorSearch.search("0812")
        // 0812 是研究生一级学科代码；本科专业里 081201/081202（测绘/遥感）
        // 也以 0812 开头，所以 `startsWith` 会一并命中 —— 这是**期望行为**：
        // 用户输入 `0812` 想看「0812 这一族」，不是只想要那一个枚举。
        assertTrue("应命中 COMPUTER(0812)", Major.COMPUTER in r)
        assertTrue("命中项都应以 0812 开头", r.all { it.code.startsWith("0812") })
        // 反向确认没把无关专业捞进来
        assertTrue("不该命中 0809xx", r.none { it.code.startsWith("0809") })
    }

    @Test
    fun `代码前缀 081 命中多个 081x 专业`() {
        val r = MajorSearch.search("081")
        assertTrue("应命中 COMPUTER(0812)", Major.COMPUTER in r)
        assertTrue("应命中 AUTOMATION(0811)", Major.AUTOMATION in r)
        assertTrue("应命中 CIVIL(0814)", Major.CIVIL in r)
        assertTrue("应命中 ARCHITECTURE(0813)", Major.ARCHITECTURE in r)
        // 0832 食品不是 081 开头
        assertTrue("不该命中 FOOD(0832)", Major.FOOD !in r)
    }

    @Test
    fun `代码前缀 08 命中全部工学门类专业`() {
        val r = MajorSearch.search("08")
        val engineering = all.filter { it.category == MajorCategory.ENGINEERING }
        // 工学门类里 34 项代码都以 08 开头（已核对 Major.kt）
        assertTrue("工学门类应非空", engineering.isNotEmpty())
        engineering.forEach { m ->
            assertTrue("工学专业 ${m.name}(${m.code}) 应被 08 命中", m in r)
        }
    }

    /**
     * 6 位二级学科代码：t1 加进来的 10 项是 6 位（如 010101 中国哲学）。
     * `"0101"` 应同时命中一级学科 0101（哲学）和它下面的 010101..010106。
     */
    @Test
    fun `代码前缀 0101 同时命中一级学科与 6 位二级学科`() {
        val r = MajorSearch.search("0101")
        assertTrue("应命中一级学科 PHILOSOPHY(0101)", Major.PHILOSOPHY in r)
        assertTrue("应命中二级学科 CHINESE_PHILOSOPHY(010102)", Major.CHINESE_PHILOSOPHY in r)
        assertTrue("应命中二级学科 ETHICS(010105)", Major.ETHICS in r)
        // 010101 也是 0101 开头
        assertTrue("应命中 MARXIST_PHILOSOPHY(010101)", Major.MARXIST_PHILOSOPHY in r)
    }

    /**
     * 前缀匹配**不是**包含匹配。
     *
     * 这条是防回归：如果把 `startsWith` 改成 `contains`，
     * 用户输入 `"12"` 就会命中 0812/1002/1251… 一堆八竿子打不着的专业，
     * 代码搜索这个功能就等于废了。
     */
    @Test
    fun `代码是前缀匹配不是包含匹配`() {
        val r = MajorSearch.search("12")
        assertTrue("不该用包含匹配把 COMPUTER(0812) 捞出来", Major.COMPUTER !in r)
        // 但真有 12 开头的专业（1251 工商管理硕士）应该命中
        assertTrue("1251 应以 12 开头被命中", Major.MBA in r)
    }

    // ---------------------------------------------------------------- 门类

    @Test
    fun `门类名 医学 命中全部医学门类专业`() {
        val r = MajorSearch.search("医学")
        val medicine = all.filter { it.category == MajorCategory.MEDICINE }
        // 医学门类现含研究生学科 + 本科专业，数量随目录扩充变化；
        // 这里断言"非空且每一项都被命中"，不断言具体数字 ——
        // 钉死数字会让每次补专业都要改测试，而那不是这条用例要守的东西。
        assertTrue("医学门类不应为空", medicine.size >= 16)
        medicine.forEach { m ->
            assertTrue("医学门类专业 ${m.name} 应被「医学」命中", m in r)
        }
    }

    /**
     * 搜「医学」会连带命中「生物医学工程」（工学）和「兽医学」（农学）——
     * 它们的**名字里确实有「医学」**。这是设计意图，不是漏判：
     * 用户搜「医学」看到「兽医学」是合理的；只想看医学门类就点门类 chip。
     */
    @Test
    fun `门类名搜索会连带命中名字含医学术语的其他门类专业`() {
        val r = MajorSearch.search("医学")
        assertTrue("生物医学工程（工学）名字里有「医学」", Major.BIOMEDICAL_ENGINEERING in r)
        assertTrue("兽医学（农学）名字里有「医学」", Major.VETERINARY in r)
    }

    // ---------------------------------------------------------------- 大小写

    @Test
    fun `英文缩写大小写不敏感`() {
        val lower = MajorSearch.search("mba")
        val upper = MajorSearch.search("MBA")
        val mixed = MajorSearch.search("MbA")
        assertTrue("小写 mba 应命中 MBA", Major.MBA in lower)
        assertEquals("三种写法结果必须一致", lower, upper)
        assertEquals("三种写法结果必须一致", upper, mixed)
    }

    // ---------------------------------------------------------------- 边界

    @Test
    fun `空关键词返回全部`() {
        assertEquals(Major.entries.size, MajorSearch.search("").size)
    }

    @Test
    fun `纯空白关键词等同于空`() {
        assertEquals(
            "只输空格不该被当成有效搜索",
            Major.entries.size,
            MajorSearch.search("   ").size,
        )
    }

    @Test
    fun `首尾空格被忽略`() {
        assertEquals(
            "「 0812 」应与「0812」同结果",
            MajorSearch.search("0812"),
            MajorSearch.search(" 0812 "),
        )
    }

    @Test
    fun `无匹配返回空列表`() {
        val r = MajorSearch.search("这个专业根本不存在")
        assertTrue("应返回空列表，实际 $r", r.isEmpty())
    }

    // ---------------------------------------------------------------- 顺序

    @Test
    fun `结果顺序与原枚举一致`() {
        listOf("08", "医学", "01", "学").forEach { kw ->
            assertSubsequenceOf(all, MajorSearch.search(kw))
        }
    }

    /**
     * 顺序稳定性的关键回归：同一个关键词搜两次，必须完全相同。
     * 如果哪天有人给 search 加了 `sortedBy` 之类的"相关度排序"，这条会挂。
     */
    @Test
    fun `同一关键词重复搜索结果完全一致`() {
        val a = MajorSearch.search("学")
        val b = MajorSearch.search("学")
        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- 子集

    @Test
    fun `传子集时保持子集自身的顺序`() {
        val subset = listOf(Major.COMPUTER, Major.MATHEMATICS, Major.SOFTWARE)
        val r = MajorSearch.search("", subset)
        assertEquals("空关键词应原样返回传入的子集", subset, r)
        assertEquals("子集过滤后仍保序", listOf(Major.COMPUTER, Major.SOFTWARE), MajorSearch.search("08", subset))
    }

    // ---------------------------------------------------------------- isBlank

    @Test
    fun `isBlank 与 search 的空判定一致`() {
        assertTrue(MajorSearch.isBlank(""))
        assertTrue(MajorSearch.isBlank("  \t "))
        assertTrue(!MajorSearch.isBlank("0812"))
        assertTrue(!MajorSearch.isBlank(" 0812 "))
        // 两边必须一致：不一致时 UI 会一边显示分组一边显示"没找到"
        assertEquals(MajorSearch.isBlank(" 0812 "), MajorSearch.search(" 0812 ").size == Major.entries.size)
    }
}
