package cn.edu.qau.timetable.ui.kaoyan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.ui.glass.LocalFloatingBarReserve
import cn.edu.qau.timetable.ui.motion.rememberEntranceWindow
import cn.edu.qau.timetable.ui.motion.staggeredAppear

/** 空状态。理由同 `KaoyanSchoolScreen.kt` 里那个同名函数：自己写一份，不动原文件。 */
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

/** 「继续追问」的快捷入口：用户第一次打开这页，多半不知道该问什么。 */
private val FOLLOW_UPS = listOf(
    "推荐几所保底院校",
    "这个专业考什么科目",
    "复试一般考什么",
    "跨考需要注意什么",
)

/**
 * AI 问答页（仅 API 模式）。
 *
 * ## 为什么网页版被整段删掉了
 * 曾经这里有两个 FilterChip：「网页版（免费）」和「API（自备密钥）」，
 * 网页版通过 WebView 打开 chat.deepseek.com、用用户自己的账号登录。
 * 上线后发现 **DeepSeek 对嵌入式 WebView 有风控** —— 登录能过，但提问会被拦，
 * 表现为一直转圈或直接返回风控提示，且无法通过任何 App 侧的改动绕开
 * （那是站点侧策略，不是客户端 bug）。
 *
 * 一条时灵时不灵的付费墙旁路不值得留在正式功能里：用户点进来只想要答案，
 * 却要先登录、再撞风控、再看不懂为什么失败。所以整段移除，
 * 只保留「自备密钥」这条确定性路径。
 *
 * ## 为什么把 API 那套抽成私有 composable 而不是删掉重写
 * 那段逻辑（未配置引导、Loading、错误卡、继续追问）踩过的坑都写在注释里，
 * 抽出来原样保留。
 */
@Composable
fun KaoyanAiScreen(
    vm: KaoyanViewModel,
    onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AiApiPane(vm, onGoSettings, modifier.fillMaxSize())
}

/**
 * API 模式（自备密钥）的具体实现，内容与原 `KaoyanAiScreen` 一致。
 *
 * ## 为什么把「未配置」做成整页引导而不是一句红字
 * 这个功能不花 App 的钱，密钥得用户自己填。大多数人第一次点进来都是未配置状态，
 * 如果只留一个禁用的输入框，用户会以为是功能坏了 —— 所以未配置时直接
 * 把「去哪儿填、填什么」写在页面上。
 *
 * ## 为什么回答下面必须挂「以研招网为准」
 * 模型会一本正经地编分数线和招生人数。这句话不是免责声明，是使用说明：
 * 这个页面的定位是「帮你想清楚该查什么」，不是「告诉你确切数字」。
 */
@Composable
private fun AiApiPane(
    vm: KaoyanViewModel,
    onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsState()
    val answer by vm.aiAnswer.collectAsState()
    val error by vm.aiError.collectAsState()
    val clipboard = LocalClipboardManager.current

    val ready = settings.aiConfig.isReady
    var question by rememberSaveable { mutableStateOf("") }

    val entrance = rememberEntranceWindow(2)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 4.dp,
            end = 12.dp,
            // 悬浮底栏会盖住内容，这里必须让出那一段，否则最后一行永远滚不出来
            bottom = 12.dp + LocalFloatingBarReserve.current,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---------------------------------------------------------- 说明卡
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .staggeredAppear(0, animate = entrance),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI 信息流搜索", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "这是一个「自己带钥匙」的 AI 问答：App 不内置任何密钥，" +
                            "你在设置里填一个 OpenAI 兼容的服务地址和密钥才会工作。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (ready) {
                        Text(
                            "当前模型：${settings.aiModel}　服务：${settings.aiBaseUrl}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        Text(
                            "还没有配置。去「设置 → AI 搜索」填三项：base URL（如 " +
                                "https://api.deepseek.com/v1）、API Key、模型名（如 deepseek-chat）。" +
                                "填完这条提示会消失。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onGoSettings) {
                            Text("去设置里填")
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 提问
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .staggeredAppear(1, animate = entrance),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("想问什么") },
                        placeholder = { Text("如：山东的双非食品专业，320 分能冲哪些学校") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.askAi(question) },
                            enabled = answer !is AiState.Loading,
                        ) { Text("提问") }
                        if (question.isNotBlank()) {
                            TextButton(onClick = { question = "" }) { Text("清空") }
                        }
                    }
                    if (answer is AiState.Loading) {
                        // 静态底轨，不再用 Material3 的 LinearProgressIndicator()。
                        //
                        // 为什么必须换掉：无参重载是**不确定态**，内部挂一条
                        // rememberInfiniteTransition —— 只要它还在组合里，就每帧推进、
                        // 无限重绘。AI 回答通常要等好几秒，这段时间等于拿整台设备的
                        // 帧预算去喂一个"我没死"的信号；而且这页还有可滚动的 LazyColumn，
                        // 那条无限过渡会让整个绘制流程一直没法进入静止态。
                        //
                        // 换成 4dp 高的胶囊形静态底轨（Spacer + background）：
                        //  - 零动画、零无限过渡、没有任何子节点，画完即静止；
                        //  - 下面那行「AI 正在整理…」已经把状态说清楚了，
                        //    不确定态进度条本身也不携带任何可度量信息；
                        //  - 高度/圆角/配色与 M3 进度条的底轨一致，观感不突兀。
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Text("AI 正在整理…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ---------------------------------------------------------- 错误
        error?.let { msg ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("这次没问成", style = MaterialTheme.typography.titleSmall)
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "改完配置后在设置页保存一次，再回来提问即可。",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- 回答
        when (val state = answer) {
            is AiState.Idle -> if (!ready) {
                item {
                    // 固定高度：EmptyState 内部是 fillMaxSize 居中，直接塞进
                    // LazyColumn 会拿到无界高度、量出来是 0。
                    Box(Modifier.fillMaxWidth().height(220.dp)) {
                        EmptyState(
                            "还没法提问",
                            "先到「设置 → AI 搜索」把 base URL、API Key、模型名填上。",
                        )
                    }
                }
            }

            is AiState.Loading -> Unit

            is AiState.Ok -> {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("AI 的回答", style = MaterialTheme.typography.titleSmall)
                            SelectionContainer {
                                Text(state.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.text))
                                    vm.notify("已复制回答")
                                }) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    Text("复制")
                                }
                            }
                            // 这句话必须在回答**下面**、贴着回答，而不是塞在页面顶部：
                            // 用户看完数字会顺手信，提示得出现在他正在读的地方。
                            Text(
                                "AI 的回答仅供参考，分数线与招生计划请以研招网为准。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("继续追问", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FOLLOW_UPS.take(2).forEach { q ->
                                AssistChip(
                                    onClick = {
                                        question = q
                                        vm.askAi(q)
                                    },
                                    label = { Text(q) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FOLLOW_UPS.drop(2).forEach { q ->
                                AssistChip(
                                    onClick = {
                                        question = q
                                        vm.askAi(q)
                                    },
                                    label = { Text(q) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 免责
        item {
            Text(
                "· 密钥只存在本机 DataStore，不进仓库、不上传；\n" +
                    "· 提问内容会发给你自己填的那个服务商，发之前想一下有没有不该外发的东西；\n" +
                    "· 分数线、招生计划这类硬数字，最终以研招网和各校研究生院公告为准。",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
