package cn.edu.qau.timetable.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.qau.timetable.data.qz.QzEndpoints
import cn.edu.qau.timetable.data.qz.QzEndpoints.Target
import cn.edu.qau.timetable.data.qz.QzJs
import cn.edu.qau.timetable.data.qz.QzJson
import cn.edu.qau.timetable.ui.motion.QauMotion
import cn.edu.qau.timetable.ui.motion.motionOf
import kotlinx.coroutines.delay
import org.json.JSONTokener

private data class SyncRequest(val target: Target, val url: Uri)

private fun isSchoolPage(uri: Uri): Boolean {
    val host = uri.host.orEmpty().lowercase()
    return uri.scheme in listOf("http", "https") && (host == "qau.edu.cn" || host.endsWith(".qau.edu.cn"))
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SyncScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    initialTarget: Target = Target.TIMETABLE,
) {
    val appSettings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inputCompat = remember(context) {
        context.assets.open("web/login-input-compat.js").bufferedReader().use { it.readText() }
    }
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var request by remember { mutableStateOf<SyncRequest?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var pageFailed by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("在教务页面登录后，即可同步所选数据。") }

    fun importPage(view: WebView, selected: Target, pageUrl: String) {
        importing = true
        status = "正在读取${selected.label}…"
        val script = QzJs.EXTRACT_FN + """
            (function() {
                if (document.querySelector('input[type="password"], #pd')) return 'login';
                return window.__qauExtract('${selected.key}');
            })();
        """.trimIndent()
        view.evaluateJavascript(script) { encoded ->
            if (web !== view) return@evaluateJavascript
            if (view.url != pageUrl || pageLoading || pageFailed) {
                importing = false
                status = "网页已切换，请在加载完成后重新同步。"
                return@evaluateJavascript
            }
            val raw = runCatching { JSONTokener(encoded.orEmpty()).nextValue() as? String }.getOrNull()
            if (raw == "login") {
                importing = false
                status = "请先登录教务系统，再点同步。"
                return@evaluateJavascript
            }
            val payload = QzJson.parse(raw)
            if (!payload.ok || payload.kind != selected.key) {
                importing = false
                status = "未找到${selected.label}表格。可在网页中手动打开对应页面，再从菜单导入当前页。"
                return@evaluateJavascript
            }
            vm.ingest(payload) { ok, message ->
                if (web === view) {
                    importing = false
                    status = if (ok) "${selected.label}同步完成 · $message" else message
                }
            }
        }
    }

    fun load(url: String) {
        pageFailed = false
        pageLoading = true
        web?.loadUrl(url)
    }

    fun sync() {
        val view = web ?: return
        val current = Uri.parse(view.url.orEmpty())
        val saved = when (target) {
            Target.TIMETABLE -> appSettings.kbUrl
            Target.GRADES -> appSettings.gradeUrl
        }
        val uri = Uri.parse(saved).takeIf(::isSchoolPage) ?: Uri.parse(QzEndpoints.defaultUrl(target))
        if (!pageFailed && current == uri) {
            importPage(view, target, current.toString())
        } else {
            request = SyncRequest(target, uri)
            status = "正在打开${target.label}页面…"
            load(uri.toString())
        }
    }

    LaunchedEffect(pageLoading, request) {
        if (pageLoading) {
            delay(25_000)
            web?.stopLoading()
            request = null
            pageLoading = false
            pageFailed = true
            status = "教务页面加载超时，请重试。"
        }
    }

    LaunchedEffect(importing) {
        if (importing) {
            delay(25_000)
            status = "同步用时较长，请等待当前导入完成。"
        }
    }

    Column(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    isFocusableInTouchMode = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (web !== view) return
                            pageLoading = true
                            pageFailed = false
                        }

                        override fun onPageCommitVisible(view: WebView, url: String) {
                            if (web === view && isSchoolPage(Uri.parse(url))) {
                                view.evaluateJavascript(inputCompat, null)
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (web !== view || view.url != url || pageFailed) return
                            pageLoading = false
                            val location = Uri.parse(url)
                            if (isSchoolPage(location)) view.evaluateJavascript(inputCompat, null)
                            val pending = request ?: return
                            request = null
                            if (isSchoolPage(location) &&
                                location.host.equals(pending.url.host, ignoreCase = true) &&
                                location.path == pending.url.path
                            ) {
                                importPage(view, pending.target, url)
                            } else {
                                status = "请确认已登录教务系统，再同步${pending.target.label}。"
                            }
                        }

                        private fun pageError() {
                            request = null
                            pageLoading = false
                            pageFailed = true
                            status = "教务页面加载失败，请检查网络后重试。"
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (web === view && request.isForMainFrame) pageError()
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (web === view && request.isForMainFrame) pageError()
                        }
                    }
                    web = this
                    loadUrl(QzEndpoints.HOME)
                }
            },
            onRelease = { view ->
                web = null
                view.stopLoading()
                view.destroy()
            },
        )
        val progressLayout = motionOf(QauMotion.LayoutFast)
        AnimatedVisibility(
            visible = pageLoading || importing,
            enter = expandVertically(progressLayout) + fadeIn(motionOf(QauMotion.Effects)),
            exit = shrinkVertically(progressLayout) + fadeOut(motionOf(QauMotion.EffectsFast)),
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Target.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = target == item,
                            onClick = {
                                target = item
                                status = "在教务页面登录后，即可同步所选数据。"
                            },
                            enabled = request == null && !importing,
                            shape = SegmentedButtonDefaults.itemShape(index, Target.entries.size),
                            label = { Text(item.label) },
                        )
                    }
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (pageFailed) load(web?.url?.takeIf(String::isNotBlank) ?: QzEndpoints.HOME)
                            else sync()
                        },
                        enabled = web != null && !pageLoading && !importing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Sync, null, Modifier.padding(end = 8.dp))
                        Text(if (pageFailed) "重新加载" else "同步${target.label}")
                    }
                    Column {
                        IconButton(onClick = { menu = true }, enabled = !importing && !pageLoading) {
                            Icon(Icons.Filled.MoreVert, "更多同步选项")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val current = web?.url
                            DropdownMenuItem(
                                text = { Text("导入当前网页") },
                                enabled = current != null && !pageFailed && isSchoolPage(Uri.parse(current)),
                                onClick = {
                                    menu = false
                                    val view = web
                                    val url = view?.url
                                    if (view != null && url != null && isSchoolPage(Uri.parse(url))) {
                                        importPage(view, target, url)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("返回教务首页") },
                                onClick = {
                                    menu = false
                                    status = "在教务页面登录后，即可同步所选数据。"
                                    load(QzEndpoints.HOME)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
