package cn.edu.qau.timetable.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 玻璃底栏需要的那条「底带」的几何计算。
 *
 * ## 为什么需要它
 *
 * 玻璃底栏只能看到自己背后那一块 —— [GlassPanel] 拿到录像后先 `clipRect` + `translate`，
 * 把录像里「底栏所在的那一块」裁出来。也就是说录像里其余部分**从来不会被采样**。
 *
 * 而 `MainActivity` 早期录的是整屏：内容每帧被栅格化进一张整屏离屏纹理，再整屏贴回屏幕。
 * 内容越重（文字越多、卡片越多），这次多出来的全屏填充越吃 GPU —— 这就是用户报的
 * 「文本多的界面掉帧」的主要来源。
 *
 * 于是录像高度从「整屏」改成「底栏 + 下边距 + 手势条 + 模糊余量」，
 * 本对象负责这三件纯计算，**不碰任何 Compose 运行时**，所以能直接在 JVM 单测里钉住：
 *
 *  1. [heightDp]：底带该多高；
 *  2. [bandTop]：底带在内容层里从哪个 y 开始（= 内容高度 − 底带高度）；
 *  3. [originInRoot]：底带左上角在 root 里的坐标 —— 玻璃面板靠它把自己的采样位置对齐。
 *
 * 把这三件算错任意一件，表现都是「模糊内容整体错位」，而错位很难靠肉眼描述清楚，
 * 所以宁可把它们抽出来写出断言。
 */
object BackdropBand {

    /**
     * 通栏底栏的高度。
     *
     * Material3 的 `NavigationBar` 默认高度就是 80dp（它再把手势条内边距叠上去），
     * 这里不能写成 `FloatingBarHeight`：两者是不同款式、不同高度。
     */
    val BarHeightDp: Dp = 80.dp

    /**
     * 底带在底栏之上多留的余量。
     *
     * 为什么需要：底栏有圆角、1dp 边框，而 [GlassPanel] 对底带做的是 24dp 高斯模糊 ——
     * 模糊的软边会采样到本体之外。不留余量的话，底栏上沿会出现一条**没被模糊**的硬边
     * （看起来像"玻璃上方贴了一条不透明的纸条"）。
     *
     * 56dp 大于 24dp 模糊半径的两倍，足够覆盖软边。
     */
    val BleedDp: Dp = 56.dp

    /**
     * 底带高度 = 底栏高 + 手势条内边距 + 模糊余量。
     *
     * @param floatingBar 是否悬浮款（悬浮款 = 本体高度 + 离底边距）。
     * @param navInset    手势小横条占掉的高度；通栏底栏会把它算进自己的内边距，
     *                    悬浮底栏则是我们自己在外层留的，两种都要加进来。
     */
    fun heightDp(floatingBar: Boolean, navInset: Dp): Dp {
        val bar = if (floatingBar) FloatingBarHeight + FloatingBarMargin else BarHeightDp
        return bar + navInset + BleedDp
    }

    /**
     * 底带顶边在内容层中的 y 坐标。
     *
     * 底带永远贴着内容层的**底部**（底栏就在屏幕底下），所以这是 `内容高度 − 底带高度`。
     * 内容比底带还矮时（横屏 + 键盘弹起这种极端情况）夹到 0：
     * 宁可录整屏，也不能得到负的起点。
     */
    fun bandTop(contentHeight: Float, bandHeight: Float): Float =
        (contentHeight - bandHeight).coerceAtLeast(0f)

    /** 底带实际高度。内容比底带矮时就是内容高度。 */
    fun bandHeight(contentHeight: Float, bandHeight: Float): Float =
        bandHeight.coerceIn(0f, contentHeight.coerceAtLeast(0f))

    /**
     * 底带左上角在 root 中的坐标。
     *
     * `contentOrigin` 是内容层（也就是原来"整屏录像"的左上角）在 root 里的位置。
     * 录像内容从 `bandTop` 才开始，所以原点要相应下移这么多。
     *
     * 玻璃面板算平移量时用的是「录像原点 − 面板原点」。传整屏原点进去，
     * 会让模糊内容整体上移一条底带的高度 —— 这就是这个函数存在的唯一理由。
     */
    fun originInRoot(contentOrigin: Offset, contentHeight: Float, bandHeight: Float): Offset =
        contentOrigin + Offset(0f, bandTop(contentHeight, bandHeight))
}
