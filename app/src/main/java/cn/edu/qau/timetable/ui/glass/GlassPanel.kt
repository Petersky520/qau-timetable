package cn.edu.qau.timetable.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.core.GlassEffect

/**
 * 底栏会盖住内容，滚动内容必须自己留出这么多底部空白，
 * 否则最后一条会被底栏永久挡住、滚不出来。
 *
 * 由 `AppRoot` 提供（底栏高度 + 下边距 + 手势条高度），各滚动页面读它。
 */
val LocalFloatingBarReserve = compositionLocalOf { 0.dp }

/** 悬浮底栏本体的高度（不含下边距与手势条）。 */
val FloatingBarHeight = 64.dp

/** 悬浮底栏离屏幕底部（手势条之上）的间距。 */
val FloatingBarMargin = 10.dp

/**
 * 把背后内容模糊着画出来的玻璃面板。
 *
 * ## 为什么需要 [backdrop] 这个参数
 *
 * Compose 的 `Modifier.blur()` 模糊的是**元素自己**，不是它背后的东西 ——
 * 想让玻璃透出"背后被模糊的内容"，就得先有一份背后内容的副本。
 * [GraphicsLayer]（Compose 1.7+）正好能做这件事：内容在画到屏幕上的同时
 * 被录进一个 layer，玻璃面板再把这个 layer 平移到自己所在的位置、
 * 裁进形状里、模糊后当作自己的底。
 *
 * ## 坐标对齐
 *
 * [backdropOrigin] 是那份录像左上角在 root 里的位置，[selfOrigin] 是面板自己的。
 * layer 里的坐标是相对录像左上角的，所以平移量 = 录像原点 − 面板原点。
 * 两个值都在运行时量出来，因此不管外面套了几层 Box / padding 都不会错位。
 */
@Composable
fun GlassPanel(
    effect: GlassEffect,
    backdrop: GraphicsLayer,
    backdropOrigin: Offset,
    tint: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    var selfOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .onGloballyPositioned { selfOrigin = it.positionInRoot() }
            // clip 放在最外层：先把模糊的软边裁掉，再交给形状
            .clip(shape),
    ) {
        // ① 背后内容的模糊副本
        //
        // ⚠️ 修饰符顺序反了会**完全失效**：`blur` 必须在外层。
        // `Modifier.a.b` 里 a 包住 b，而 graphicsLayer（blur 就是它）建立的图层
        // 只包住**它之后**绘制的那些内容。写成 `.drawWithContent{}.blur()` 时，
        // 副本是外层画的、blur 图层里空无一物 —— 模糊作用在空气上，
        // 面板就退化成一块不透明的白板。
        //
        // 选「无」时整块跳过：染色本来就不透明，画了也看不见。
        if (effect.blursBackdrop) {
            Box(
                Modifier
                    .matchParentSize()
                    .blur(24.dp, BlurredEdgeTreatment.Unbounded)
                    .drawWithContent {
                        val dx = backdropOrigin.x - selfOrigin.x
                        val dy = backdropOrigin.y - selfOrigin.y
                        clipRect {
                            translate(dx, dy) { drawLayer(backdrop) }
                        }
                    },
            )
        }

        // ② 染色
        Box(
            Modifier
                .matchParentSize()
                .background(if (effect.blursBackdrop) tint.copy(alpha = 0.62f) else tint),
        )

        // ③ 极淡的一圈边，否则一整块平涂看不出边界在哪
        Box(
            Modifier
                .matchParentSize()
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.18f), shape = shape),
        )

        content()
    }
}
