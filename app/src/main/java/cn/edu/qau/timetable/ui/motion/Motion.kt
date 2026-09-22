package cn.edu.qau.timetable.ui.motion

import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// 非线性运动系统
//
// 和"线性/定时"动画的区别，以及这里怎么落地：
//
//  1. **物理建模而非定时** —— 位移、缩放一律走弹簧，时长是解出来的、不是规定的。
//     观感是"起步快、收尾长"的非线性曲线，而不是匀速或一张固定缓动表。
//
//  2. **速度连续（可打断）** —— 弹簧在目标值中途改变时会带着当前速度继续，不归零
//     重放。快速连点"下一周"、来回切标签时不会跳变或抖动。
//
//  3. **路径稳定** —— 位移弹簧取 dampingRatio = 0.9，几乎不过冲。这是有意为之：
//     位移过冲会让"怎么进来的就怎么回去"不成立，元素看起来在漂移。只有缩放这种
//     强调性动作才允许回弹。
//
//  4. **进快出慢的不对称** —— 退场用更硬的弹簧、更短的位移；进场留足收尾时间。
//     两者对称是最容易让动画显得廉价的地方。
//
//  5. **错落入场** —— 列表/网格按顺序拉开几十毫秒，避免整体"啪"地一起出现。
//
// 同时遵循系统「动画时长缩放」：用户在无障碍或开发者选项里关掉动画时，
// 所有弹簧降级为瞬时落位。
// ---------------------------------------------------------------------------

object QauMotion {

    // ------------------------------------------------------------------ 曲线
    //
    // 非线性缓动。用于少数不方便用弹簧的地方（需要显式 DurationMillis 的过渡，
    // 或与系统组件对齐时）。
    //
    // 这条曲线的形状是：起步极快、尾巴很长 —— 同一段时间里走过的路程前段远多于
    // 后段，这是"非线性"最直接的来源。

    /** 强调减速：起步快、收尾绵长。 */
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 标准：两端各收一点，用于短距离、小面积的变化。 */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 强调加速：用于退场，迅速让出画面。 */
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // ------------------------------------------------------------------ 弹簧

    /**
     * 位移/尺寸的默认弹簧。
     *
     * dampingRatio 0.9：略低于临界阻尼，收尾干净、几乎不过冲。
     * stiffness 380：触摸后一帧内就能看出响应，整体收敛约 250ms。
     */
    val Spatial: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.9f, stiffness = 380f)

    /** 同一族的"更快"版本：用于退场，以及需要迅速让位的次级元素。 */
    val SpatialFast: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.92f, stiffness = 700f)

    /** 更慢、更有重量感的版本：用于大面积位移。 */
    val SpatialSlow: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.88f, stiffness = 220f)

    /**
     * 缩放专用：允许轻微回弹。
     *
     * dampingRatio 0.62 会过冲约 3%，也就是 0.95 → 1.0 时会摸到 1.005 再落回来。
     * 这一点"Q 弹"是强调性动作的观感来源，所以只给缩放，不给位移。
     */
    val ScaleSpringy: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.62f, stiffness = 420f)

    /**
     * 透明度/颜色专用：临界阻尼 + 高刚度。
     * 透明度回弹是纯粹的错误观感（会"闪一下再实"），所以必须临界阻尼。
     */
    val Effects: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 1f, stiffness = 1300f)

    /** 更快的透明度变化，用于退场。 */
    val EffectsFast: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 1f, stiffness = 2400f)

    // ------------------------------------------------------- 像素位移（整型）
    //
    // slideIn/slideOut 这类过渡走的是 IntOffset，需要单独给一份弹簧。

    val SlideSpatial: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 0.9f, stiffness = 380f)

    val SlideSpatialFast: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = 900f)

    // ------------------------------------------------------------------ 尺度

    /** 列表/格子入场时的起始缩放。 */
    const val ENTER_SCALE = 0.95f

    /** 入场时的初始下沉量。 */
    val ENTER_OFFSET = 14.dp

    /** 选中态图标的放大倍率。 */
    const val SELECTED_ICON_SCALE = 1.06f

    /** 错落入场：相邻元素的延迟步长（毫秒）。 */
    const val STAGGER_STEP_MS = 26

    /** 错落延迟的封顶序号 —— 长列表后半段不再累加，否则要等到天荒地老。 */
    const val STAGGER_MAX_INDEX = 8

    /**
     * 视差推入时旧页面退出的比例。
     * 新页面按 100% 宽度推入，旧页面只退 28% —— 两者速度不同才产生纵深。
     */
    const val PARALLAX_EXIT_FRACTION = 0.28f
}

// ---------------------------------------------------------------------------
// 减弱动画支持
// ---------------------------------------------------------------------------

/**
 * 是否允许播放动画。
 *
 * 由系统「动画时长缩放」（Settings.Global.ANIMATOR_DURATION_SCALE）决定：
 * 用户在无障碍/开发者选项里关掉动画时变成 false，所有弹簧降级为瞬时落位。
 */
val LocalMotionEnabled = staticCompositionLocalOf { true }

/** 读取系统动画缩放：0 表示用户关掉了动画。 */
private fun systemMotionEnabled(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    return scale > 0f
}

/** Compose 里的 Context 可能被 ContextWrapper 包过，逐层拆到 LifecycleOwner 为止。 */
private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

/**
 * 跟随系统动画开关。
 *
 * 需要在 ON_RESUME 重新读一次：用户很可能是"切出去改设置、再切回来"，
 * 只在 composition 时读一次会一直用着旧值。
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(systemMotionEnabled(context)) }
    val owner = remember(context) { context.findLifecycleOwner() }

    DisposableEffect(owner, context) {
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = systemMotionEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

/**
 * 按当前开关把弹簧降级为瞬时。
 *
 * 只能在 @Composable 作用域调用，再把结果传进非 composable 的 transitionSpec
 * lambda —— transitionSpec 本身不是 composable。
 */
@Composable
fun <T> motionOf(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalMotionEnabled.current) spec else snap()

// ---------------------------------------------------------------------------
// 可复用修饰符
// ---------------------------------------------------------------------------

/**
 * 错落入场：透明度 + 上移 + 轻微缩放，按 [index] 依次延迟。
 *
 * [key] 变化时会重放 —— 周次切换后课表格子重新入场就靠这个。
 *
 * [animate] 为 false 时直接落位（不播）。列表项必须配合 [rememberEntranceWindow]
 * 使用，见那里的说明。
 *
 * 位移用 [QauMotion.Spatial]，透明度直接取同一个进度：因为 Spatial 几乎不过冲，
 * clamp 之后不会出现"先透后实"的闪烁。
 */
@Composable
fun Modifier.staggeredAppear(
    index: Int,
    key: Any? = Unit,
    animate: Boolean = true,
): Modifier {
    val enabled = LocalMotionEnabled.current && animate
    val progress = remember(key) { Animatable(if (enabled) 0f else 1f) }

    LaunchedEffect(key, enabled) {
        if (!enabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        val delayMs = index.coerceIn(0, QauMotion.STAGGER_MAX_INDEX) * QauMotion.STAGGER_STEP_MS
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, QauMotion.Spatial)
    }

    return this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * QauMotion.ENTER_OFFSET.toPx()
        val s = QauMotion.ENTER_SCALE + (1f - QauMotion.ENTER_SCALE) * p
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin.Center
    }
}

/**
 * 入场窗口：返回值在开屏后的一小段时间内为 true，之后翻成 false。
 *
 * 为什么需要它：LazyColumn 会销毁滚出视口的项，滚回来时重新组合。如果每项都无条件
 * 播放入场动画，来回滚动就会看到卡片不断"重新淡入" —— 这是典型的懒加载列表动画事故。
 *
 * 所以只有窗口内组合出来的项才播动画；窗口关闭后新组合出来的项直接落位。
 * 窗口长度 = 最后一档错落延迟 + 弹簧收敛时间，量出来大约是几百毫秒。
 *
 * 注意：屏幕因切换标签页而重建时会重新开窗 —— 这正是我们要的（换页 = 新一次入场）。
 */
@Composable
fun rememberEntranceWindow(itemCount: Int): Boolean {
    var open by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val staggerMs =
            itemCount.coerceIn(0, QauMotion.STAGGER_MAX_INDEX) * QauMotion.STAGGER_STEP_MS
        delay((staggerMs + 450).toLong())
        open = false
    }
    return open
}
