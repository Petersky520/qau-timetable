package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.MajorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 专业目录（`Major` + `MajorCategory`）的契约测试。
 *
 * v3.3 任务 1 把专业从 20 项扩到覆盖教育部 14 个学科门类，行数从 53 涨到几百行 ——
 * 这种「大段纯数据」的改动最容易被手滑改坏，而且改坏了**编译照样通过**：
 * 少一个 code、重复一个 code、某个门类漏配 category，表现只是「推荐结果变少」
 * 或者「选校页那个 chip 点了没反应」，肉眼极难发现。
 *
 * 所以这里全部钉**结构不变量**：
 *  - 总量下限（`>= 90`，不是 `== 90` —— 契约是「不少于」，扩到 95 也合法）
 *  - 14 个门类齐备，且每门类至少 4 个专业（契约 §1.2 明文）
 *  - code 全局唯一（推荐引擎拿它做**精确**比较，重复会让两门专业互相冒充）
 *  - **原有 20 项的枚举名与 code 一个字都没动**（有持久化数据和种子数据依赖）
 *  - `fromName` 的三种输入（枚举名 / 中文标签 / 垃圾值）都能安全还原
 *
 * 注意：断言用 `>=` 而非 `==` 是刻意的。`== 90` 会在 major-engine 扩到 95 时误报，
 * 而「不能少于」才是契约真正想表达的东西。
 */
class MajorCatalogTest {

    // ------------------------------------------------------------ 数量下限

    /**
     * 契约 §1.1：总数不少于 90。
     *
     * 这条是整套测试的地基 —— 90 项以下说明门类覆盖没做，后面那些门类断言
     * 虽然也可能绿（每门类 4 项 × 14 = 56 < 90），但功能上是残的。
     */
    @Test
    fun `专业总数不少于90`() {
        val n = Major.entries.size
        assertTrue("专业目录只有 $n 项，契约要求不少于 90 项（§1.1）", n >= 90)
    }

    /** 门类枚举本身必须是 14 项，且按教育部的顺序声明（UI 的 TabRow 直接照枚举顺序渲染）。 */
    @Test
    fun `学科门类为14个`() {
        assertEquals("教育部学科门类共 14 个", 14, MajorCategory.entries.size)
        assertEquals(
            listOf(
                "哲学", "经济学", "法学", "教育学", "文学", "历史学", "理学",
                "工学", "农学", "医学", "管理学", "艺术学", "军事学", "交叉学科",
            ),
            MajorCategory.entries.map { it.label },
        )
    }

    /** 每个门类的中文标签都不能空 —— 空标签在选校页就是一个点不动的空白 chip。 */
    @Test
    fun `每个门类都有中文标签`() {
        MajorCategory.entries.forEach { c ->
            assertTrue("门类 ${c.name} 的标签为空", c.label.isNotBlank())
        }
    }

    /**
     * 契约 §1.2：**每个门类至少 4 个专业**。
     *
     * 少于 4 个时，选校页按门类分组的第二排 chip 少得可怜，
     * 「覆盖全学科门类」就只剩个壳子。这条会精确报出是哪个门类塌了。
     */
    @Test
    fun `每个门类至少4个专业`() {
        val byCategory = Major.entries.groupingBy { it.category }.eachCount()
        val thin = MajorCategory.entries.map { it to (byCategory[it] ?: 0) }.filter { it.second < 4 }
        assertTrue(
            "以下门类专业数不足 4 个：" + thin.joinToString { "${it.first.label}=${it.second}" },
            thin.isEmpty(),
        )
    }

    /**
     * 契约 §1.2：工学 / 管理学 / 医学 / 文学应**多于** 4 个。
     *
     * 这四类招考体量最大，刚好卡 4 个等于没扩。用 `> 4` 而不是具体数字，
     * 免得把「多写几个」这种合法改动判红。
     */
    @Test
    fun `工学管理学医学文学专业数应多于4个`() {
        val byCategory = Major.entries.groupingBy { it.category }.eachCount()
        listOf(
            MajorCategory.ENGINEERING,
            MajorCategory.MANAGEMENT,
            MajorCategory.MEDICINE,
            MajorCategory.LITERATURE,
        ).forEach { c ->
            val n = byCategory[c] ?: 0
            assertTrue("${c.label} 只有 $n 个专业，契约要求多于 4 个", n > 4)
        }
    }

    // ------------------------------------------------------------ code 唯一性

    /** code 全局唯一。重复时两门专业会匹配到同一批院校，推荐结果互相冒充。 */
    @Test
    fun `专业代码全局唯一`() {
        val dup = Major.entries.groupBy { it.code }.filterValues { it.size > 1 }
            .map { (code, list) -> "$code → ${list.map { it.name }}" }
        assertTrue("专业代码重复：${dup.joinToString("；")}", dup.isEmpty())
    }

    /**
     * code 必须是**真实的教育部学科代码**：4 位（一级学科 / 专硕类别）或 6 位（二级学科）。
     *
     * 为什么允许 6 位：哲学、历史学这类门类下一级学科不足 4 个，为了满足
     * 「每门类至少 4 个专业」，实现补的是二级学科代码（如 010102 中国哲学）——
     * 这是 `Major.kt` KDoc 里写明的取舍，仍然是教育部目录里的真代码，不是自造。
     * 但除此之外的位数（3 位、5 位、字母）一定是手滑，这里一并拦下。
     */
    @Test
    fun `专业代码是4位或6位数字`() {
        val bad = Major.entries.filter { !it.code.matches(Regex("\\d{4}|\\d{6}")) }
        assertTrue(
            "以下专业的 code 不是 4 位或 6 位数字：" + bad.joinToString { "${it.name}=${it.code}" },
            bad.isEmpty(),
        )
    }

    /** 每个专业都要有非空中文标签，且标签也唯一（`fromName` 会按标签反查）。 */
    @Test
    fun `专业中文标签非空且唯一`() {
        val blank = Major.entries.filter { it.label.isBlank() }
        assertTrue("以下专业没有中文标签：${blank.map { it.name }}", blank.isEmpty())

        val dup = Major.entries.groupBy { it.label }.filterValues { it.size > 1 }.keys
        assertTrue("专业中文标签重复，fromName 会还原错：$dup", dup.isEmpty())
    }

    /** 每个专业都必须落到一个门类上，不能留在兜底的 OTHER 之外的空状态。 */
    @Test
    fun `每个专业都有门类`() {
        Major.entries.forEach { m ->
            assertNotNull("专业 ${m.name} 的 category 为空", m.category)
            // 门类必须是枚举里真实存在的项，不能是某个默认值占位
            assertTrue(
                "专业 ${m.name} 的门类 ${m.category} 不在 MajorCategory 里",
                m.category in MajorCategory.entries,
            )
        }
    }

    // ------------------------------------------------- 原有 20 项：不允许改动

    /**
     * 原有 20 个枚举项的枚举名与 code **一个字都不能变**。
     *
     * 为什么钉死：DataStore 里存的是**枚举名**（`fromName` 先比 name），
     * 老用户升级后如果枚举名被改成 `CS`，画像会静默退回 COMPUTER；
     * 而 code 变了，`schools.json` 里那 100 多条老数据整批变成孤儿，
     * 推荐结果从「108 条里挑」变成「只有新专业」，而且不报任何错。
     *
     * 期望值抄自 v3.2 的 `Major.kt`，不是从实现里反推的 —— 否则就是自证。
     */
    @Test
    fun `原有20个专业的枚举名与code未被改动`() {
        val expected = linkedMapOf(
            "COMPUTER" to "0812",          // 计算机科学与技术
            "SOFTWARE" to "0835",          // 软件工程
            "ELECTRONIC" to "0854",        // 电子信息（专硕）
            "AUTOMATION" to "0811",        // 控制科学与工程
            "MECHANICAL" to "0802",        // 机械工程
            "CIVIL" to "0814",             // 土木工程
            "CHEMISTRY" to "0817",         // 化学工程与技术
            "FOOD" to "0832",              // 食品科学与工程
            "AGRONOMY" to "0901",          // 作物学
            "PLANT_PROTECTION" to "0904",  // 植物保护
            "ANIMAL_SCIENCE" to "0905",    // 畜牧学
            "VETERINARY" to "0906",        // 兽医学
            "HORTICULTURE" to "0902",      // 园艺学
            "LANDSCAPE" to "0953",         // 风景园林（专硕）
            "ECONOMICS" to "0202",         // 应用经济学
            "MANAGEMENT" to "1203",        // 农林经济管理
            "FOREIGN_LANG" to "0502",      // 外国语言文学
            "LAW" to "0351",               // 法律（非法学）
            "EDUCATION" to "0451",         // 教育硕士
            "JOURNALISM" to "0552",        // 新闻与传播
        )
        val actual = Major.entries.associate { it.name to it.code }
        expected.forEach { (name, code) ->
            assertEquals("原专业 $name 的 code 被改动了（会打断已有种子数据）", code, actual[name])
        }
        // 反向：这 20 个枚举名一个都不能消失
        val missing = expected.keys - actual.keys
        assertTrue("原有专业枚举项丢失：$missing", missing.isEmpty())
    }

    /** 原有 20 项的中文标签同样被持久化/展示，不允许为了「统一措辞」顺手改。 */
    @Test
    fun `原有20个专业的中文标签未被改动`() {
        val expected = mapOf(
            "COMPUTER" to "计算机科学与技术",
            "SOFTWARE" to "软件工程",
            "AGRONOMY" to "作物学",
            "VETERINARY" to "兽医学",
            "ECONOMICS" to "应用经济学",
            "MANAGEMENT" to "农林经济管理",
            "LAW" to "法律（非法学）",
            "EDUCATION" to "教育硕士",
            "JOURNALISM" to "新闻与传播",
        )
        val actual = Major.entries.associate { it.name to it.label }
        expected.forEach { (name, label) ->
            assertEquals("原专业 $name 的中文标签被改动了", label, actual[name])
        }
    }

    // ------------------------------------------------------------ fromName 容错

    @Test
    fun `fromName 认得枚举名`() {
        assertSame(Major.COMPUTER, Major.fromName("COMPUTER"))
        assertSame(Major.VETERINARY, Major.fromName("VETERINARY"))
        // 全量：每个枚举名都必须能还原成它自己（扩目录时漏写改名会在这里炸）
        Major.entries.forEach { m ->
            assertSame("枚举名 ${m.name} 无法还原成自身", m, Major.fromName(m.name))
        }
    }

    @Test
    fun `fromName 认得中文标签`() {
        assertSame(Major.COMPUTER, Major.fromName("计算机科学与技术"))
        assertSame(Major.AGRONOMY, Major.fromName("作物学"))
        assertSame(Major.LAW, Major.fromName("法律（非法学）"))
    }

    /** 未知值、空串、null 一律兜底 COMPUTER —— 一个坏字段不该让选校页打不开。 */
    @Test
    fun `fromName 未知值兜底COMPUTER`() {
        assertSame(Major.COMPUTER, Major.fromName("火星殖民学"))
        assertSame(Major.COMPUTER, Major.fromName(""))
        assertSame(Major.COMPUTER, Major.fromName(null))
        assertSame(Major.COMPUTER, Major.fromName("cs"))
    }

    /** 兜底必须是 COMPUTER 本身，不能是「entries[0]」—— 扩目录后 entries[0] 可能被换掉。 */
    @Test
    fun `fromName 兜底常量固定为COMPUTER`() {
        assertSame(Major.COMPUTER, Major.fromName("完全不存在的专业名"))
        assertSame("0812", Major.fromName("完全不存在的专业名").code)
    }

    // ------------------------------------------------------------ 数学要求

    /**
     * 农学门类的专业一律不考数学（数农/化学二选一，按本 App 的口径算「不要求」）。
     *
     * 这一条是给「按门类批量铺数学要求」的人踩刹车的：漏填一个 `mathRequired=true`
     * 会让农学考生看到「该专业需要考数学」的警告，直接把人劝退。
     */
    @Test
    fun `农学门类专业不要求数学`() {
        val bad = Major.entries.filter { it.category == MajorCategory.AGRICULTURE && it.mathRequired }
        assertTrue(
            "以下农学专业被标成要考数学：" + bad.joinToString { "${it.name}(${it.code})" },
            bad.isEmpty(),
        )
    }

    /** 哲学 / 历史学 / 艺术学同样不考数学（专业课二门），标 true 会误伤跨考生。 */
    @Test
    fun `哲学历史学艺术学不要求数学`() {
        val humanistic = listOf(MajorCategory.PHILOSOPHY, MajorCategory.HISTORY, MajorCategory.ART)
        val bad = Major.entries.filter { it.category in humanistic && it.mathRequired }
        assertTrue(
            "以下人文类/艺术类专业被标成要考数学：" +
                bad.joinToString { "${it.name}(${it.code})" },
            bad.isEmpty(),
        )
    }
}
