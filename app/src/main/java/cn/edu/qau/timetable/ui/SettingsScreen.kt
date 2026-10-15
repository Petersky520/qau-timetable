package cn.edu.qau.timetable.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GlassEffect
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.UiStyle
import cn.edu.qau.timetable.notify.RingerModeController
import cn.edu.qau.timetable.util.CrashLogger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    var silenceEnabled by remember { mutableStateOf(settings.silenceEnabled) }
    var silenceMode by remember { mutableStateOf(settings.silenceMode) }
    var dndGranted by remember { mutableStateOf(RingerModeController.isGranted(context)) }
    var kbUrl by remember { mutableStateOf(settings.kbUrl) }
    var examUrl by remember { mutableStateOf(settings.examUrl) }
    var gradeUrl by remember { mutableStateOf(settings.gradeUrl) }
    var classroomUrl by remember { mutableStateOf(settings.classroomUrl) }

    // 调休「新增一条」表单的草稿状态。
    // newPick：-1 = 还没选；0 = 放假；1..7 = 补那一天的课。
    var newOverrideDate by remember { mutableStateOf("") }
    var newPick by remember { mutableStateOf(-1) }

    // 设置从 DataStore 加载完成后同步一次本地输入框
    LaunchedEffect(settings) {
        termName = settings.termName
        startMonday = settings.startMonday
        totalWeeks = settings.totalWeeks.toString()
        remindEnabled = settings.remindEnabled
        remindMinutes = settings.remindMinutesBefore.toString()
        silenceEnabled = settings.silenceEnabled
        silenceMode = settings.silenceMode
        kbUrl = settings.kbUrl
        examUrl = settings.examUrl
        gradeUrl = settings.gradeUrl
        classroomUrl = settings.classroomUrl
    }

    // 从系统设置页返回时重新读一次权限 —— 否则用户授完权回到 App，
    // 界面还停在"未授权"，会让人以为没生效。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dndGranted = RingerModeController.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openDndSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
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

                Text("界面风格", style = MaterialTheme.typography.bodyMedium)
                Text(settings.uiStyle.description, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiStyle.entries.forEach { s ->
                        FilterChip(
                            selected = settings.uiStyle == s,
                            onClick = { vm.setUiStyle(s) },
                            label = { Text(s.label) },
                        )
                    }
                }

                val m3 = settings.uiStyle == UiStyle.MATERIAL3
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("跟随壁纸取色", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                !m3 -> "MIUI X 用固定配色，这一项对它不生效。"

                                android.os.Build.VERSION.SDK_INT >=
                                    android.os.Build.VERSION_CODES.S ->
                                    "Material You 动态取色，配色由手机壁纸决定。"

                                else ->
                                    "需要 Android 12 及以上；当前系统不支持，将使用内置配色。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor && m3,
                        enabled = m3,
                        onCheckedChange = { vm.setDynamicColor(it) },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("悬浮底栏", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (settings.floatingBottomBar) "浮在内容之上" else "通栏贴底",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = settings.floatingBottomBar,
                        onCheckedChange = { vm.setFloatingBottomBar(it) },
                    )
                }

                Text("底栏效果", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (settings.glassEffect) {
                        GlassEffect.NONE -> "不透明"
                        GlassEffect.GAUSSIAN -> "把背后内容糊成磨砂玻璃"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassEffect.entries.forEach { e ->
                        FilterChip(
                            selected = settings.glassEffect == e,
                            onClick = { vm.setGlassEffect(e) },
                            label = { Text(e.label) },
                        )
                    }
                }
                if (!settings.floatingBottomBar && settings.glassEffect.blursBackdrop) {
                    Text(
                        "选了模糊，底栏会浮起来让内容从底下穿过",
                        style = MaterialTheme.typography.labelSmall,
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
                DateField(
                    value = startMonday,
                    onValueChange = { startMonday = it },
                    label = "第 1 周周一",
                    placeholder = "如 2026-09-07",
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

        // ---------------------------------------------------------- 调休安排
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("调休安排", style = MaterialTheme.typography.titleSmall)
                Text(
                    "法定节假日调休填在这里。补课日照常有提醒、也会自动静音。",
                    style = MaterialTheme.typography.labelSmall,
                )

                if (settings.dayOverrides.isEmpty()) {
                    Text("还没有调休安排。", style = MaterialTheme.typography.labelSmall)
                } else {
                    settings.dayOverrides.forEach { o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${o.date}  ${DayOverrides.labelOf(o.date.dayOfWeek.value)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    o.useDayOfWeek
                                        ?.let { "补${DayOverrides.labelOf(it)}的课" }
                                        ?: "放假，当天没有课",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(onClick = {
                                vm.setDayOverrides(
                                    settings.dayOverrides.filterNot { it.date == o.date }
                                )
                            }) { Text("删除") }
                        }
                    }
                }

                DateField(
                    value = newOverrideDate,
                    onValueChange = { newOverrideDate = it },
                    label = "日期",
                    placeholder = "如 2026-10-11",
                )
                Text("这一天：", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = newPick == 0,
                        onClick = { newPick = 0 },
                        label = { Text("放假") },
                    )
                    for (d in 1..3) {
                        FilterChip(
                            selected = newPick == d,
                            onClick = { newPick = d },
                            label = { Text(DayOverrides.labelOf(d)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 4..7) {
                        FilterChip(
                            selected = newPick == d,
                            onClick = { newPick = d },
                            label = { Text(DayOverrides.labelOf(d)) },
                        )
                    }
                }
                Button(onClick = {
                    val date = runCatching { LocalDate.parse(newOverrideDate.trim()) }.getOrNull()
                    when {
                        date == null ->
                            vm.toast("日期格式不对，要写成 2026-10-11 这样")

                        newPick < 0 ->
                            vm.toast("先选一下这天是「放假」还是「补周几的课」")

                        else -> {
                            // 同一天只留一条：先去掉同日期的旧安排再追加
                            val next = settings.dayOverrides.filterNot { it.date == date } +
                                DayOverride(
                                    date = date,
                                    useDayOfWeek = if (newPick == 0) null else newPick,
                                )
                            vm.setDayOverrides(next)
                            newOverrideDate = ""
                            newPick = -1
                        }
                    }
                }) { Text("添加调休") }
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

        // ---------------------------------------------------------- 上课静音
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("上课自动静音", style = MaterialTheme.typography.titleSmall)
                Text(
                    "上课时自动把铃声调低，下课后还原成你原来的设置。" +
                        "中间 15 分钟以内的课间不会恢复，午休和晚饭时间会正常恢复。",
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("开启自动静音", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (dndGranted) {
                                "已获得勿扰权限。"
                            } else {
                                "还缺「勿扰 / 通知策略」权限，需要去系统设置里允许。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = silenceEnabled,
                        onCheckedChange = { on ->
                            // 没有权限就先引导去授权，不要先把开关打开骗用户
                            if (on && !dndGranted) openDndSettings() else {
                                silenceEnabled = on
                                vm.setSilence(on, silenceMode)
                            }
                        },
                    )
                }
                if (!dndGranted) {
                    OutlinedButton(
                        onClick = { openDndSettings() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("去授予勿扰权限") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SilenceMode.entries.forEach { m ->
                        FilterChip(
                            selected = silenceMode == m,
                            onClick = {
                                silenceMode = m
                                // 已经开着的话立刻用新模式重排，不用再点一次
                                if (silenceEnabled) vm.setSilence(true, m)
                            },
                            label = { Text(m.label) },
                        )
                    }
                }
                Text(
                    "「完全静音」连震动都没有；「仅震动」会保留震动。" +
                        "如果你在上课期间自己调过铃声，下课时不会覆盖你的选择。",
                    style = MaterialTheme.typography.labelSmall,
                )
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

/**
 * 日期输入框：可以直接敲 `2026-10-11`，也可以点右边的日历图标挑。
 *
 * 挑日期用的是 **Material 3 的日期选择器**（就在 App 主题里弹出），而不是跳去
 * 别的 App —— Android **没有**「请日历应用帮我选个日期、再把结果还回来」这种机制：
 * 系统只提供 `CalendarContract` 读写日程，没有日期选择的 Intent 契约，
 * 各家日历（小米日历、Google 日历…）也都没有对外暴露这种入口。
 * 所以「调用手机里的日历软件选日期」这件事在 Android 上做不到，
 * 能做到的是用系统级的日期选择器 —— 下面这个就是。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { picking = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = "挑选日期")
            }
        },
    )

    if (picking) {
        // 用输入框里已有的日期做初值；解析不出来就落在今天
        val initial = remember(picking) {
            runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: LocalDate.now()
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    // M3 日期选择器回传的是「UTC 零点」的毫秒数，必须按 UTC 还原成日期；
                    // 用本地时区还原会整体差一天（东八区会变成前一天）。
                    state.selectedDateMillis?.let { ms ->
                        onValueChange(
                            Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        )
                    }
                    picking = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
