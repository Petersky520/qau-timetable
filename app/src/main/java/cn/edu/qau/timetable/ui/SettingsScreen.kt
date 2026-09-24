package cn.edu.qau.timetable.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.util.CrashLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var crashLog by remember { mutableStateOf<String?>(null) }
    var showCrash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { crashLog = CrashLogger.read(context) }

    var termName by remember { mutableStateOf(settings.termName) }
    var startMonday by remember { mutableStateOf(settings.startMonday) }
    var totalWeeks by remember { mutableStateOf(settings.totalWeeks.toString()) }
    var remindEnabled by remember { mutableStateOf(settings.remindEnabled) }
    var remindMinutes by remember { mutableStateOf(settings.remindMinutesBefore.toString()) }
    var kbUrl by remember { mutableStateOf(settings.kbUrl) }
    var examUrl by remember { mutableStateOf(settings.examUrl) }
    var gradeUrl by remember { mutableStateOf(settings.gradeUrl) }
    var classroomUrl by remember { mutableStateOf(settings.classroomUrl) }

    // 设置从 DataStore 加载完成后同步一次本地输入框
    LaunchedEffect(settings) {
        termName = settings.termName
        startMonday = settings.startMonday
        totalWeeks = settings.totalWeeks.toString()
        remindEnabled = settings.remindEnabled
        remindMinutes = settings.remindMinutesBefore.toString()
        kbUrl = settings.kbUrl
        examUrl = settings.examUrl
        gradeUrl = settings.gradeUrl
        classroomUrl = settings.classroomUrl
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------------------------------------------------------- 校区
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("校区", style = MaterialTheme.typography.titleSmall)
                Text(
                    "两个校区的作息时间不同，选错会导致上课提醒时间不对。",
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campus.entries.forEach { c ->
                        FilterChip(
                            selected = settings.campus == c,
                            onClick = { vm.setCampus(c) },
                            label = { Text(c.label) },
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- 外观
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("外观", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("跟随壁纸取色", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                "Material You 动态取色，配色由手机壁纸决定。"
                            } else {
                                "需要 Android 12 及以上；当前系统不支持，将使用内置配色。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { vm.setDynamicColor(it) },
                    )
                }
            }
        }

        // ---------------------------------------------------------- 学期
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学期", style = MaterialTheme.typography.titleSmall)
                Text(
                    "「第 1 周周一」决定周次换算，请按校历填写。留空则按月份粗略估算。",
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = termName,
                    onValueChange = { termName = it },
                    label = { Text("学期名，如 2026-2027-1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startMonday,
                    onValueChange = { startMonday = it },
                    label = { Text("第 1 周周一，如 2026-09-07") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = totalWeeks,
                    onValueChange = { totalWeeks = it.filter { ch -> ch.isDigit() } },
                    label = { Text("总周数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    vm.setTerm(
                        name = termName.trim(),
                        startMonday = startMonday.trim(),
                        totalWeeks = totalWeeks.toIntOrNull()?.coerceIn(1, 30) ?: 20,
                    )
                }) { Text("保存学期设置") }
            }
        }

        // ---------------------------------------------------------- 提醒
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("上课提醒", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开启提醒", Modifier.weight(1f))
                    Switch(checked = remindEnabled, onCheckedChange = { remindEnabled = it })
                }
                OutlinedTextField(
                    value = remindMinutes,
                    onValueChange = { remindMinutes = it.filter { ch -> ch.isDigit() } },
                    label = { Text("提前多少分钟") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "只排未来 7 天的闹钟；开机或导入课表后会自动重排。",
                    style = MaterialTheme.typography.labelSmall,
                )
                Button(onClick = {
                    vm.setRemind(
                        enabled = remindEnabled,
                        minutesBefore = remindMinutes.toIntOrNull()?.coerceIn(1, 120) ?: 15,
                    )
                }) { Text("保存提醒设置") }
            }
        }

        // ---------------------------------------------------------- 抓取地址
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("教务系统抓取地址", style = MaterialTheme.typography.titleSmall)
                Text(
                    "默认是强智 jsxsd 的常见路径。如果抓不到，可以登录后在浏览器里" +
                        "打开对应页面，把地址栏的 URL 粘到这里。",
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = kbUrl, onValueChange = { kbUrl = it },
                    label = { Text("课表页") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = examUrl, onValueChange = { examUrl = it },
                    label = { Text("考试安排页") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gradeUrl, onValueChange = { gradeUrl = it },
                    label = { Text("成绩页") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = classroomUrl, onValueChange = { classroomUrl = it },
                    label = { Text("空闲教室页") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    vm.setUrls(kbUrl.trim(), examUrl.trim(), gradeUrl.trim(), classroomUrl.trim())
                }) { Text("保存地址") }
            }
        }

        // ---------------------------------------------------------- 崩溃日志
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("上次崩溃日志", style = MaterialTheme.typography.titleSmall)
                val log = crashLog
                if (log.isNullOrBlank()) {
                    Text(
                        "没有记录到崩溃。（WebView 渲染进程的崩溃不会被捕获，" +
                            "那种情况需要 adb logcat）",
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    Text(
                        "App 闪退过。把这段日志复制发给开发者即可定位。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showCrash = !showCrash }) {
                            Text(if (showCrash) "收起" else "查看")
                        }
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(log)) }) {
                            Text("复制")
                        }
                        OutlinedButton(onClick = {
                            CrashLogger.clear(context)
                            crashLog = null
                            showCrash = false
                        }) { Text("清除") }
                    }
                    if (showCrash) {
                        SelectionContainer {
                            Text(
                                log,
                                style = MaterialTheme.typography.labelSmall
                                    .copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- 关于
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于", style = MaterialTheme.typography.titleSmall)
                Text(
                    "本 App 是个人自用工具，不是学校官方应用。\n" +
                        "· 密码只在 WebView 里输入，App 不保存、不上传；\n" +
                        "· 所有数据存在本机，不会同步到任何服务器；\n" +
                        "· 课表通过抓取教务系统页面得到，页面改版可能导致抓取失败。\n\n" +
                        "学校声明「微信企业号移动平台」是唯一官方移动端渠道，" +
                        "因此请勿将本 App 用于对外分发。",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
