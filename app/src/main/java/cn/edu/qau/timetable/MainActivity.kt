package cn.edu.qau.timetable

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.edu.qau.timetable.notify.ReminderReceiver
import cn.edu.qau.timetable.ui.ClassroomsScreen
import cn.edu.qau.timetable.ui.ExamsScreen
import cn.edu.qau.timetable.ui.GradesScreen
import cn.edu.qau.timetable.ui.MainViewModel
import cn.edu.qau.timetable.ui.SettingsScreen
import cn.edu.qau.timetable.ui.SyncScreen
import cn.edu.qau.timetable.ui.TimetableScreen
import cn.edu.qau.timetable.ui.TodayScreen
import cn.edu.qau.timetable.ui.theme.QauTheme
import cn.edu.qau.timetable.util.CrashLogger

class MainActivity : ComponentActivity() {

    // 说明：这里原来 override attachBaseContext 把 locale 强制成简体中文
    // （想规避 Android 7 时代 WebView 重置 Activity locale 的老问题）。
    // 那个问题早已修复，而且 Chrome 从不强制 locale ——
    // 为了排查"输入倒序"，把这层"浏览器没有的行为"删掉。

    private val vm: MainViewModel by viewModels {
        val container = (application as QauApp).container
        viewModelFactory {
            initializer { MainViewModel(container.repo, container.reminders) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureChannel(this)

        setContent {
            val settings by vm.settings.collectAsState()
            QauTheme(dynamicColor = settings.dynamicColor) {
                AppRoot(vm)
            }
        }
    }
}

private enum class Tab(
    val label: String,
    val iconSelected: ImageVector,
    val iconNormal: ImageVector,
) {
    TIMETABLE("课表", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    TODAY("今日", Icons.Filled.Today, Icons.Outlined.Today),
    EXAMS("考试", Icons.Filled.Assignment, Icons.Outlined.Assignment),
    GRADES("成绩", Icons.Filled.School, Icons.Outlined.School),
    ROOMS("教室", Icons.Filled.Place, Icons.Outlined.Place),
}

private sealed interface Overlay {
    data object None : Overlay
    data object Sync : Overlay
    data object Settings : Overlay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TIMETABLE) }
    var overlay by rememberSaveable { mutableStateOf(false) }
    var overlayIsSettings by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsState()

    // 上次崩溃过就在启动时直接把日志弹出来 —— 用户不用去设置里翻
    var crashText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashText = runCatching { CrashLogger.read(context) }.getOrNull()
    }

    crashText?.let { text ->
        AlertDialog(
            onDismissRequest = { crashText = null },
            title = { Text("上次运行崩溃了") },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall
                            .copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLogger.clear(context)
                    crashText = null
                }) { Text("清除并关闭") }
            },
        )
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 二级界面（同步 / 设置）打开时，系统返回键 / 返回手势应该回到主界面，
    // 而不是直接退出 App —— 之前没有这个处理，属于明显不符合预期的地方。
    BackHandler(enabled = overlay) { overlay = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                // 返回键放在左上角（主界面时不显示），符合 Android 的导航习惯。
                // 之前二级界面只能滚动到底部点「返回」，很容易找不到入口。
                navigationIcon = {
                    if (overlay) {
                        IconButton(onClick = { overlay = false }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                title = {
                    Text(
                        when {
                            overlay && overlayIsSettings -> "设置"
                            overlay -> "同步课表"
                            else -> "${tab.label} · 青农课表"
                        }
                    )
                },
                actions = {
                    IconButton(onClick = { overlay = true; overlayIsSettings = false }) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "同步")
                    }
                    IconButton(onClick = { overlay = true; overlayIsSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        bottomBar = {
            if (!overlay) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Tab.entries.forEach { t ->
                        val selected = tab == t
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tab = t },
                            // 之前这里把 label 同时当成 icon 和 label，等于显示了两遍；
                            // 现在图标用 Material 图标，选中/未选中用 filled / outlined 区分。
                            icon = {
                                Icon(
                                    imageVector = if (selected) t.iconSelected else t.iconNormal,
                                    contentDescription = t.label,
                                )
                            },
                            label = { Text(t.label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (overlay) {
            // 返回统一由 TopAppBar 左上角的箭头 / 系统返回键负责，
            // 二级界面内部不再放「返回」按钮。
            if (overlayIsSettings) {
                SettingsScreen(vm, modifier)
            } else {
                SyncScreen(vm, modifier)
            }
        } else {
            when (tab) {
                Tab.TIMETABLE -> TimetableScreen(vm, modifier)
                Tab.TODAY -> TodayScreen(vm, modifier)
                Tab.EXAMS -> ExamsScreen(vm, modifier)
                Tab.GRADES -> GradesScreen(vm, modifier)
                Tab.ROOMS -> ClassroomsScreen(vm, modifier)
            }
        }
    }
}
