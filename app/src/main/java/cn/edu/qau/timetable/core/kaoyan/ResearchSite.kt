package cn.edu.qau.timetable.core.kaoyan

/** 站点分类。分组顺序就是 UI 里 chip 的顺序，所以枚举声明顺序有意义。 */
enum class SiteCategory(val label: String) {
    THESIS("论文检索"),
    PATENT("专利 / 标准"),
    STUDY("学习资料"),
    COURSE("公开课"),
    DATA("数据 / 政策"),
    ;

    companion object {
        /**
         * 从持久化值还原分类。
         *
         * 认不出就落到 [THESIS]：它是列表里第一组、条目最多的一组，
         * 落错档最多是多显示几张卡片，不会让站点整条消失。
         */
        fun fromName(name: String?): SiteCategory =
            entries.firstOrNull { it.name == name || it.label == name } ?: THESIS
    }
}

/**
 * 一个外部站点。
 *
 * searchTemplate 里用 `{q}` 占位查询词，App 负责替换并 URL 编码 ——
 * 把编码放在调用侧而不是模板里，站点自己的特殊规则（比如知网用 `kw=`）
 * 就能原样保留。
 *
 * 刻意**不做**站点可用性探测：目录是离线资产，联网失败时照样要能展示、
 * 能拼出 URL 交给浏览器去打开。探测只会让没网的时候整页空白。
 */
data class ResearchSite(
    val id: String,
    val name: String,
    val category: SiteCategory,
    val homepage: String,
    val searchTemplate: String,
    val description: String,
)
