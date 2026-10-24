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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DateRange
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.edu.qau.timetable.notify.ReminderReceiver
import cn.edu.qau.timetable.ui.ExamsScreen
import cn.edu.qau.timetable.ui.GradesScreen
import cn.edu.qau.timetable.ui.MainViewModel
import cn.edu.qau.timetable.ui.SettingsScreen
import cn.edu.qau.timetable.ui.SyncScreen
import cn.edu.qau.timetable.ui.TimetableScreen
import cn.edu.qau.timetable.ui.TodayScreen
import cn.edu.qau.timetable.ui.glass.BackdropBand
import cn.edu.qau.timetable.ui.glass.FloatingBarHeight
import cn.edu.qau.timetable.ui.glass.FloatingBarMargin
import cn.edu.qau.timetable.ui.glass.GlassPanel
import cn.edu.qau.timetable.ui.glass.LocalFloatingBarReserve
import cn.edu.qau.timetable.ui.KaoyanFeature
import cn.edu.qau.timetable.ui.KaoyanHost
import cn.edu.qau.timetable.ui.motion.LocalMotionEnabled
import cn.edu.qau.timetable.ui.motion.QauMotion
import cn.edu.qau.timetable.ui.motion.motionOf
import cn.edu.qau.timetable.ui.motion.rememberMotionEnabled
import cn.edu.qau.timetable.ui.theme.QauTheme
import cn.edu.qau.timetable.util.CrashLogger
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    // 说明：这里原来 override attachBaseContext 把 locale 强制成简体中文
    // （想规避 Android 7 时代 WebView 重置 Activity locale 的老问题）。
    // 那个问题早已修复，而且 Chrome 从不强制 locale ——
    // 为了排查"输入倒序"，把这层"浏览器没有的行为"删掉。

    private val vm: MainViewModel by viewModels {
        val container = (application as QauApp).container
        viewModelFactory {
            initializer {
                MainViewModel(
                    repo = container.repo,
                    reminders = container.reminders,
                    silence = container.silence,
                    kaoyan = container.kaoyan,
                )
            }
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
            // 用的是 effectiveGlassEffect 而不是用户选的 glassEffect ——
            // 开了性能模式时玻璃被压平成纯色，底栏就不再"浮着"，这一处的留白
            // 必须和 AppRoot 同源，否则内容会多让或少让一段。
            val barOverlaysContent =
                settings.floatingBottomBar || settings.effectiveGlassEffect.blursBackdrop
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
    // 「考研」用书堆图标：School（学士帽）已经被成绩占用，
    // 两个标签用同一个图标会让人分不清自己在哪一页。
    KAOYAN("考研", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
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
    // 内容层量出来的「在哪、多大」。**不**直接存 backdropOrigin：
    // 底带高度是算出来的（随悬浮/通栏切换、手势条内边距变化），
    // 而 onGloballyPositioned 只在位置/尺寸变化时回调 ——
    // 只改底带高度（尺寸没变）时那个缓存的原点就会过期，表现为模糊内容错位。
    // 所以存原始测量值，原点每次都从它 + 当前底带高度重新推。
    var contentBox by remember { mutableStateOf(Rect.Zero) }
    // 手势小横条占掉的高度。底栏要浮在它上面，滚动内容也要为它留出空白。
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 实际生效的底栏效果。性能模式在这里把 GAUSSIAN 压平成 NONE：
    // 用户选的仍是模糊，但渲染走纯色 —— 不录屏、不模糊。
    val effectiveGlass = settings.effectiveGlassEffect

    // 底栏是"浮在内容之上"还是"占位"：
    //   悬浮底栏              → 永远浮着（本来就是要让内容从底下穿过去）
    //   原版底栏 + 有玻璃效果 → 也得浮着，不浮就没有东西可模糊
    //   原版底栏 + 无效果     → 回到 Scaffold 正常占位、完全不透明的那副样子
    val barOverlaysContent =
        settings.floatingBottomBar || effectiveGlass.blursBackdrop

    // 🔴 整屏录屏的开关 —— 这是这个 App 里唯一"每帧都在烧 GPU"的地方。
    //
    // 只有同时满足下面两条才值得录：
    //   ① 效果本身要模糊（性能模式 / 用户选「无」时为 false）
    //   ② 底栏真的在屏幕上（覆盖层推入时底栏是滑出屏幕的）
    //
    // 第 ② 条以前没判：同步页 / 设置页是**盖在内容之上**的整页，
    // 底栏早已不可见，可内容层仍然每帧整屏录一遍再画一遍。
    // 设置页本身就是一堆 Switch 和输入框，滚起来最吃这个亏。
    val recordBackdrop = effectiveGlass.blursBackdrop && overlay == Overlay.NONE

    // ------------------------------------------------------------ 录像只录底栏那一条
    //
    // 玻璃底栏贴在屏幕底部，它**只采样自己背后那一条**的像素：GlassPanel 拿到录像后
    // 做的第一件事就是 clipRect + translate，把录像里"底栏所在的那一块"裁出来。
    // 也就是说，屏幕上方 90% 的高度录了却从来没有被读过。
    //
    // 但以前录的是整屏：`contentLayer.record { drawContent() }` 里 layer 的尺寸
    // 就是内容层尺寸，`drawLayer` 再把这张整屏纹理贴回屏幕。代价是每帧：
    //   ① 整屏内容栅格化进一张离屏纹理（约 2.6M 像素的 GPU 填充）；
    //   ② 再把这张纹理整屏合成一次。
    // 内容越重（文字越多、卡片越多），①的填充率越吃紧 —— 这就是"文本多的界面掉帧"。
    //
    // 现在把录像的高度压成「底栏 + 下边距 + 手势条 + 模糊余量」：
    //   ① 离屏纹理缩小到约一成面积，GPU 填充与显存带宽同比例下降；
    //   ② 去掉了"整屏纹理贴回屏幕"那一次合成 —— 内容直接画到屏幕上。
    //
    // 代价是内容在一次绘制里被走两遍（一遍到屏幕、一遍裁剪进底带）。
    // 第二遍被 clipRect 限制在底带内，视口外的绘制命令会被 Skia 按边界早退，
    // 实际栅格化的像素只有底带那一块。这个取舍是有依据的：
    // 3.3 的实测证明"去掉整屏录屏"就能立刻恢复流畅 —— 说明瓶颈在 GPU 填充率，
    // 而不是 CPU 侧的绘制命令下发。
    //
    // +56dp 是余量：底栏圆角、边框和 24dp 高斯模糊的软边都要采样到本体之外，
    // 少这一圈会在底栏上沿看到一条没被模糊的硬边。
    val backdropBandHeightDp: Dp = remember(settings.floatingBottomBar, navInset) {
        BackdropBand.heightDp(settings.floatingBottomBar, navInset)
    }
    val backdropBandHeightPx = with(density) { backdropBandHeightDp.toPx() }

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
        Overlay.NONE -> "${tab.label} · 青小农"
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
            // 内容层：画到屏幕上，**同时**只为玻璃底栏录一条"底带"。
            // 底栏在这个 Box **之外** —— 否则它会把自己也录进去、自己模糊自己。
            Box(
                Modifier
                    .fillMaxSize()
                    // 只有真的要画模糊、且底栏真的可见时，才需要这份"背后内容"的录像。
                    //
                    // 这一层以前是无条件挂在内容层上的（只要用户选了高斯模糊），
                    // 于是**每一帧**都把整屏录进 GraphicsLayer 再画一遍：
                    // 考研页那种「意向卡 + 分三组结果卡 + 进度条」的重内容，
                    // 叠上整屏录屏就是用户报的「点进考研掉帧比较严重」。
                    //
                    // 现在两个降级条件（见上面 recordBackdrop 的说明）任一成立就整段跳过，
                    // 修饰符链里连 drawWithContent 都不存在 —— 不是"少画一点"，是"压根不画"。
                    .then(
                        if (recordBackdrop) {
                            Modifier
                                .onGloballyPositioned { coords ->
                                    // 只存原始测量值。底带原点在下面用 contentBox +
                                    // 当前底带高度现算（见 contentBox 的说明）：
                                    // 在这里直接算会漏掉「只改底带高度」的那种变化。
                                    contentBox = Rect(
                                        offset = coords.positionInRoot(),
                                        size = Size(
                                            coords.size.width.toFloat(),
                                            coords.size.height.toFloat(),
                                        ),
                                    )
                                }
                                .drawWithContent {
                                    val bandH = min(backdropBandHeightPx, size.height)
                                        .coerceAtLeast(1f)
                                    val bandTop = BackdropBand.bandTop(size.height, bandH)
                                    val bandW = size.width
                                    val layerW = bandW.roundToInt().coerceAtLeast(1)
                                    val layerH = bandH.roundToInt().coerceAtLeast(1)

                                    // ① 内容照常画到屏幕上。
                                    //    以前这一步是"录进整屏 layer、再把 layer 贴回屏幕"，
                                    //    等于每帧强制一次全屏离屏渲染 + 一次全屏贴图。
                                    drawContent()

                                    // ② 只为底栏录一条底带：把内容整体上移 bandTop，
                                    //    于是底栏背后那一段正好落进 size=(layerW × layerH) 的 layer。
                                    //    底栏只采样这一条，上面的内容录了也永远用不到。
                                    contentLayer.record(IntSize(layerW, layerH)) {
                                        clipRect(left = 0f, top = 0f, right = bandW, bottom = bandH) {
                                            translate(top = -bandTop) {
                                                this@drawWithContent.drawContent()
                                            }
                                        }
                                    }
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
                            // 考研页里「去设置」要真的能跳过去：设置是 AppRoot 的覆盖层，
                            // 只有这里能把 overlay 切过去。
                            onOpenSettings = { overlay = Overlay.SETTINGS },
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
                        // 传生效后的效果，而不是用户选的：性能模式开着时这里收到 NONE，
                        // GlassPanel 于是走纯色分支（不建模糊图层、不画录像）。
                        effect = effectiveGlass,
                        backdrop = contentLayer,
                        // 录像里装的是「底带」，所以参考原点必须是**底带左上角**，
                        // 不是内容层左上角 —— 传错了模糊内容会整体错开一条底带的高度。
                        // 这里每次重组现算，而不是在 onGloballyPositioned 里缓存：
                        // 底带高度变了（切悬浮/通栏、手势条内边距变化）但内容尺寸没变时，
                        // 那个回调根本不会触发。
                        backdropOrigin = BackdropBand.originInRoot(
                            contentOrigin = contentBox.topLeft,
                            contentHeight = contentBox.height,
                            bandHeight = min(backdropBandHeightPx, contentBox.height),
                        ),
                        // 录像可能是旧的（比如刚关掉性能模式）。底栏只在 recordBackdrop
                        // 为真的那些帧里才有正确的录像可画，其余时候退化成纯色块，
                        // 不会画出一份过期的画面。
                        backdropFresh = recordBackdrop,
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
    onOpenSettings: () -> Unit,
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
            // 走接缝而不是直接引用 KaoyanScreen：Lite 变体里这个调用点
            // 指向的是 flavor 的 KaoyanHost 实现，考研整条依赖链因此不可达，
            // 会被 R8 裁掉（见 app/src/{full,lite}/.../KaoyanHost.kt）。
            Tab.KAOYAN -> KaoyanHost(
                vm = vm,
                modifier = Modifier.fillMaxSize(),
                onOpenSettings = onOpenSettings,
            )
        }
    }
}
