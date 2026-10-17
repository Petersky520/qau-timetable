package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.data.kaoyan.parseCatalogJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 院校目录 JSON 的解析。
 *
 * 契约 §7 原本要求测 `assets/kaoyan/schools.json`，但读 assets 需要 Android Context，
 * 于是把解析抽成 `internal fun parseCatalogJson(raw: String)`，直接喂字符串 ——
 * 「内置种子」和「远端刷新」走的是同一个函数，测它就等于同时测了两条链路。
 *
 * 这一层的失败模式是**静默的数据丢失**：少解析一个字段不会报错，
 * 只会让推荐结果里那几条院校集体掉到 0 分。所以缺字段的容错逐项断言。
 */
class SchoolCatalogParseTest {

    // ------------------------------------------------------------ 正常 JSON

    /** 字段齐全的一条：每个字段都要原样落到 `SchoolItem` 上，不能串位。 */
    @Test
    fun `完整条目全部字段正确解析`() {
        val raw = """
            {
              "schools": [
                {
                  "id": "10019-0901",
                  "name": "中国农业大学",
                  "province": "北京",
                  "city": "北京",
                  "tier": "985",
                  "majorCode": "0901",
                  "majorName": "作物学",
                  "scoreLast": 358,
                  "scorePrev": 356,
                  "quota": 42,
                  "applicants": 176,
                  "mathRequired": false,
                  "tags": "自划线,保护一志愿",
                  "note": "示例数据"
                }
              ]
            }
        """.trimIndent()

        val list = parseCatalogJson(raw)
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("10019-0901", s.id)
        assertEquals("中国农业大学", s.name)
        assertEquals("北京", s.province)
        assertEquals("北京", s.city)
        assertEquals("985", s.tier)
        assertEquals("0901", s.majorCode)
        assertEquals("作物学", s.majorName)
        assertEquals(358, s.scoreLast)
        assertEquals(356, s.scorePrev)
        assertEquals(42, s.quota)
        assertEquals(176, s.applicants)
        assertFalse(s.mathRequired)
        assertEquals("自划线,保护一志愿", s.tags)
        assertEquals("示例数据", s.note)
    }

    /** 多条按数组顺序保留 —— 目录顺序即 UI 顺序。 */
    @Test
    fun `多条条目按原顺序返回`() {
        val raw = """
            {"schools":[
              {"id":"a","name":"甲大学"},
              {"id":"b","name":"乙大学"},
              {"id":"c","name":"丙大学"}
            ]}
        """.trimIndent()
        assertEquals(listOf("a", "b", "c"), parseCatalogJson(raw).map { it.id })
    }

    /** `mathRequired: true` 与字符串形式的数字都要认（远端数据不一定是严格类型）。 */
    @Test
    fun `布尔与字符串数字都能解析`() {
        val raw = """
            {"schools":[
              {"id":"x","name":"甲","mathRequired":true,"quota":"30","applicants":"45","scoreLast":"340"}
            ]}
        """.trimIndent()
        val s = parseCatalogJson(raw).single()
        assertTrue(s.mathRequired)
        assertEquals(30, s.quota)
        assertEquals(45, s.applicants)
        assertEquals(340, s.scoreLast)
    }

    /** 浮点与负数字面量不该让整份解析炸掉（`toInt()` 截断）。 */
    @Test
    fun `浮点数字被截断为整数`() {
        val raw = """{"schools":[{"id":"x","name":"甲","quota":12.9,"applicants":7.4}]}"""
        val s = parseCatalogJson(raw).single()
        assertEquals(12, s.quota)
        assertEquals(7, s.applicants)
    }

    /** 转义字符（换行、引号、反斜杠、\u 转义）要正确还原，否则 note 里会出现乱码。 */
    @Test
    fun `字符串转义被正确还原`() {
        val raw = """
            {"schools":[{"id":"x","name":"甲","note":"第一行\n第二行 \"引号\" \\ 反斜杠 \u4e2d"}]}
        """.trimIndent()
        val s = parseCatalogJson(raw).single()
        assertEquals("第一行\n第二行 \"引号\" \\ 反斜杠 中", s.note)
    }

    /** 顶层前后带空白也能解析（远端返回常带 BOM 之外的空白与换行）。 */
    @Test
    fun `顶层前后空白被忽略`() {
        val raw = "\n\n  {\"schools\":[{\"id\":\"x\",\"name\":\"甲\"}]}  \n"
        assertEquals(1, parseCatalogJson(raw).size)
    }

    // ------------------------------------------------------------ 缺字段容错

    /**
     * 只给主键和院校名：其余字段全部走缺省值，条目仍然保留。
     *
     * 这是最关键的一条 —— 远端是用户自己维护的 JSON，少写一个 `note`
     * 不该让整所院校消失。
     */
    @Test
    fun `缺字段时走缺省值且条目保留`() {
        val raw = """{"schools":[{"id":"x","name":"甲"}]}"""
        val s = parseCatalogJson(raw).single()
        assertEquals("x", s.id)
        assertEquals("甲", s.name)
        assertEquals("", s.province)
        assertEquals("", s.city)
        assertEquals("REGULAR", s.tier)
        assertEquals("", s.majorCode)
        assertEquals("", s.majorName)
        assertEquals(0, s.scoreLast)
        assertEquals(0, s.scorePrev)
        assertEquals(0, s.quota)
        assertEquals(0, s.applicants)
        assertFalse(s.mathRequired)
        assertEquals("", s.tags)
        assertEquals("", s.note)
    }

    /** 缺字段的条目和完整条目混在一起时，两条都要在。 */
    @Test
    fun `部分条目缺字段不影响其他条目`() {
        val raw = """
            {"schools":[
              {"id":"full","name":"完整大学","province":"山东","quota":20,"applicants":40},
              {"id":"partial","name":"残缺大学"}
            ]}
        """.trimIndent()
        val list = parseCatalogJson(raw)
        assertEquals(2, list.size)
        assertEquals(20, list[0].quota)
        assertEquals(0, list[1].quota)
    }

    /** 缺 `id` 或 `name` 的条目被丢弃 —— 它们在任何界面里都没法展示，也当不了主键。 */
    @Test
    fun `缺少id或name的条目被丢弃`() {
        val raw = """
            {"schools":[
              {"id":"ok","name":"正常大学"},
              {"name":"没有id大学"},
              {"id":"no-name"},
              {"id":"","name":"空id大学"}
            ]}
        """.trimIndent()
        val list = parseCatalogJson(raw)
        assertEquals(1, list.size)
        assertEquals("ok", list[0].id)
    }

    /** 数组里的非对象元素（数字、字符串、null）被跳过，不牵连其他条目。 */
    @Test
    fun `数组里的非对象元素被跳过`() {
        val raw = """{"schools":[123,"oops",null,{"id":"x","name":"甲"}]}"""
        val list = parseCatalogJson(raw)
        assertEquals(1, list.size)
        assertEquals("x", list[0].id)
    }

    /** 字段类型不匹配时回落缺省值，不抛异常。 */
    @Test
    fun `字段类型不匹配时回落缺省值`() {
        val raw = """{"schools":[{"id":"x","name":"甲","quota":{"bad":1},"scoreLast":[1,2],"mathRequired":"yes"}]}"""
        val s = parseCatalogJson(raw).single()
        assertEquals(0, s.quota)
        assertEquals(0, s.scoreLast)
        assertFalse(s.mathRequired)
    }

    // ------------------------------------------------------------ 非法 JSON

    /** 完全不是 JSON → 空列表（调用方据此报「返回的不是合法 JSON」）。 */
    @Test
    fun `非法JSON返回空列表`() {
        assertTrue(parseCatalogJson("<html><body>404 Not Found</body></html>").isEmpty())
        assertTrue(parseCatalogJson("").isEmpty())
        assertTrue(parseCatalogJson("   ").isEmpty())
    }

    /** 结构破损：括号不闭合、缺逗号、缺冒号 —— 全部返回空列表而不是抛异常。 */
    @Test
    fun `结构破损的JSON返回空列表`() {
        assertTrue(parseCatalogJson("""{"schools":[{"id":"x","name":"甲"}""").isEmpty())
        assertTrue(parseCatalogJson("""{"schools":[{"id":"x" "name":"甲"}]}""").isEmpty())
        assertTrue(parseCatalogJson("""{"schools" [{"id":"x","name":"甲"}]}""").isEmpty())
        assertTrue(parseCatalogJson("""{'schools':[]}""").isEmpty())
    }

    /** 合法 JSON 但后面拖着垃圾 → 也算非法（防止把 HTML 尾缀当数据收下）。 */
    @Test
    fun `JSON尾部有垃圾时返回空列表`() {
        assertTrue(parseCatalogJson("""{"schools":[]} garbage""").isEmpty())
    }

    /** 合法 JSON 但不是对象（数组 / 字符串 / 数字）→ 空列表。 */
    @Test
    fun `顶层不是对象时返回空列表`() {
        val jsonString = "\"just a string\""
        assertTrue(parseCatalogJson("""[{"id":"x","name":"甲"}]""").isEmpty())
        assertTrue(parseCatalogJson(jsonString).isEmpty())
        assertTrue(parseCatalogJson("42").isEmpty())
        assertTrue(parseCatalogJson("null").isEmpty())
    }

    /** 合法 JSON 但没有 `schools` 字段 → 空列表（不是崩溃）。 */
    @Test
    fun `缺少schools字段时返回空列表`() {
        assertTrue(parseCatalogJson("""{"other":[]}""").isEmpty())
        assertTrue(parseCatalogJson("{}").isEmpty())
    }

    /** `schools` 不是数组 → 空列表。 */
    @Test
    fun `schools不是数组时返回空列表`() {
        assertTrue(parseCatalogJson("""{"schools":"oops"}""").isEmpty())
        assertTrue(parseCatalogJson("""{"schools":{"id":"x"}}""").isEmpty())
        assertTrue(parseCatalogJson("""{"schools":null}""").isEmpty())
    }

    /** 空数组 → 空列表（「格式对但没有条目」，和非法 JSON 是两回事，但返回值一样）。 */
    @Test
    fun `空数组返回空列表`() {
        assertTrue(parseCatalogJson("""{"schools":[]}""").isEmpty())
    }

    /** 解析不抛异常 —— 这是 internal 函数的硬性约定，调用方不包 try。 */
    @Test
    fun `任何输入都不抛异常`() {
        listOf(
            "",
            "{",
            "}",
            "[",
            "]",
            "\"",
            "\\",
            """{"schools":[}""",
            """{"schools":[{"id":"x"}]}""",
            (1..200).joinToString("") { "{" },
            "\u0000\u0001\u0002",
        ).forEach { input ->
            parseCatalogJson(input) // 不抛就是通过
        }
    }

    // ------------------------------------------------------------ 与推荐引擎的衔接

    /** 解析出来的条目能被推荐引擎直接消费（字段语义对得上，不出现 NaN / 越界）。 */
    @Test
    fun `解析结果可直接喂给推荐引擎`() {
        val raw = """
            {"schools":[{"id":"10019-0901","name":"中国农业大学","province":"北京","city":"北京",
              "tier":"985","majorCode":"0901","majorName":"作物学",
              "scoreLast":358,"scorePrev":356,"quota":42,"applicants":176,"mathRequired":false}]}
        """.trimIndent()
        val schools = parseCatalogJson(raw)
        assertEquals(1, schools.size)
        // 报录比：176 / 42 ≈ 4.19，落在常态区间
        assertEquals(176.0 / 42.0, schools[0].applyRatio, 0.0001)
    }
}
