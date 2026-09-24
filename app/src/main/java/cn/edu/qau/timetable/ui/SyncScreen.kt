package cn.edu.qau.timetable.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    var status by remember { mutableStateOf("① 在下方登录青农大教务系统\n② 登录后点「跳转并抓取」") }
    var diagnostics by remember { mutableStateOf("") }
    var pageHtml by remember { mutableStateOf("") }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var lastRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var lastSpans by remember { mutableStateOf<List<List<Int>>>(emptyList()) }

    // 手动填入：用 App 的原生输入框输入，再注入网页，
    // 完全绕开 WebView 的输入法链路（用于规避"输入倒序"）。
    // 默认展开：WebView 的输入法链路在这台机器上有问题，
    // 用 App 原生输入框再注入网页，是当前唯一稳定可用的登录方式。
    var showFill by remember { mutableStateOf(true) }
    var fillU by remember { mutableStateOf("") }
    var fillP by remember { mutableStateOf("") }
    var fillC by remember { mutableStateOf("") }

    fun urlFor(t: QzEndpoints.Target): String = when (t) {
        QzEndpoints.Target.TIMETABLE -> appSettings.kbUrl
        QzEndpoints.Target.EXAMS -> appSettings.examUrl
        QzEndpoints.Target.GRADES -> appSettings.gradeUrl
        QzEndpoints.Target.CLASSROOMS -> appSettings.classroomUrl
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

    fun onPageReady(wv: WebView) {
        try {
            if (webHolder.destroyed) return
            wv.evaluateJavascript(QzJs.EXTRACT_FN, null)
            wv.evaluateJavascript(QzJs.FIND_LINKS_FN, null)
            if (appSettings.forceLtrInput) {
                wv.evaluateJavascript(QzJs.FORCE_LTR_FN, null)
            }
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
                            append("  navigator=").append(obj.optString("navLang"))
                            append("  LTR修复=").append(obj.optBoolean("ltrFixApplied")).append('\n')
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

                    "fill" -> {
                        val gotUn = if (obj.isNull("un")) "(未填)" else obj.optString("un")
                        val gotCode = if (obj.isNull("code")) "(未填)" else obj.optString("code")
                        diagnostics = "回读网页里的真实值（确认注入没被页面改写）：\n" +
                            "  #un   = $gotUn\n" +
                            "  #code = $gotCode\n" +
                            "  #pd   长度 = ${obj.optInt("lenPd", -1)}\n" +
                            "与你在上面输入的一致 → 说明原生输入框这条路是好的，" +
                            "直接点网页上的「登录」即可。"
                        status = "✅ 已填入网页，请点网页上的登录按钮"
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
        if (appSettings.forceLtrInput) safeEval(QzJs.FORCE_LTR_FN, "注入 LTR 修复")
        safeEval(QzJs.extractCall(t.key), "抓取")
    }

    fun diagnose() {
        status = "正在采集输入方向诊断…"
        if (appSettings.forceLtrInput) safeEval(QzJs.FORCE_LTR_FN, "注入 LTR 修复")
        safeEval(QzJs.DIAG_FN, "诊断")
    }

    fun exportHtml() {
        status = "正在导出当前页面 HTML…"
        safeEval(QzJs.PAGE_HTML_FN, "导出 HTML")
    }

    /**
     * 把原生输入框的三个值写进网页的 #un / #pd / #code。
     *
     * 这条路径**完全不经过 WebView 的输入法**，所以如果"输入倒序"是
     * WebView/输入法的锅，这里一定是正常的。
     * 页面在提交时是读 $("#un").val() / $("#pd").val()，
     * 所以直接赋值 + 派发 input/change 事件就够了。
     */
    fun fillNative() {
        if (fillU.isBlank() && fillP.isBlank() && fillC.isBlank()) {
            status = "先在下面把学号/密码/动态码填好"
            return
        }
        val js = buildString {
            append("(function(){try{")
            append("function val(id){var el=document.getElementById(id);return el?String(el.value):null;}")
            append("function set(id,v){if(!v)return;var el=document.getElementById(id);if(!el)return;")
            append("el.focus();el.value=v;")
            append("el.dispatchEvent(new Event('input',{bubbles:true}));")
            append("el.dispatchEvent(new Event('change',{bubbles:true}));}")
            append("set('un',").append(JSONObject.quote(fillU)).append(");")
            append("set('pd',").append(JSONObject.quote(fillP)).append(");")
            append("set('code',").append(JSONObject.quote(fillC)).append(");")
            append("QauBridge.post(JSON.stringify({ok:true,kind:'fill',un:val('un'),code:val('code'),")
            append("lenPd:(function(){var e=document.getElementById('pd');return e?String(e.value).length:-1;})()}));")
            append("}catch(e){QauBridge.post(JSON.stringify({ok:false,error:String(e)}));}})();")
        }
        status = "正在填入网页…"
        safeEval(js, "填入")
    }

    Column(modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(10.dp)) {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(status, style = MaterialTheme.typography.bodySmall)

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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("输入方向修复（LTR）", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "实测对「输入倒序」无效，默认关闭；留作对照实验。",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = appSettings.forceLtrInput,
                        onCheckedChange = { vm.setForceLtr(it) },
                    )
                }

                // ------------------------------------------------ 手动填入
                OutlinedButton(
                    onClick = { showFill = !showFill },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (showFill) "收起手动填入" else "手动填入（绕开输入法，治输入倒序）") }

                if (showFill) {
                    Text(
                        "在下面这几个框里输入（走 App 原生输入框），" +
                            "再点「填入网页」。动态码看网页上的图片。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = fillU, onValueChange = { fillU = it },
                        label = { Text("学号 / 教工号") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = fillP, onValueChange = { fillP = it },
                        label = { Text("密码") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = fillC, onValueChange = { fillC = it },
                        label = { Text("动态码 / 验证码") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { fillNative() }, modifier = Modifier.weight(1f)) {
                            Text("填入网页")
                        }
                        OutlinedButton(
                            onClick = { fillU = ""; fillP = ""; fillC = "" },
                            modifier = Modifier.weight(1f),
                        ) { Text("清空") }
                    }
                }

                if (diagnostics.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            diagnostics,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
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

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    // ---- 与 Chrome 对齐，这一点是"输入倒序"的关键 ----
                    //
                    // 页面的 viewport meta 是 width=device-width, initial-scale=1。
                    // 如果不设 useWideViewPort，WebView 会**忽略这个 meta**，
                    // 把页面按默认的 980px 宽布局后再整体缩放塞进控件宽度。
                    // 于是输入框的坐标都落在"缩放空间"里，中文输入法跟随光标
                    // 组词时算出的插入位置就会错 —— 表现就是输入倒序。
                    // Chrome 是尊重 viewport meta 的，所以同一个页面在浏览器里正常。
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    // 与浏览器对齐：不改动自动填充行为。
                    // （之前显式设 IMPORTANT_FOR_AUTOFILL_NO 反而制造了差异）
                    settings.javaScriptCanOpenWindowsAutomatically = true

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 刻意不设 useWideViewPort / loadWithOverviewMode：
                    // 它们让页面随控件尺寸变化重新缩放，软键盘弹出时
                    // 会在输入法组词过程中触发重排。

                    addJavascriptInterface(QauBridge { json -> deliver(json) }, "QauBridge")
                    webViewClient = object : WebViewClient() {
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
