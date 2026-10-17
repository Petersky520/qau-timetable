package cn.edu.qau.timetable.ui.kaoyan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.core.kaoyan.ResearchSite
import cn.edu.qau.timetable.core.kaoyan.SiteCatalog
import cn.edu.qau.timetable.core.kaoyan.SiteCategory
import cn.edu.qau.timetable.ui.glass.LocalFloatingBarReserve
import cn.edu.qau.timetable.ui.motion.rememberEntranceWindow
import cn.edu.qau.timetable.ui.motion.staggeredAppear

/**
 * 空状态。
 *
 * 和 `ui/SimpleScreens.kt` 里那个 private 的同名函数是同一套写法，但这里刻意
 * **再写一份**而不是把原来那个改成 public：那个函数的样式属于它自己的页面，
 * 一旦公开，别的模块就会开始往里加参数，最后谁都不敢动它。
 */
@Composable
private fun EmptyState(title: String, hint: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .staggeredAppear(0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 论文 / 资料站点页。
 *
 * 同一份代码服务两个子页，靠 [categories] 过滤 —— 两页的交互（搜索、开首页）
 * 完全一样，差别只在展示哪些分类。写成两个函数只会让同一处 bug 修两遍。
 */
@Composable
fun KaoyanSiteScreen(
    vm: KaoyanViewModel,
    categories: Set<SiteCategory>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sites = remember(categories) { SiteCatalog.byCategories(categories) }

    // 全局搜索：一次输入，列出该分类下所有站点的搜索链接让用户挑一个打开。
    // 这是这页的核心交互 —— 同一个关键词在知网/万方/Google Scholar 各搜一遍，
    // 比来回切浏览器、逐个点开站点再粘贴关键词省事得多。
    var globalQuery by rememberSaveable { mutableStateOf("") }
    var showGlobal by rememberSaveable { mutableStateOf(false) }

    // 单站搜索：哪个站点、关键词是什么
    var searchTarget by remember { mutableStateOf<ResearchSite?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val entrance = rememberEntranceWindow(sites.size)

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = globalQuery,
                onValueChange = { globalQuery = it },
                label = { Text("一次输入，多站直达") },
                placeholder = { Text("如 玉米抗倒伏") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                if (globalQuery.isBlank()) {
                    vm.toastHint("先输入关键词，再把同一份关键词发到各个站点")
                } else {
                    showGlobal = true
                }
            }) { Text("搜索") }
        }

        Text(
            "共 ${sites.size} 个站点。App 只负责拼检索链接，正文仍由浏览器打开，" +
                "需要机构权限的库请先在校内网 / VPN 下登录。",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )

        if (sites.isEmpty()) {
            EmptyState("这个分类下没有站点", "内置目录里没有匹配的分类，检查一下分组设置。")
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 4.dp,
                end = 12.dp,
                // 悬浮底栏会盖住内容，这里必须让出那一段，否则最后一条永远滚不出来
                bottom = 12.dp + LocalFloatingBarReserve.current,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sites, key = { _, s -> s.id }) { index, site ->
                SiteRow(
                    site = site,
                    onSearch = {
                        searchTarget = site
                        // 单站搜索预填全局关键词：大概率用户就是想在这个站再搜一遍
                        searchQuery = globalQuery
                    },
                    onOpenHome = { openUrl(context, site.homepage, vm) },
                    modifier = Modifier.staggeredAppear(index, animate = entrance),
                )
            }
        }
    }

    // ------------------------------------------------------------ 全局搜索弹窗
    if (showGlobal) {
        MultiSiteDialog(
            sites = sites,
            query = globalQuery,
            onDismiss = { showGlobal = false },
            onPick = { site ->
                showGlobal = false
                openUrl(context, SiteCatalog.searchUrl(site, globalQuery), vm)
            },
        )
    }

    // ------------------------------------------------------------ 单站搜索弹窗
    searchTarget?.let { site ->
        AlertDialog(
            onDismissRequest = { searchTarget = null },
            title = { Text("在 ${site.name} 检索") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "留空会直接打开首页，而不是一个空结果的检索页。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = SiteCatalog.searchUrl(site, searchQuery)
                    searchTarget = null
                    openUrl(context, url, vm)
                }) { Text("打开") }
            },
            dismissButton = {
                TextButton(onClick = { searchTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SiteRow(
    site: ResearchSite,
    onSearch: () -> Unit,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    site.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    site.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(site.description, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSearch) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("搜索")
                }
                TextButton(onClick = onOpenHome) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("打开首页")
                }
            }
        }
    }
}

/**
 * 多站直达：一个关键词，该分类下每个站点各一条链接。
 *
 * 用 `heightIn(max = ...)` + `verticalScroll` 而不是 LazyColumn：
 * 弹窗里的列表要和标题、按钮一起滚，套一层 LazyColumn 会让弹窗高度塌成 0
 * （Lazy 组件在无界约束下量不出高度）。
 */
@Composable
private fun MultiSiteDialog(
    sites: List<ResearchSite>,
    query: String,
    onDismiss: () -> Unit,
    onPick: (ResearchSite) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用「${query.trim()}」检索") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                sites.forEach { site ->
                    TextButton(
                        onClick = { onPick(site) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                site.name,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Start,
                            )
                            Text(
                                site.category.label,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 交给浏览器打开。
 *
 * 不内置 WebView：这些站点大多有自己的登录态、反爬和全文下载限制，
 * 塞进 App 的 WebView 反而更难用（下载、验证码、机构 IP 校验都会失效）。
 * 打不开时把原因回给用户，而不是静默失败。
 */
private fun openUrl(context: Context, url: String, vm: KaoyanViewModel) {
    if (url.isBlank()) {
        vm.toastHint("这个站点没有配置地址")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(intent) }.isSuccess
    if (!ok) vm.toastHint("没有能打开这个链接的应用")
}

/**
 * 页面级的提示出口。
 *
 * 站点页拿不到 `MainViewModel`，但提示仍然要出现在全局 Snackbar 上 ——
 * 所以走 ViewModel 的 toast 通道，由 `KaoyanScreen` 统一转发。
 */
private fun KaoyanViewModel.toastHint(message: String) = notify(message)
