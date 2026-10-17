package cn.edu.qau.timetable.kaoyan

import cn.edu.qau.timetable.core.kaoyan.ResearchSite
import cn.edu.qau.timetable.core.kaoyan.SiteCatalog
import cn.edu.qau.timetable.core.kaoyan.SiteCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置站点目录与 `searchUrl` 的 URL 拼接。
 *
 * 这一层的错误全都是「拼出一个打不开的 URL」——不会抛异常、不会有日志，
 * 用户只会看到浏览器里一个 404 页面然后以为功能坏了。
 * 所以每条断言都咬在具体字符上（`%20` 而不是 `+`、`&` 必须编码）。
 */
class SiteCatalogTest {

    private val all = SiteCatalog.all

    // ------------------------------------------------------------ 目录本身

    /** 契约 §2 要求至少 18 条（裁决 D4：实现 28 条，断言用 >= 18）。 */
    @Test
    fun `目录至少18条`() {
        assertTrue("内置目录只有 ${all.size} 条，不满足契约的 >= 18", all.size >= 18)
    }

    /** id 唯一 —— 它是列表 key 和收藏持久化的键，重复会让两条站点共用一个状态。 */
    @Test
    fun `id 唯一`() {
        val dup = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("站点 id 重复：$dup", dup.isEmpty())
    }

    /** 站点名也要唯一，否则用户在选择弹窗里分不清是哪一个。 */
    @Test
    fun `站点名不重复`() {
        val dup = all.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("站点名重复：$dup", dup.isEmpty())
    }

    /** 每个 homepage 都以 http 开头 —— 拼错了就是 `Intent.ACTION_VIEW` 直接失败。 */
    @Test
    fun `每个首页都以 http 开头`() {
        val bad = all.filterNot { it.homepage.startsWith("http") }
        assertTrue("这些站点的 homepage 不是 http(s)：${bad.map { it.id to it.homepage }}", bad.isEmpty())
    }

    /** 每个 searchTemplate 也必须是可打开的 http(s) 地址。 */
    @Test
    fun `每个检索模板都以 http 开头`() {
        val bad = all.filterNot { it.searchTemplate.startsWith("http") }
        assertTrue("这些站点的 searchTemplate 不是 http(s)：${bad.map { it.id to it.searchTemplate }}", bad.isEmpty())
    }

    /** 检索模板必须带 `{q}` 占位，否则替换后仍是死链（要么没关键词，要么原样留着 q）。 */
    @Test
    fun `每个检索模板都带占位符`() {
        val bad = all.filterNot { it.searchTemplate.contains("{q}") }
        assertTrue("这些站点缺少 {q} 占位：${bad.map { it.id }}", bad.isEmpty())
    }

    /** 描述不能为空 —— 卡片里那一行是用户判断「要不要点进去」的唯一依据。 */
    @Test
    fun `每个站点都有描述`() {
        val bad = all.filter { it.description.isBlank() }
        assertTrue("这些站点没有描述：${bad.map { it.id }}", bad.isEmpty())
    }

    /** 五个分类全部覆盖（裁决 D4）。少一个分类，对应子页签会整片空白。 */
    @Test
    fun `五个分类全覆盖`() {
        val covered = all.map { it.category }.toSet()
        assertEquals(
            "缺分类：${SiteCategory.entries.toSet() - covered}",
            SiteCategory.entries.toSet(),
            covered,
        )
    }

    /** 契约点名的站点必须真的在目录里（防止实现时漏掉关键的那几个）。 */
    @Test
    fun `契约点名的关键站点都在目录里`() {
        val names = all.map { it.name }
        listOf(
            "中国知网 CNKI",
            "万方数据",
            "维普期刊",
            "Google Scholar",
            "Semantic Scholar",
            "arXiv",
            "PubMed",
            "DOAJ 开放获取期刊",
            "CORE",
            "百度学术",
            "国家知识产权局",
            "SooPAT 专利搜索",
            "中国大学 MOOC",
            "学堂在线",
            "国家高等教育智慧教育平台",
            "考研帮",
            "哔哩哔哩搜索",
            "超星尔雅 / 学习通",
            "MIT OpenCourseWare",
            "Coursera",
            "中国研究生招生信息网",
            "教育部学位与研究生教育发展中心",
        ).forEach { expected ->
            assertTrue("目录里缺少契约点名的站点「$expected」", names.contains(expected))
        }
    }

    // ------------------------------------------------------------ searchUrl

    private fun siteOf(id: String): ResearchSite =
        all.firstOrNull { it.id == id } ?: error("目录里没有 id=$id 的站点")

    /** 正常的 ASCII 关键词：替换 + 保留站点自己的参数名。 */
    @Test
    fun `英文关键词按站点参数名替换`() {
        val arxiv = siteOf("arxiv")
        assertEquals(
            "https://arxiv.org/search/?query=transformer&searchtype=all",
            SiteCatalog.searchUrl(arxiv, "transformer"),
        )
    }

    /** 中文关键词必须百分号编码，且不能被编成乱码（UTF-8）。 */
    @Test
    fun `中文关键词按UTF8编码`() {
        val arxiv = siteOf("arxiv")
        // 「考研」的 UTF-8 字节：E8 80 83 E7 A0 94
        assertEquals(
            "https://arxiv.org/search/?query=%E8%80%83%E7%A0%94&searchtype=all",
            SiteCatalog.searchUrl(arxiv, "考研"),
        )
    }

    /**
     * 空格必须编成 `%20` 而不是 `+`。
     *
     * `URLEncoder` 走的是表单编码，空格就是 `+`；但 `+` 在查询串里是**字面加号**，
     * 站点会把「deep learning」当成「deep+learning」去搜。这是本项目必须自己纠正的一处。
     */
    @Test
    fun `空格编码为百分号20而不是加号`() {
        val url = SiteCatalog.searchUrl(siteOf("google-scholar"), "deep learning")
        assertTrue("空格被编成了 +：$url", url.contains("%20"))
        assertFalse("出现了表单编码的 +：$url", url.contains("+"))
        assertEquals("https://scholar.google.com/scholar?q=deep%20learning", url)
    }

    /** 连续空格：每个空格都要变成 `%20`，不能塌成一个。 */
    @Test
    fun `连续空格逐个编码`() {
        val url = SiteCatalog.searchUrl(siteOf("google-scholar"), "a  b")
        assertEquals("https://scholar.google.com/scholar?q=a%20%20b", url)
    }

    /**
     * `&` 必须编成 `%26`。
     *
     * 模板里本身就有 `&`（如 arXiv 的 `&searchtype=all`），如果把关键词里的 `&`
     * 原样吐出去，站点会把后半截当成新参数 —— 这是 URL 注入的最低级形式。
     */
    @Test
    fun `和号编码为百分号26`() {
        val url = SiteCatalog.searchUrl(siteOf("arxiv"), "a&b")
        assertEquals("https://arxiv.org/search/?query=a%26b&searchtype=all", url)
        // 模板自带的 &searchtype 必须保持原样，只编码关键词
        assertTrue(url.endsWith("&searchtype=all"))
    }

    /** 混合场景：中文 + 空格 + `&` 同时出现。 */
    @Test
    fun `中文空格和和号混合编码`() {
        val url = SiteCatalog.searchUrl(siteOf("arxiv"), "机器学习 & 深度学习")
        assertEquals(
            "https://arxiv.org/search/?query=%E6%9C%BA%E5%99%A8%E5%AD%A6%E4%B9%A0%20%26%20%E6%B7%B1%E5%BA%A6%E5%AD%A6%E4%B9%A0&searchtype=all",
            url,
        )
        assertFalse("关键词里的 & 没有被编码：$url", url.contains(" & "))
    }

    /** 前后空白会被 trim 掉，正常关键词不因为多打一个空格就变成 `%20keyword`。 */
    @Test
    fun `关键词前后空白被裁掉`() {
        assertEquals(
            "https://arxiv.org/search/?query=kaoyan&searchtype=all",
            SiteCatalog.searchUrl(siteOf("arxiv"), "  kaoyan  "),
        )
    }

    /** 裁决 D5：空查询回落 homepage，而不是拼一个空参数。 */
    @Test
    fun `空查询回落到首页`() {
        val arxiv = siteOf("arxiv")
        assertEquals(arxiv.homepage, SiteCatalog.searchUrl(arxiv, ""))
    }

    /** 只有空白字符的查询同样回落 homepage（trim 之后为空）。 */
    @Test
    fun `纯空白查询回落到首页`() {
        val cnki = siteOf("cnki")
        assertEquals(cnki.homepage, SiteCatalog.searchUrl(cnki, "   "))
        assertEquals(cnki.homepage, SiteCatalog.searchUrl(cnki, "\t\n"))
    }

    /** 模板里若还有第二处 `{q}`，也应全部替换 —— 不留裸占位符给浏览器。 */
    @Test
    fun `替换后不残留占位符`() {
        all.forEach { site ->
            val url = SiteCatalog.searchUrl(site, "测试 关键词")
            assertFalse("${site.id} 的 URL 里还有未替换的 {q}：$url", url.contains("{q}"))
        }
    }

    /** 全目录逐条跑一遍：任何站点都不能在编码这一层崩掉或拼出裸中文。 */
    @Test
    fun `全目录关键词都能拼出无裸中文的URL`() {
        all.forEach { site ->
            val url = SiteCatalog.searchUrl(site, "计算机 考研&复试")
            assertFalse("${site.id} 的 URL 里残留了裸空格：$url", url.contains(" "))
            assertFalse("${site.id} 的 URL 里残留了裸中文：$url", url.contains("计算机"))
            assertTrue("${site.id} 的 URL 不是 http(s)：$url", url.startsWith("http"))
        }
    }

    // ------------------------------------------------------------ 其他查询辅助

    /** `byId` 命中与未命中。 */
    @Test
    fun `按id查站点`() {
        assertEquals("中国知网 CNKI", SiteCatalog.byId("cnki")?.name)
        assertEquals(null, SiteCatalog.byId("no-such-site"))
    }

    /** `byCategories` 只返回指定分类，且保持目录里的原始顺序。 */
    @Test
    fun `按分类过滤保持原始顺序`() {
        val thesis = SiteCatalog.byCategories(setOf(SiteCategory.THESIS))
        assertTrue(thesis.isNotEmpty())
        assertTrue(thesis.all { it.category == SiteCategory.THESIS })
        // 过滤结果的相对顺序必须和 all 一致
        val idsInAll = all.map { it.id }
        assertEquals(thesis.map { it.id }, idsInAll.filter { id -> thesis.any { it.id == id } })
    }

    /** 空分类集合视为「不过滤」，返回全量 —— 否则子页签会空白。 */
    @Test
    fun `空分类集合返回全量`() {
        assertEquals(all, SiteCatalog.byCategories(emptySet()))
    }
}
