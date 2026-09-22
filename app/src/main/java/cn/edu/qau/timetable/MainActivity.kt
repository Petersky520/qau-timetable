package cn.edu.qau.timetable

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
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
import cn.edu.qau.timetable.ui.motion.LocalMotionEnabled
import cn.edu.qau.timetable.ui.motion.QauMotion
import cn.edu.qau.timetable.ui.motion.motionOf
import cn.edu.qau.timetable.ui.motion.rememberMotionEnabled
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
                // 系统里关掉动画（动画时长缩放 = 0）时，整套弹簧降级为瞬时落位。
                val motionEnabled = rememberMotionEnabled()
                CompositionLocalProvider(LocalMotionEnabled provides motionEnabled) {
                    AppRoot(vm)
                }
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

/**
 * 覆盖层。
 *
 * 原来用两个独立的 boolean 表示（overlay + overlayIsSettings），
 * 组合出过"两个都为 true 但语义模糊"的中间态。改成三值枚举后，
 * 状态空间是封闭的，也天然可以放进 rememberSaveable。
 */
private enum class Overlay { NONE, SYNC, SETTINGS }

// ---------------------------------------------------------------------------
// 过渡定义
//
// transitionSpec 不是 composable，所以弹簧规格（可能被"减弱动画"降级）
// 必须在外面用 motionOf() 取好再传进来。
// ---------------------------------------------------------------------------

/** 标签页之间：方向性横向滑动 + 淡入淡出，位移距离刻意做小（peer 级导航）。 */
private fun AnimatedContentTransitionScope<Tab>.tabSwitch(
    slide: FiniteAnimationSpec<IntOffset>,
    slideFast: FiniteAnimationSpec<IntOffset>,
    fade: FiniteAnimationSpec<Float>,
    fadeFast: FiniteAnimationSpec<Float>,
): ContentTransform {
    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
    return (
        slideInHorizontally(slide) { w -> dir * (w / 12) } + fadeIn(fade)
        ).togetherWith(
        slideOutHorizontally(slideFast) { w -> -dir * (w / 16) } + fadeOut(fadeFast)
    )
}

/**
 * 覆盖层推入：新页面整幅推入、旧页面只退 28%。
 *
 * 两者位移比例不同，视觉上就产生了纵深 —— 这是系统级"页面压栈"的观感，
 * 比两层用同样的速度平移要"有层次"得多。
 *
 * 进场给足时间用软弹簧，退场用硬弹簧迅速让位：不对称作曲。
 */
private fun AnimatedContentTransitionScope<Overlay>.pagePush(
    slide: FiniteAnimationSpec<IntOffset>,
    slideFast: FiniteAnimationSpec<IntOffset>,
    fade: FiniteAnimationSpec<Float>,
    fadeFast: FiniteAnimationSpec<Float>,
): ContentTransform {
    val entering = targetState != Overlay.NONE
    val parallax: (Int) -> Int =
        { w -> -(w * QauMotion.PARALLAX_EXIT_FRACTION).toInt() }

    return if (entering) {
        (slideInHorizontally(slide) { it } + fadeIn(fade)).togetherWith(
            slideOutHorizontally(slideFast, parallax) +
                fadeOut(fadeFast) +
                // 被压到下层的一页轻轻缩小，进一步强化纵深
                scaleOut(targetScale = 0.985f, animationSpec = fade)
        )
    } else {
        (slideInHorizontally(slide, parallax) + fadeIn(fade)).togetherWith(
            slideOutHorizontally(slideFast) { it } + fadeOut(fadeFast)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TIMETABLE) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }

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

    // 被"减弱动画"降级过的弹簧，只取一次，供下面非 composable 的 transitionSpec 使用。
    val slideSpec = motionOf(QauMotion.SlideSpatial)
    val slideFastSpec = motionOf(QauMotion.SlideSpatialFast)
    val fadeSpec = motionOf(QauMotion.Effects)
    val fadeFastSpec = motionOf(QauMotion.EffectsFast)

    val title = when (overlay) {
        Overlay.SETTINGS -> "设置"
        Overlay.SYNC -> "同步课表"
        Overlay.NONE -> "${tab.label} · 青农课表"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                title = {
                    // 标题整块上滚换文案。
                    //
                    // 这里刻意用**整高**位移、且进出共用一条弹簧：AnimatedContent 会把
                    // 子项裁剪到容器边界，半高位移会让新旧文字在中间同时可见、互相压住；
                    // 前后速度不一致则会在中间漏出背景。整高 + 同速 = 一次干净的"翻页"，
                    // 旧字完全滚出的那一刻新字刚好滚满。
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (slideInVertically(slideSpec) { h -> h } + fadeIn(fadeSpec))
                                .togetherWith(
                                    slideOutVertically(slideSpec) { h -> -h } +
                                        fadeOut(fadeSpec)
                                )
                        },
                        label = "title",
                    ) { t -> Text(t) }
                },
                actions = {
                    IconButton(onClick = {
                        overlay = Overlay.SYNC
                    }) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "同步")
                    }
                    IconButton(onClick = {
                        overlay = Overlay.SETTINGS
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        bottomBar = {
            // 底栏不是"消失"，是向下退出 —— 覆盖层推入时底栏让位，
            // 反过来退场时它再从下方回来。
            AnimatedVisibility(
                visible = overlay == Overlay.NONE,
                enter = slideInVertically(slideSpec) { h -> h } + fadeIn(fadeSpec),
                exit = slideOutVertically(slideFastSpec) { h -> h } + fadeOut(fadeFastSpec),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Tab.entries.forEach { t ->
                        val selected = tab == t && overlay == Overlay.NONE
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tab = t },
                            // 已选中的图标轻微放大，给"选上了"一点分量。
                            // 用带过冲的缩放弹簧 —— 这是全局唯一允许回弹的地方，
                            // 因为它不涉及位置，不会让元素看起来在漂。
                            icon = {
                                val iconScale by animateFloatAsState(
                                    targetValue = if (selected) {
                                        QauMotion.SELECTED_ICON_SCALE
                                    } else {
                                        1f
                                    },
                                    animationSpec = QauMotion.ScaleSpringy,
                                    label = "navIconScale",
                                )
                                Icon(
                                    imageVector = if (selected) t.iconSelected else t.iconNormal,
                                    contentDescription = t.label,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
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
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = overlay,
                transitionSpec = { pagePush(slideSpec, slideFastSpec, fadeSpec, fadeFastSpec) },
                label = "overlay",
            ) { ov ->
                when (ov) {
                    Overlay.NONE -> TabHost(
                        tab = tab,
                        vm = vm,
                        onTabChange = { tab = it },
                        slideSpec = slideSpec,
                        slideFastSpec = slideFastSpec,
                        fadeSpec = fadeSpec,
                        fadeFastSpec = fadeFastSpec,
                    )

                    Overlay.SYNC -> SyncScreen(
                        vm,
                        Modifier.fillMaxSize(),
                        onBack = { overlay = Overlay.NONE },
                    )

                    Overlay.SETTINGS -> SettingsScreen(
                        vm,
                        Modifier.fillMaxSize(),
                        onBack = { overlay = Overlay.NONE },
                    )
                }
            }
        }
    }
}

/** 标签页宿主：五个平级页面之间的方向性切换。 */
@Composable
private fun TabHost(
    tab: Tab,
    vm: MainViewModel,
    onTabChange: (Tab) -> Unit,
    slideSpec: FiniteAnimationSpec<IntOffset>,
    slideFastSpec: FiniteAnimationSpec<IntOffset>,
    fadeSpec: FiniteAnimationSpec<Float>,
    fadeFastSpec: FiniteAnimationSpec<Float>,
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = { tabSwitch(slideSpec, slideFastSpec, fadeSpec, fadeFastSpec) },
        label = "tab",
    ) { t ->
        when (t) {
            Tab.TIMETABLE -> TimetableScreen(vm, Modifier.fillMaxSize())
            Tab.TODAY -> TodayScreen(vm, Modifier.fillMaxSize())
            Tab.EXAMS -> ExamsScreen(vm, Modifier.fillMaxSize())
            Tab.GRADES -> GradesScreen(vm, Modifier.fillMaxSize())
            Tab.ROOMS -> ClassroomsScreen(vm, Modifier.fillMaxSize())
        }
    }
}
