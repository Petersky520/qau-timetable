package cn.edu.qau.timetable.core.kaoyan

import java.net.URLEncoder

/**
 * 内置的论文 / 资料站点目录。
 *
 * ## 为什么内置而不是联网拉
 * 这份目录是「工具索引」，不是内容：它的价值在于断网、翻墙失败、
 * 学校网关抽风时仍然能列出该去哪儿找。做成远端配置的话，
 * 最需要它的时候恰好拿不到。
 *
 * ## 为什么 searchTemplate 保留各站自己的参数名
 * 每个站点的检索参数各不相同（知网 `kw=`、万方 `q=`、B站 `keyword=`），
 * 统一成一个 `?q=` 会在大多数站点上直接 404。所以模板里只留 `{q}` 占位，
 * 由 [searchUrl] 在调用侧替换并编码。
 *
 * 域名均为站点真实域名；只保证「拼出的 URL 形态正确」，
 * 不保证站点自身的检索页长期不变 —— 那属于站点方的事。
 */
object SiteCatalog {

    // ---- 论文检索 ----

    /** 中国知网：中文文献绕不开的一站，研究生学位论文库尤其重要。 */
    private val CNKI = ResearchSite(
        id = "cnki",
        name = "中国知网 CNKI",
        category = SiteCategory.THESIS,
        homepage = "https://www.cnki.net/",
        searchTemplate = "https://kns.cnki.net/kns8s/defaultresult/index?kw={q}",
        description = "中文期刊、硕博学位论文、会议论文的主力库，查国内研究现状首选。",
    )

    private val WANFANG = ResearchSite(
        id = "wanfang",
        name = "万方数据",
        category = SiteCategory.THESIS,
        homepage = "https://www.wanfangdata.com.cn/",
        searchTemplate = "https://s.wanfangdata.com.cn/paper?q={q}",
        description = "期刊与学位论文覆盖面广，部分高校镜像比知网更快。",
    )

    private val CQVIP = ResearchSite(
        id = "cqvip",
        name = "维普期刊",
        category = SiteCategory.THESIS,
        homepage = "https://www.cqvip.com/",
        searchTemplate = "https://qikan.cqvip.com/Qikan/Search/Index?key={q}",
        description = "中文科技期刊收录较全，适合补知网没订到的刊。",
    )

    private val GOOGLE_SCHOLAR = ResearchSite(
        id = "google-scholar",
        name = "Google Scholar",
        category = SiteCategory.THESIS,
        homepage = "https://scholar.google.com/",
        searchTemplate = "https://scholar.google.com/scholar?q={q}",
        description = "跨库检索英文文献，引用次数与被引关系最好用；访问可能需要网络条件。",
    )

    private val SEMANTIC_SCHOLAR = ResearchSite(
        id = "semantic-scholar",
        name = "Semantic Scholar",
        category = SiteCategory.THESIS,
        homepage = "https://www.semanticscholar.org/",
        searchTemplate = "https://www.semanticscholar.org/search?q={q}",
        description = "AI 辅助的文献库，能看引用上下文，筛选综述类文章很方便。",
    )

    private val ARXIV = ResearchSite(
        id = "arxiv",
        name = "arXiv",
        category = SiteCategory.THESIS,
        homepage = "https://arxiv.org/",
        searchTemplate = "https://arxiv.org/search/?query={q}&searchtype=all",
        description = "预印本平台，计算机、物理、数学的最新成果基本都先发在这里。",
    )

    private val PUBMED = ResearchSite(
        id = "pubmed",
        name = "PubMed",
        category = SiteCategory.THESIS,
        homepage = "https://pubmed.ncbi.nlm.nih.gov/",
        searchTemplate = "https://pubmed.ncbi.nlm.nih.gov/?term={q}",
        description = "生物医学文献主库，兽医、食品、植保方向查外文常来。",
    )

    private val DOAJ = ResearchSite(
        id = "doaj",
        name = "DOAJ 开放获取期刊",
        category = SiteCategory.THESIS,
        homepage = "https://doaj.org/",
        searchTemplate = "https://doaj.org/search/articles?source=%7B%22query%22%3A%7B%22query_string%22%3A%7B%22query%22%3A%22{q}%22%7D%7D%7D",
        description = "开放获取期刊目录，文章可免费下载全文，适合找无订阅压力的参考资料。",
    )

    private val CORE = ResearchSite(
        id = "core",
        name = "CORE",
        category = SiteCategory.THESIS,
        homepage = "https://core.ac.uk/",
        searchTemplate = "https://core.ac.uk/search?q={q}",
        description = "聚合全球机构知识库与开放论文，找学位论文原文的成功率不错。",
    )

    private val BAIDU_XUESHU = ResearchSite(
        id = "baidu-xueshu",
        name = "百度学术",
        category = SiteCategory.THESIS,
        homepage = "https://xueshu.baidu.com/",
        searchTemplate = "https://xueshu.baidu.com/s?wd={q}",
        description = "中英文一站检索，能直接看到「免费下载」入口，做初步摸底很快。",
    )

    // ---- 专利 / 标准 ----

    private val CNIPA = ResearchSite(
        id = "cnipa",
        name = "国家知识产权局",
        category = SiteCategory.PATENT,
        homepage = "https://www.cnipa.gov.cn/",
        searchTemplate = "https://pss-system.cponline.cnipa.gov.cn/conventionalSearch?searchWord={q}",
        description = "官方专利检索入口，做技术现状与专利壁垒分析用。",
    )

    private val SOOPAT = ResearchSite(
        id = "soopat",
        name = "SooPAT 专利搜索",
        category = SiteCategory.PATENT,
        homepage = "http://www.soopat.com/",
        searchTemplate = "http://www.soopat.com/Home/Result?SearchWord={q}",
        description = "中文专利检索引擎，可按申请人、法律状态筛选，界面比官方库轻。",
    )

    private val OPEN_STD = ResearchSite(
        id = "openstd",
        name = "国家标准全文公开系统",
        category = SiteCategory.PATENT,
        homepage = "https://openstd.samr.gov.cn/",
        searchTemplate = "https://openstd.samr.gov.cn/bzgk/gb/std_list?p.p1=0&p.p90=circulation_date&p.p91=desc&p.p2={q}",
        description = "强制性国标可在线阅读全文，写实验方法引用标准时必备。",
    )

    // ---- 学习资料 ----

    private val ICourse163 = ResearchSite(
        id = "icourse163",
        name = "中国大学 MOOC",
        category = SiteCategory.STUDY,
        homepage = "https://www.icourse163.org/",
        searchTemplate = "https://www.icourse163.org/search.htm?search={q}",
        description = "国内高校开设的系统课程，数学、英语、专业课打基础用它。",
    )

    private val XUETANGX = ResearchSite(
        id = "xuetangx",
        name = "学堂在线",
        category = SiteCategory.STUDY,
        homepage = "https://www.xuetangx.com/",
        searchTemplate = "https://www.xuetangx.com/search?query={q}",
        description = "清华发起的慕课平台，考研数学与专业课的公开课较多。",
    )

    private val SMARTEDU = ResearchSite(
        id = "smartedu-higher",
        name = "国家高等教育智慧教育平台",
        category = SiteCategory.STUDY,
        homepage = "https://higher.smartedu.cn/",
        searchTemplate = "https://higher.smartedu.cn/search?keyword={q}",
        description = "教育部官方汇集的高校课程资源，免费、无广告，专业课录像比较全。",
    )

    private val KAOYAN_BANG = ResearchSite(
        id = "kaoyanbang",
        name = "考研帮",
        category = SiteCategory.STUDY,
        homepage = "https://www.kaoyan.com/",
        searchTemplate = "https://www.kaoyan.com/search/?q={q}",
        description = "院校报录比、经验贴、真题回忆的集散地，看「过来人怎么说」。",
    )

    private val BILIBILI = ResearchSite(
        id = "bilibili",
        name = "哔哩哔哩搜索",
        category = SiteCategory.STUDY,
        homepage = "https://www.bilibili.com/",
        searchTemplate = "https://search.bilibili.com/all?keyword={q}",
        description = "免费网课的实际入口，搜「专业课代码 + 年份」常能找到真题讲解。",
    )

    private val CHAOXING = ResearchSite(
        id = "chaoxing",
        name = "超星尔雅 / 学习通",
        category = SiteCategory.STUDY,
        homepage = "https://erya.mooc.chaoxing.com/",
        searchTemplate = "https://mooc1.chaoxing.com/visit/searchmore?searchWord={q}",
        description = "很多学校买了它的通识课与电子书，用校园网登录可直接看。",
    )

    private val KAOYAN_CN = ResearchSite(
        id = "chinakaoyan",
        name = "中国考研网",
        category = SiteCategory.STUDY,
        homepage = "https://www.chinakaoyan.com/",
        searchTemplate = "https://www.chinakaoyan.com/search/?keyword={q}",
        description = "招生简章、专业目录、调剂信息汇总，信息更新偏快但需与官网核对。",
    )

    // ---- 公开课 ----

    private val MIT_OCW = ResearchSite(
        id = "mit-ocw",
        name = "MIT OpenCourseWare",
        category = SiteCategory.COURSE,
        homepage = "https://ocw.mit.edu/",
        searchTemplate = "https://ocw.mit.edu/search/?q={q}",
        description = "MIT 课程讲义、作业与考试原题全公开，英文教材配套学习很对口。",
    )

    private val COURSERA = ResearchSite(
        id = "coursera",
        name = "Coursera",
        category = SiteCategory.COURSE,
        homepage = "https://www.coursera.org/",
        searchTemplate = "https://www.coursera.org/search?query={q}",
        description = "英文系统课，可申请助学金免费学；部分课程带中文字幕。",
    )

    private val EDX = ResearchSite(
        id = "edx",
        name = "edX",
        category = SiteCategory.COURSE,
        homepage = "https://www.edx.org/",
        searchTemplate = "https://www.edx.org/search?q={q}",
        description = "哈佛、MIT 等校课程，免费旁听模式足够应付专业课补基础。",
    )

    private val NETEASE_OPEN = ResearchSite(
        id = "netease-open",
        name = "网易公开课",
        category = SiteCategory.COURSE,
        homepage = "https://open.163.com/",
        searchTemplate = "https://open.163.com/search?keyword={q}",
        description = "带中文字幕的公开课合集，TED 与名校讲座搬运较多，门槛最低。",
    )

    // ---- 数据 / 政策 ----

    private val YZ_CHSI = ResearchSite(
        id = "yz-chsi",
        name = "中国研究生招生信息网",
        category = SiteCategory.DATA,
        homepage = "https://yz.chsi.com.cn/",
        searchTemplate = "https://yz.chsi.com.cn/zsml/queryAction.do?dwmc={q}",
        description = "研招网官方专业目录与报名入口，分数线、招生计划以这里为准。",
    )

    private val CDGDC = ResearchSite(
        id = "cdgdc",
        name = "教育部学位与研究生教育发展中心",
        category = SiteCategory.DATA,
        homepage = "https://www.cdgdc.edu.cn/",
        searchTemplate = "https://www.cdgdc.edu.cn/search?keyword={q}",
        description = "学科评估结果发布方，判断一个专业在该校强不强就看这里。",
    )

    private val NEEA = ResearchSite(
        id = "neea",
        name = "中国教育考试网",
        category = SiteCategory.DATA,
        homepage = "https://www.neea.edu.cn/",
        searchTemplate = "https://www.neea.edu.cn/search?keyword={q}",
        description = "四六级、教资等考试官方信息，复试英语与证书类要求查这里。",
    )

    private val SDZK = ResearchSite(
        id = "sdzk",
        name = "山东省教育招生考试院",
        category = SiteCategory.DATA,
        homepage = "https://www.sdzk.cn/",
        searchTemplate = "https://www.sdzk.cn/search/?keyword={q}",
        description = "本省报考点、现场确认与调剂政策的第一手通知来源。",
    )

    /** 全量目录。顺序即 UI 展示顺序：先论文，再资料，最后数据。 */
    val all: List<ResearchSite> = listOf(
        // 论文检索
        CNKI,
        WANFANG,
        CQVIP,
        GOOGLE_SCHOLAR,
        SEMANTIC_SCHOLAR,
        ARXIV,
        PUBMED,
        DOAJ,
        CORE,
        BAIDU_XUESHU,
        // 专利 / 标准
        CNIPA,
        SOOPAT,
        OPEN_STD,
        // 学习资料
        ICourse163,
        XUETANGX,
        SMARTEDU,
        KAOYAN_BANG,
        BILIBILI,
        CHAOXING,
        KAOYAN_CN,
        // 公开课
        MIT_OCW,
        COURSERA,
        EDX,
        NETEASE_OPEN,
        // 数据 / 政策
        YZ_CHSI,
        CDGDC,
        NEEA,
        SDZK,
    )

    /** 按分类过滤，保持 [all] 里的原始顺序。 */
    fun byCategories(categories: Set<SiteCategory>): List<ResearchSite> =
        if (categories.isEmpty()) all else all.filter { it.category in categories }

    fun byId(id: String): ResearchSite? = all.firstOrNull { it.id == id }

    /**
     * 把关键词填进站点模板。
     *
     * 两个细节必须一起做，缺一个就会拼出坏 URL：
     * 1. `URLEncoder` 用的是 `application/x-www-form-urlencoded`，
     *    空格会被编成 `+` —— 那是**表单**语义，放在路径/查询串里
     *    会被站点当成字面加号，所以这里换回 `%20`。
     * 2. 空关键词不做替换：模板里的 `{q}` 原样留着，用户点进去是站点的
     *    检索页而不是一个 `q=` 空查询。这种情况下直接返回首页更合理。
     */
    fun searchUrl(site: ResearchSite, query: String): String {
        val keyword = query.trim()
        if (keyword.isEmpty()) return site.homepage
        val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        return site.searchTemplate.replace("{q}", encoded)
    }
}
