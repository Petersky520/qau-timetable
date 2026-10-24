package cn.edu.qau.timetable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.ui.glass.BackdropBand
import cn.edu.qau.timetable.ui.glass.FloatingBarHeight
import cn.edu.qau.timetable.ui.glass.FloatingBarMargin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玻璃底栏「底带」几何。
 *
 * 这些值算错的后果都不是崩溃，而是**模糊内容整体错位**或者**底栏上沿出现一条硬边** ——
 * 都是肉眼难描述、又很容易在后续改动里悄悄回归的那种问题。所以逐条钉住：
 * bandTop 决定录什么，originInRoot 决定玻璃从哪里采样，两者必须严格互补。
 */
class BackdropBandTest {

    private fun eq(expected: Float, actual: Float, tol: Float = 0.001f) =
        assertEquals(expected, actual, tol)

    @Test
    fun `悬浮底栏的底带高度等于本体加边距加手势条加余量`() {
        val inset = 24.dp
        val expected = FloatingBarHeight + FloatingBarMargin + inset + BackdropBand.BleedDp
        assertEquals(expected, BackdropBand.heightDp(floatingBar = true, navInset = inset))
    }

    @Test
    fun `通栏底栏的底带高度用 80dp 而不是悬浮款的 64dp`() {
        val inset = 0.dp
        val flat = BackdropBand.heightDp(floatingBar = false, navInset = inset)
        val floating = BackdropBand.heightDp(floatingBar = true, navInset = inset)
        assertEquals(BackdropBand.BarHeightDp + inset + BackdropBand.BleedDp, flat)
        // 通栏是 80dp，悬浮是 64+10dp，两者不能混用 —— 混用会让模糊内容整体错位。
        assertTrue("通栏底带应比悬浮底带高", flat > floating)
    }

    @Test
    fun `底带贴着内容层底部`() {
        eq(expected = 2400f - 200f, actual = BackdropBand.bandTop(2400f, 200f))
        eq(expected = 200f, actual = BackdropBand.bandHeight(2400f, 200f))
    }

    @Test
    fun `内容比底带还矮时不出现负数`() {
        // 横屏 + 键盘弹起这类极端尺寸：宁可按整屏录，也不能拿到负的起点/负的高度。
        eq(expected = 0f, actual = BackdropBand.bandTop(120f, 200f))
        eq(expected = 120f, actual = BackdropBand.bandHeight(120f, 200f))
    }

    @Test
    fun `录像原点随底带下移`() {
        // 内容层原点在 (0, 90)（顶栏高度），高 2400，底带 200
        // → 底带顶边在内容内 y=2200，所以录像原点应在 root 的 y=2290。
        val origin = Offset(0f, 90f)
        val got = BackdropBand.originInRoot(origin, contentHeight = 2400f, bandHeight = 200f)
        eq(expected = 0f, actual = got.x)
        eq(expected = 2290f, actual = got.y)
    }

    @Test
    fun `玻璃采样位置与录像位置互补`() {
        // 这段是「错位」问题的核心不变量：
        //   · 录像从内容内 y=bandTop 开始；
        //   · 玻璃要采样的那一点，在录像坐标系里的 y 应该是 (目标 y − bandTop)。
        // 换句话说，用 originInRoot 作参考原点时，底栏自身所在处恰好落在底带内。
        val contentHeight = 2400f
        val bandHeight = 220f
        val contentOrigin = Offset(0f, 90f)

        val bandTop = BackdropBand.bandTop(contentHeight, bandHeight)
        val recordedOrigin = BackdropBand.originInRoot(contentOrigin, contentHeight, bandHeight)

        // 底栏（屏幕底部那一条）在 root 中的 y
        val barTopInRoot = contentOrigin.y + contentHeight - bandHeight
        // 它在录像里的坐标
        val barTopInRecording = barTopInRoot - recordedOrigin.y
        eq(expected = 0f, actual = barTopInRecording)

        // 且录像整体不越界：起点 + 高度 = 内容底边
        eq(expected = contentOrigin.y + contentHeight, actual = recordedOrigin.y + bandHeight)
        eq(expected = contentHeight - bandHeight, actual = bandTop)
    }

    @Test
    fun `底带远小于整屏`() {
        // 优化的全部意义：底带面积相对整屏要小一个数量级，否则这次改造就白做了。
        val screen = 2400f
        val band = BackdropBand.bandHeight(
            screen,
            BackdropBand.heightDp(floatingBar = true, navInset = 24.dp).value * 3f,
        )
        assertTrue("底带应小于整屏的 20%，实际 ${band / screen}", band / screen < 0.2f)
    }
}
