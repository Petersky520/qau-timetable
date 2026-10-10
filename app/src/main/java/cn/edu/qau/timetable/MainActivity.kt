package cn.edu.qau.timetable

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.NavigationBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
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
import cn.edu.qau.timetable.ui.glass.FloatingBarHeight
import cn.edu.qau.timetable.ui.glass.FloatingBarMargin
import cn.edu.qau.timetable.ui.glass.GlassPanel
import cn.edu.qau.timetable.ui.glass.LocalFloatingBarReserve
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
            initializer { MainViewModel(container.repo, container.reminders, container.silence) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 全面屏沉浸：内容一直画到系统栏底下，手势小横条浮在内容之上。
        // 必须赶在 super.onCreate 之前调 —— 之后再调，窗口已经按系统栏让过位了。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureChannel(this)

        setContent {
            val settings by vm.settings.collectAsState()
            // 悬浮底栏会盖住内容，滚动内容必须自己让出这一段 —— 否则最后一条
            // 会被底栏永久挡住、滚不出来。各页面从 LocalFloatingBarReserve 读它。
            val navInset =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // 底栏的真实高度交给它自己量。M3 的 NavigationBar 内部高度是写死的，
            // 这里若硬压一个高度，轻则留白对不上，重则把标签裁掉。
            var barHeight by remember { mutableStateOf(FloatingBarHeight) }
            // 和 AppRoot 里同一套判定：底栏是"浮在内容之上"还是"占位"。
            val barOverlaysContent =
                settings.floatingBottomBar || settings.glassEffect.blursBackdrop
            QauTheme(
                style = settings.uiStyle,
                dynamicColor = settings.dynamicColor,
            ) {
                // 系统里关掉动画（动画时长缩放 = 0）时，整套弹簧降级为瞬时落位。
                val motionEnabled = rememberMotionEnabled()
                CompositionLocalProvider(
                    LocalMotionEnabled provides motionEnabled,
                    // 只有浮起来的底栏需要滚动内容给它让位；占位式底栏由 Scaffold 正常占位。
                    // 通栏款自己已经把手势条高度算进 barHeight 里了，不用再加一遍。
                    LocalFloatingBarReserve provides if (barOverlaysContent) {
                        barHeight + if (settings.floatingBottomBar) {
                            FloatingBarMargin + navInset
                        } else {
                            0.dp
                        }
                    } else {
                        0.dp
                    },
                ) {
                    AppRoot(vm, onBarHeightChange = { barHeight = it })
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
private fun AppRoot(vm: MainViewModel, onBarHeightChange: (Dp) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TIMETABLE) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val snackbar = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsState()
    val settings by vm.settings.collectAsState()

    // ---- 玻璃底栏 ----
    //
    // contentLayer 是"内容的一份录像"：玻璃底栏靠它才能把背后的东西模糊着画出来，
    // 因为 Compose 的 Modifier.blur() 只模糊元素自己，看不到背后。
    val contentLayer = rememberGraphicsLayer()
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    // 手势小横条占掉的高度。底栏要浮在它上面，滚动内容也要为它留出空白。
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 底栏是"浮在内容之上"还是"占位"：
    //   悬浮底栏              → 永远浮着（本来就是要让内容从底下穿过去）
    //   原版底栏 + 有玻璃效果 → 也得浮着，不浮就没有东西可模糊
    //   原版底栏 + 无效果     → 回到 Scaffold 正常占位、完全不透明的那副样子
    val barOverlaysContent =
        settings.floatingBottomBar || settings.glassEffect.blursBackdrop

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
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }

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
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                // 返回键放在左上角（主界面时不显示），符合 Android 的导航习惯。
                // 之前二级界面只能滚动到底部点「返回」，很容易找不到入口。
                navigationIcon = {
                    if (overlay != Overlay.NONE) {
                        IconButton(onClick = { overlay = Overlay.NONE }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
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
        // 原版底栏，且**没有**玻璃效果时才走这里：贴底、占位、不透明。
        // 一旦选了高斯模糊，底栏就必须浮起来让内容穿过，
        // 否则它背后永远是空的，模糊无从谈起。
        bottomBar = {
            if (!barOverlaysContent) {
                AnimatedVisibility(
                    visible = overlay == Overlay.NONE,
                    enter = slideInVertically(slideSpec) { h -> h } + fadeIn(fadeSpec),
                    exit = slideOutVertically(slideFastSpec) { h -> h } + fadeOut(fadeFastSpec),
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        NavBarItems(tab = tab, overlay = overlay, onSelect = { tab = it })
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                // 只让开顶部：底部要留给内容「从底栏底下穿过去」，
                // 否则玻璃背后什么都没有，模糊也就无从谈起。
                .padding(top = padding.calculateTopPadding())
                // 换成占位式底栏时则相反 —— 它要占位，内容不能钻到底下。
                .padding(
                    bottom = if (barOverlaysContent) 0.dp else padding.calculateBottomPadding()
                )
                .fillMaxSize()
                // edge-to-edge 之后 adjustResize 不再自动缩放窗口（API 30+ 的语义变了），
                // 输入法内边距必须由 App 自己消费 —— 少了这一句，
                // 登录页的学号/密码框会被键盘整个盖住。
                .imePadding(),
        ) {
            // 内容层：一边画到屏幕上，一边录进 graphicsLayer，供玻璃底栏取用。
            // 底栏在这个 Box **之外** —— 否则它会把自己也录进去、自己模糊自己。
            Box(
                Modifier
                    .fillMaxSize()
                    // 只有真的要画模糊时，才需要这份"背后内容"的录像。
                    // 选「无」时底栏本来就不透明，录了也没人看 —— 这一句省掉的是
                    // 每帧一次整屏录屏，是这套效果里唯一实质性的开销。
                    .then(
                        if (settings.glassEffect.blursBackdrop) {
                            Modifier
                                .onGloballyPositioned { backdropOrigin = it.positionInRoot() }
                                .drawWithContent {
                                    contentLayer.record { this@drawWithContent.drawContent() }
                                    drawLayer(contentLayer)
                                }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                AnimatedContent(
                    targetState = overlay,
                    transitionSpec = {
                        pagePush(slideSpec, slideFastSpec, fadeSpec, fadeFastSpec)
                    },
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

                        // 返回统一由顶栏左上角的箭头 / 系统返回键负责，
                        // 二级界面内部不再放「返回」按钮。
                        Overlay.SYNC -> SyncScreen(vm, Modifier.fillMaxSize())

                        Overlay.SETTINGS -> SettingsScreen(vm, Modifier.fillMaxSize())
                    }
                }
            }

            // 悬浮玻璃底栏。
            //
            // 不再是 Scaffold 的 bottomBar —— 那样会占掉一条高度，内容永远到不了它底下，
            // 玻璃就没有东西可模糊。现在它盖在内容之上，内容从底下穿过去。
            // 它也不是"消失"，是向下退出：覆盖层推入时让位，退场时再从下方回来。
            if (barOverlaysContent) {
                // 浮起来的那种：悬浮款是圆角胶囊、留边距；原版款是通栏贴底。
                val floating = settings.floatingBottomBar
                AnimatedVisibility(
                    visible = overlay == Overlay.NONE,
                    enter = slideInVertically(slideSpec) { h -> h } + fadeIn(fadeSpec),
                    exit = slideOutVertically(slideFastSpec) { h -> h } + fadeOut(fadeFastSpec),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    GlassPanel(
                        effect = settings.glassEffect,
                        backdrop = contentLayer,
                        backdropOrigin = backdropOrigin,
                        // 染色取主题的 surfaceVariant，而不是 surfaceContainer：
                        // MIUI X 下 surfaceContainer 是**纯白**，白叠白会把模糊冲得看不见；
                        // surfaceVariant 在 M3 是暖灰、在 MIUI X 是浅灰，两套都有对比度。
                        // 浓淡由 GlassPanel 按效果决定，这里只给色相。
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        shape = if (floating) RoundedCornerShape(28.dp) else RectangleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (floating) {
                                    Modifier
                                        .padding(horizontal = 14.dp)
                                        .padding(bottom = FloatingBarMargin + navInset)
                                } else {
                                    Modifier
                                }
                            )
                            // 量的是内层（玻璃本体）的高度，padding 在外侧，
                            // 所以拿到的是底栏真实高度，可以回填给滚动页面的留白。
                            .onSizeChanged {
                                onBarHeightChange(with(density) { it.height.toDp() })
                            },
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            // 悬浮款的手势条间距已经由上面那个 padding 处理；
                            // 通栏款要一直贴到屏幕最底（手势条压在它上面），
                            // 所以让 NavigationBar 自己去让系统栏内边距。
                            windowInsets = if (floating) {
                                WindowInsets(0, 0, 0, 0)
                            } else {
                                NavigationBarDefaults.windowInsets
                            },
                        ) {
                            NavBarItems(tab = tab, overlay = overlay, onSelect = { tab = it })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底栏里的五个标签项。
 *
 * 原版底栏和悬浮底栏共用这一份 —— 两套底栏的差别只在"容器长什么样"，
 * 条目本身没有理由维护两份，否则改一处漏一处。
 */
@Composable
private fun RowScope.NavBarItems(
    tab: Tab,
    overlay: Overlay,
    onSelect: (Tab) -> Unit,
) {
    Tab.entries.forEach { t ->
        val selected = tab == t && overlay == Overlay.NONE
        NavigationBarItem(
            selected = selected,
            onClick = { onSelect(t) },
            // 已选中的图标轻微放大，给"选上了"一点分量。
            // 用带过冲的缩放弹簧 —— 这是全局唯一允许回弹的地方，
            // 因为它不涉及位置，不会让元素看起来在漂。
            icon = {
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) QauMotion.SELECTED_ICON_SCALE else 1f,
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
