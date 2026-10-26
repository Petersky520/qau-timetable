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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import cn.edu.qau.timetable.notify.ScheduleStatus
import cn.edu.qau.timetable.util.CrashLogger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 设置页。
 *
 * 这页「文本多、一打字就掉帧」，根因不在 TextField，而在重组作用域：
 * 原先 15 个输入框的 `remember { mutableStateOf }` 全挂在 `SettingsScreen` 函数体里，
 * 而整页是同一个 `Column(verticalScroll)`。任何一个字符变化都会让 `SettingsScreen`
 * 整体失效 —— 10 张卡全部重跑（重新读 MaterialTheme、重建 FilterChip 行、重建
 * TextField 装饰、重新格式化字符串），一帧里做完这些自然掉帧。
 *
 * 现在每张卡都是独立 composable，输入草稿状态用 `remember` 关在卡内部，
 * `AppSettings` 也由卡自己 `collectAsState` 订阅。于是：敲字只让那一张卡重组；
 * 保存设置后 AppSettings 变了，也只影响真正读它的卡。
 *
 * 这个函数体因此只剩下布局，不再持有任何状态 —— 它自己几乎不会重组，
 * 这是整页打字流畅的关键，别再往这里加 `mutableStateOf`。
 */
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CampusCard(vm)
        AppearanceCard(vm)
        TermCard(vm)
        OverridesCard(vm)
        ReminderCard(vm)
        SilenceCard(vm)
        UrlsCard(vm)
        // AI 搜索只服务于考研模块（选校问答）。Lite 版没有考研，
        // 留着这张卡只会让人填了密钥却找不到用处。
        if (KaoyanFeature.hasKaoyan) AiCard(vm)
        CrashLogCard()
        AboutCard()
    }
}

/**
 * 校区卡。
 *
 * 单独拆出来的理由：它的状态只有 `settings.campus` 一项，跟文本输入毫无关系。
 * 拆开之后，在「学期」「抓取地址」里敲字，这张卡连组合都不会进。
 */
@Composable
private fun CampusCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

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
}

/**
 * 外观卡（界面风格 / 动态取色 / 底栏 / 性能模式）。
 *
 * 单独拆出来的理由：这一块是设置页里最「贵」的一卡 —— 5 组 FilterChip + 3 个
 * Switch + 一串依赖系统版本的条件文案。它没有任何输入框，把它挪出 `SettingsScreen`
 * 之后，打字时它不会被重建。
 */
@Composable
private fun AppearanceCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

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

            HorizontalDivider(Modifier.padding(vertical = 2.dp))

            // ------------------------------------------------- 性能模式
            //
            // 一个开关换掉"每帧整屏录屏 + 实时高斯模糊"。低内存设备默认就开着
            // （SettingsStore 用 ActivityManager.isLowRamDevice 判断）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("性能模式", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (settings.performanceMode) {
                            "已开启：底栏改用纯色，不再实时模糊。滚动更跟手，也更省电。"
                        } else {
                            "关着：底栏实时模糊背后内容。低端机上滚动可能掉帧就开这个。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (settings.performanceMode && settings.glassEffect.blursBackdrop) {
                        // 用户选了模糊但被性能模式压平 —— 必须说清楚，
                        // 否则他会以为「底栏效果」那颗 chip 坏了。
                        Text(
                            "开着性能模式时，「底栏效果」选的模糊不会生效，会以纯色显示。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(
                    checked = settings.performanceMode,
                    onCheckedChange = { vm.setPerformanceMode(it) },
                )
            }
        }
    }
}

/**
 * 学期卡。
 *
 * 单独拆出来的理由：三个输入框（学期名 / 第 1 周周一 / 总周数）的草稿状态全部
 * 住在这里。以前它们在页面顶层，敲学期名会让提醒、静音、AI 那些卡跟着重跑；
 * 现在打字只重组这一张卡，日期选择弹窗也不再牵动整页。
 *
 * 回填用 `remember(settings.xxx)` 做键：只有 DataStore 里这一项真的变了才覆盖
 * 输入框，用户正在敲的内容不会被无关的设置变更冲掉。
 */
@Composable
private fun TermCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    var termName by remember(settings.termName) { mutableStateOf(settings.termName) }
    var startMonday by remember(settings.startMonday) { mutableStateOf(settings.startMonday) }
    var totalWeeks by remember(settings.totalWeeks) {
        mutableStateOf(settings.totalWeeks.toString())
    }

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
}

/**
 * 调休安排卡。
 *
 * 单独拆出来的理由：这里有「新增一条」的草稿状态（日期 + 放假/补周几）和整张
 * 调休列表，列表每加一条都会重画。状态关在卡内，别的卡敲字时不会被牵连。
 */
@Composable
private fun OverridesCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    // 调休「新增一条」表单的草稿状态。
    // newPick：-1 = 还没选；0 = 放假；1..7 = 补那一天的课。
    // 这两项不参与 DataStore 回填：它们是「尚未提交的表单」，回填只会把用户
    // 填了一半的东西清掉。
    var newOverrideDate by remember { mutableStateOf("") }
    var newPick by remember { mutableStateOf(-1) }

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
}

/**
 * 上课提醒卡。
 *
 * 单独拆出来的理由：开关和「提前多少分钟」都是本地草稿，点保存才落盘。
 * 状态留在卡内之后，改这两项不会让整页包括其它 9 张卡一起重组。
 */
@Composable
private fun ReminderCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    var remindEnabled by remember(settings.remindEnabled) {
        mutableStateOf(settings.remindEnabled)
    }
    var remindMinutes by remember(settings.remindMinutesBefore) {
        mutableStateOf(settings.remindMinutesBefore.toString())
    }

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
            // 音效开关。
            //
            // 单独一行而不是塞进上面那个「保存提醒设置」按钮：音效是**立即生效**的
            // （切了立刻重建通知渠道），跟「提前多少分钟」那种要保存才落盘的字段
            // 语义不同。混在一起用户会以为改完还得再点一次保存。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("奶龙捧腹大笑音效", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关掉则用系统默认提示音。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Switch(
                    checked = settings.remindSoundEnabled,
                    onCheckedChange = { vm.setRemindSound(it) },
                )
            }
            // 上一次排程的结论。
            //
            // 提醒有 6 个静默早退点（开关关 / 没课表 / 第 1 周周一没填 /
            // 课表为空 / 7 天内没课 / 精确闹钟权限被拒）。只给一个开关的话，
            // 用户报「提醒不响」时双方都只能猜 —— 这行字直接说清卡在哪一环。
            ScheduleStatusLine(ScheduleStatus.Kind.REMINDER, vm)
            Button(onClick = {
                vm.setRemind(
                    enabled = remindEnabled,
                    minutesBefore = remindMinutes.toIntOrNull()?.coerceIn(1, 120) ?: 15,
                )
            }) { Text("保存提醒设置") }
        }
    }
}

/**
 * 上课自动静音卡。
 *
 * 单独拆出来的理由：这卡既有输入（静音模式 chip、开关），又订阅系统状态
 * （勿扰授权 `dndGranted`）。授权状态的 ON_RESUME 重读是 `DisposableEffect`，
 * 挂在页面顶层会让整页跟着生命周期事件重组；放进卡里之后，系统设置页返回
 * 只刷新这一张卡。
 */
@Composable
private fun SilenceCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current

    var silenceEnabled by remember(settings.silenceEnabled) {
        mutableStateOf(settings.silenceEnabled)
    }
    var silenceMode by remember(settings.silenceMode) { mutableStateOf(settings.silenceMode) }
    var dndGranted by remember { mutableStateOf(RingerModeController.isGranted(context)) }

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
            // 同 ReminderCard：静音也有好几个静默早退点，把上次排程结论亮出来。
            // 尤其「重装 App 后通知策略权限被撤销」这一条，不写出来没人能想到。
            ScheduleStatusLine(ScheduleStatus.Kind.SILENCE, vm)
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
}

/**
 * 教务系统抓取地址卡。
 *
 * 单独拆出来的理由：三个长 URL 输入框是这页最长的文本，敲键盘时最费帧。
 * 状态关在卡内，敲一个字只重组这三行输入框，不再带着整页 10 张卡一起跑。
 */
@Composable
private fun UrlsCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    var kbUrl by remember(settings.kbUrl) { mutableStateOf(settings.kbUrl) }
    var examUrl by remember(settings.examUrl) { mutableStateOf(settings.examUrl) }
    var gradeUrl by remember(settings.gradeUrl) { mutableStateOf(settings.gradeUrl) }

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
            Button(onClick = {
                vm.setUrls(kbUrl.trim(), examUrl.trim(), gradeUrl.trim())
            }) { Text("保存地址") }
        }
    }
}

/**
 * AI 搜索卡。
 *
 * 单独拆出来的理由：4 个输入框（其中密钥框还带密码样式）+ 两条独立保存路径，
 * 是这页文本输入最密集的一块。草稿状态关在卡内，填密钥时不会让提醒/静音/调休
 * 那几张卡跟着重组 —— 顺带也让密钥只在这张卡的组合里被读到。
 */
@Composable
private fun AiCard(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()

    // AI 搜索（考研模块用）。密钥输入框走密码样式，且**不**回显到任何日志。
    var aiBaseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var aiApiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var catalogUrl by remember(settings.kaoyanCatalogUrl) {
        mutableStateOf(settings.kaoyanCatalogUrl)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI 搜索（考研）", style = MaterialTheme.typography.titleSmall)
            Text(
                "「考研 → AI 搜索」用的是你自己填的服务：任何 OpenAI 兼容接口都行" +
                    "（DeepSeek、通义、自建 vLLM 等）。三项都填了才会生效。",
                style = MaterialTheme.typography.labelSmall,
            )
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = { aiBaseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = { aiApiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                // 密钥明文摆在屏幕上，旁边有人一眼就看走了
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = aiModel,
                onValueChange = { aiModel = it },
                label = { Text("模型名") },
                placeholder = { Text("deepseek-chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = catalogUrl,
                onValueChange = { catalogUrl = it },
                label = { Text("考研目录刷新地址（可空）") },
                placeholder = { Text("留空 = 只用内置样例目录") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.setAi(aiBaseUrl.trim(), aiApiKey.trim(), aiModel.trim())
                }) { Text("保存 AI 配置") }
                OutlinedButton(onClick = {
                    vm.setKaoyanCatalogUrl(catalogUrl.trim())
                }) { Text("保存目录地址") }
            }
            // 「目录地址」和上面三项分开保存：改目录不该顺带把密钥重写一遍，
            // 密钥框里可能刚被用户清空（想换一把还没拿到新的）。
            Text(
                "密钥只存在本机 DataStore，不进仓库、不上传，App 也不会打印它。" +
                    "换手机或卸载重装需要重新填一次。",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * 上次崩溃日志卡。
 *
 * 单独拆出来的理由：它的「查看 / 复制 / 清除」是三个本地 UI 状态，而且日志正文
 * 可能很长、还套了一个内层 `verticalScroll`。这些跟设置项无关，没有理由让别的卡
 * 的输入去触发它重排。
 *
 * 它不接收 `vm`：这张卡从头到尾不读写设置，只碰 `CrashLogger` 和应用内部文件。
 */
@Composable
private fun CrashLogCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var crashLog by remember { mutableStateOf<String?>(null) }
    var showCrash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { crashLog = CrashLogger.read(context) }

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
}

/**
 * 「上一次排程的结果」那一行。
 *
 * ## 为什么值得占一行字
 *
 * 提醒和静音都有好几个早退条件，而且**每一个都是静默 return**：
 * 界面上开关是开着的、看起来一切正常，闹钟其实一个都没排上。
 * 用户报「提醒不响」时，光看开关完全分不清是
 *   ① 没排（条件不满足）② 排了没响 ③ 权限被系统拒了
 * 这三种的修法完全不同。
 *
 * 把调度器的结论原样显示出来，用户自己就能看出卡在哪一环 ——
 * 也让以后的问题报告从「不响」变成「这里写着第 1 周周一没填对」。
 *
 * ## 刷新时机
 *
 * 排程是异步的（`reschedule()` 起协程），所以不能在点击后立刻读。
 * 这里以 `settings` 的变化为信号重读一次：任何会触发重排的操作
 * （改开关、改提前分钟、导入课表）最终都会改变 settings，
 * 于是读完时排程基本已经落盘。排程还没跑完时会短暂显示上一次的结论，
 * 这对诊断足够 —— 比读不到就干脆不显示要好。
 */
@Composable
private fun ScheduleStatusLine(kind: ScheduleStatus.Kind, vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    // settings 只用作「重排可能已经发生」的触发器，值本身不参与显示。
    val text = remember(settings) {
        runCatching { ScheduleStatus.read(context, kind) }.getOrNull()
    }

    if (text.isNullOrBlank()) {
        Text(
            "还没排过闹钟${kind.label}。点上面的保存、或去「同步」页抓一次课表就会排。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    } else {
        Text(
            "上次排程：$text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 关于卡。
 *
 * 单独拆出来的理由：整页唯一一张纯静态卡（三行说明文字，无状态、无设置依赖）。
 * 拆成独立 composable 之后它天然可跳过 —— 别的卡怎么重组都跟它无关。
 */
@Composable
private fun AboutCard() {
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
