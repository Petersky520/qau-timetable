package cn.edu.qau.timetable.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.qau.timetable.BuildConfig
import cn.edu.qau.timetable.data.qz.QzEndpoints
import cn.edu.qau.timetable.data.qz.QzJs
import cn.edu.qau.timetable.data.qz.QzJson
import cn.edu.qau.timetable.data.qz.QzTableParser
import org.json.JSONObject

/** JS -> Kotlin 的桥。回调发生在 Binder 线程，且 json 可能为 null（JS 传了 undefined）。 */
private class QauBridge(private val onJson: (String?) -> Unit) {
    @JavascriptInterface
    fun post(json: String?) {
        onJson(json)
    }
}

/**
 * WebView 没有 isDestroyed()，所以自己记一个销毁标志 ——
 * 对已 destroy() 的 WebView 调 evaluateJavascript / loadUrl 会直接崩。
 */
private class WebHolder {
    @Volatile
    var web: WebView? = null

    @Volatile
    var destroyed: Boolean = false
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SyncScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val appSettings by vm.settings.collectAsState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val webHolder = remember { WebHolder() }
    val clipboard = LocalClipboardManager.current

    var target by remember { mutableStateOf(QzEndpoints.Target.TIMETABLE) }
    var status by remember { mutableStateOf("请在教务网页中选择账号登录，登录后展开下方工具抓取。") }
    val context = LocalContext.current
    val inputCompat = remember(context) {
        context.assets.open("web/login-input-compat.js").bufferedReader().use { it.readText() }
    }
    var diagnostics by remember { mutableStateOf("") }
    var pageHtml by remember { mutableStateOf("") }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var lastRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var lastSpans by remember { mutableStateOf<List<List<Int>>>(emptyList()) }

    // 抓取工具登录之后才用得到，默认收起，把竖向空间让给网页
    var toolsExpanded by remember { mutableStateOf(false) }

    fun urlFor(t: QzEndpoints.Target): String = when (t) {
        QzEndpoints.Target.TIMETABLE -> appSettings.kbUrl
        QzEndpoints.Target.EXAMS -> appSettings.examUrl
        QzEndpoints.Target.GRADES -> appSettings.gradeUrl
    }

    /**
     * 所有对 WebView 的调用都走这里：统一在主线程、统一兜异常。
     * 之前直接调 evaluateJavascript / loadUrl，WebView 已销毁或还没加载完时
     * 会抛异常直接闪退。
     */
    fun safeEval(js: String, label: String) {
        val wv = webHolder.web
        if (wv == null || webHolder.destroyed) {
            status = "⚠️ 网页还没准备好"
            return
        }
        mainHandler.post {
            try {
                if (!webHolder.destroyed) {
                    wv.evaluateJavascript(js, null)
                } else {
                    status = "⚠️ 网页已关闭，请重新进入同步页"
                }
            } catch (t: Throwable) {
                status = "⚠️ $label 失败：${t.javaClass.simpleName}: ${t.message}"
            }
        }
    }

    fun safeLoad(url: String) {
        val wv = webHolder.web
        if (wv == null || webHolder.destroyed) {
            status = "⚠️ 网页还没准备好"
            return
        }
        try {
            if (url.isBlank()) {
                status = "⚠️ 抓取地址为空，请到「设置」里填写"
                return
            }
            wv.loadUrl(url)
        } catch (t: Throwable) {
            status = "⚠️ 打开页面失败：${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun installInputCompat(wv: WebView) {
        if (webHolder.destroyed) return
        val host = android.net.Uri.parse(wv.url ?: "").host.orEmpty().lowercase()
        if (host == "qau.edu.cn" || host.endsWith(".qau.edu.cn")) {
            wv.evaluateJavascript(inputCompat, null)
        }
    }

    fun onPageReady(wv: WebView) {
        try {
            if (webHolder.destroyed) return
            installInputCompat(wv)
            wv.evaluateJavascript(QzJs.EXTRACT_FN, null)
            wv.evaluateJavascript(QzJs.FIND_LINKS_FN, null)
            val kind = pendingKind
            if (kind != null) {
                pendingKind = null
                status = "页面加载完成，正在抓取…"
                wv.evaluateJavascript(QzJs.extractCall(kind), null)
            }
        } catch (t: Throwable) {
            status = "⚠️ 注入脚本失败：${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun deliver(raw: String?) {
        if (raw.isNullOrBlank()) return
        mainHandler.post {
            try {
                val obj = runCatching { JSONObject(raw) }.getOrNull()
                when (obj?.optString("kind")) {
                    "diag" -> {
                        diagnostics = buildString {
                            append("页面：").append(obj.optString("url")).append('\n')
                            append("方向：html=").append(obj.optString("htmlDir"))
                            append("  body=").append(obj.optString("bodyDir"))
                            append("  document.dir=").append(obj.optString("docDir")).append('\n')
                            append("lang=").append(obj.optString("lang"))
                            append("  navigator=").append(obj.optString("navLang")).append('\n')
                            val arr = obj.optJSONArray("inputs")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val o = arr.optJSONObject(i) ?: continue
                                    append("输入框[").append(i).append("] type=").append(o.optString("type"))
                                    append(" dir=").append(o.optString("dir"))
                                    append(" unicode-bidi=").append(o.optString("bidi")).append('\n')
                                }
                            }

                            // 最关键的一步：把输入框里的**真实值**打出来。
                            // 值本身反了 -> 输入法插入位置错乱；值对了只是显示反 -> bidi 问题。
                            val vUn = if (obj.isNull("valUn")) "(页面里没有 #un)" else obj.optString("valUn")
                            val vCode = if (obj.isNull("valCode")) "(页面里没有 #code)" else obj.optString("valCode")
                            append("—— 输入框真实值（判据）——\n")
                            append("#un   = ").append(vUn).append('\n')
                            append("#code = ").append(vCode).append('\n')
                            append("#pd   长度 = ").append(obj.optInt("lenPd", -1)).append('\n')
                            append("判断：上面 #un 若与你实际敲进去的一致，说明只是显示被重排（bidi）；\n")
                            append("      若本身就是反的，说明是输入法插入位置错乱。\n")
                        }
                        status = "已采集输入方向诊断（见下方，可长按复制）"
                    }

                    "html" -> {
                        pageHtml = obj.optString("html")
                        diagnostics = "已导出当前页面 HTML（${pageHtml.length} 字符），见下方"
                        status = "请长按下面的 HTML 复制后发给开发者"
                    }

                    else -> {
                        val payload = QzJson.parse(raw)
                        if (!payload.ok) {
                            status = "❌ 抓取失败：${payload.error}"
                            return@post
                        }
                        diagnostics = "页面：${payload.url}\n表格：${payload.rows.size} 行 × " +
                            "${payload.headers.size} 列\nrowspans：${payload.rowspans.size} 行\n" +
                            "表头：${payload.headers.joinToString(" | ")}\n" +
                            QzTableParser.describe(payload.rows)
                        lastRows = payload.rows
                        lastSpans = payload.rowspans
                        status = "已抓取到 ${payload.rows.size} 行，正在导入…"
                        vm.ingest(payload) { ok, msg ->
                            status = if (ok) "✅ $msg" else "⚠️ $msg"
                        }
                    }
                }
            } catch (t: Throwable) {
                status = "⚠️ 处理抓取结果失败：${t.javaClass.simpleName}: ${t.message}"
            }
        }
    }

    fun jumpAndFetch(t: QzEndpoints.Target) {
        pendingKind = t.key
        status = "正在跳转到「${t.label}」页面…"
        diagnostics = "目标地址：${urlFor(t)}"
        pageHtml = ""
        safeLoad(urlFor(t))
    }

    fun fetchCurrent(t: QzEndpoints.Target) {
        status = "正在从当前页面抓取「${t.label}」…"
        pageHtml = ""
        safeEval(QzJs.EXTRACT_FN, "注入抽取脚本")
        safeEval(QzJs.FIND_LINKS_FN, "注入查找脚本")
        safeEval(QzJs.extractCall(t.key), "抓取")
    }

    fun diagnose() {
        status = "正在采集输入方向诊断…"
        safeEval(QzJs.DIAG_FN, "诊断")
    }

    fun exportHtml() {
        status = "正在导出当前页面 HTML…"
        safeEval(QzJs.PAGE_HTML_FN, "导出 HTML")
    }

    Column(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    // ---- viewport：与浏览器对齐 ----
                    //
                    // 页面的 viewport meta 是 width=device-width, initial-scale=1。
                    // 不设 useWideViewPort 时 WebView 会忽略这个 meta，按 980px 宽
                    // 布局后再整体缩放塞进控件宽度。设上是为了让布局与浏览器一致。
                    //
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    // 与浏览器对齐：不改动自动填充行为。
                    // （之前显式设 IMPORTANT_FOR_AUTOFILL_NO 反而制造了差异）
                    settings.javaScriptCanOpenWindowsAutomatically = true

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    addJavascriptInterface(QauBridge { json -> deliver(json) }, "QauBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageCommitVisible(view: WebView, url: String) {
                            // Install as soon as the login document is visible, even if images are still loading.
                            installInputCompat(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            // 延后一拍：onPageFinished 有时会在 layout 阶段回调，
                            // 此时直接写 Compose 状态不安全。
                            mainHandler.post { onPageReady(view) }
                        }
                    }
                    loadUrl(QzEndpoints.HOME)
                    webHolder.web = this
                }
            },
        )

        // ------------------------------------------------ ③ 抓取工具（登录之后用）
        Card(Modifier.fillMaxWidth().padding(10.dp)) {
            Column(
                Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { toolsExpanded = !toolsExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (toolsExpanded) "收起抓取工具" else "抓取工具（登录后展开）") }

                if (toolsExpanded) {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    if (diagnostics.isNotEmpty()) {
                        SelectionContainer { Text(diagnostics, style = MaterialTheme.typography.labelSmall) }
                    }
                    Text("抓取目标", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QzEndpoints.Target.entries.forEach { t ->
                            AssistChip(
                                onClick = { target = t },
                                label = { Text(t.label) },
                                leadingIcon = if (target == t) {
                                    { Text("✓") }
                                } else null,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { jumpAndFetch(target) }, modifier = Modifier.weight(1f)) {
                            Text("跳转并抓取")
                        }
                        OutlinedButton(onClick = { fetchCurrent(target) }, modifier = Modifier.weight(1f)) {
                            Text("抓当前页")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { diagnose() }, modifier = Modifier.weight(1f)) {
                            Text("诊断输入方向")
                        }
                        OutlinedButton(onClick = { exportHtml() }, modifier = Modifier.weight(1f)) {
                            Text("导出页面HTML")
                        }
                    }

                    // 抓到的原始表格直接丢进剪贴板 —— 反馈问题时粘一下就行
                    if (lastRows.isNotEmpty()) {
                        Button(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(QzTableParser.dump(lastRows, lastSpans))
                                )
                                status = "已把抓到的原始表格复制到剪贴板，粘贴发出来即可"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("复制抓取结果（反馈用）") }
                    }

                    if (pageHtml.isNotEmpty()) {
                        Text("页面 HTML（长按可复制）", style = MaterialTheme.typography.labelSmall)
                        SelectionContainer {
                            Text(
                                pageHtml,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .horizontalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(6.dp),
                            )
                        }
                    }

                    Text(
                        "提示：如果自动跳转打不开课表，请手动在下面的网页里点到课表页面，" +
                            "再点「抓当前页」。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webHolder.destroyed = true
            val wv = webHolder.web
            if (wv != null) {
                runCatching { wv.removeJavascriptInterface("QauBridge") }
                runCatching { wv.stopLoading() }
                runCatching { wv.destroy() }
            }
            webHolder.web = null
        }
    }
}
