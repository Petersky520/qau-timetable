package cn.edu.qau.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.qau.timetable.core.UiStyle
import android.os.Build

// ---------------------------------------------------------------------------
// Material 3 Expressive
//
// Expressive 的视觉特征：更鲜艳的色调、扩到 8 档的圆角刻度、
// 更粗更醒目的字重、按 surfaceContainer 分层的色调表面。
//
// 说明：Expressive 的**装饰性形状**（MaterialShapes / Cookie / Clover）和
// ButtonGroup / FloatingToolbar / LoadingIndicator 等新组件
// 并不在 material3 1.4.0 里（属 1.5.0-alpha），
// 因此这里落地的是 1.4.0 真正提供的部分：完整形状刻度 + 色调 + 字体。
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F2B0),
    onPrimaryContainer = Color(0xFF00210A),

    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F0F),

    tertiary = Color(0xFF3A6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEE9F8),
    onTertiaryContainer = Color(0xFF001F27),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF191D17),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    surfaceDim = Color(0xFFD7DBD3),
    surfaceBright = Color(0xFFF7FBF2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFEBEFE6),
    surfaceContainerHigh = Color(0xFFE5EAE0),
    surfaceContainerHighest = Color(0xFFE0E4DB),

    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    inverseSurface = Color(0xFF2E322C),
    inverseOnSurface = Color(0xFFEFF2E9),
    inversePrimary = Color(0xFF99D597),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D597),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF145226),
    onPrimaryContainer = Color(0xFFB4F2B0),

    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243423),
    secondaryContainer = Color(0xFF3A4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),

    tertiary = Color(0xFFA2CDDC),
    onTertiary = Color(0xFF023541),
    tertiaryContainer = Color(0xFF214C58),
    onTertiaryContainer = Color(0xFFBEE9F8),

    background = Color(0xFF11140F),
    onBackground = Color(0xFFE1E4DB),
    surface = Color(0xFF11140F),
    onSurface = Color(0xFFE1E4DB),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceDim = Color(0xFF11140F),
    surfaceBright = Color(0xFF373A34),
    surfaceContainerLowest = Color(0xFF0C0F0A),
    surfaceContainerLow = Color(0xFF191D17),
    surfaceContainer = Color(0xFF1D211B),
    surfaceContainerHigh = Color(0xFF282B25),
    surfaceContainerHighest = Color(0xFF333630),

    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    inverseSurface = Color(0xFFE1E4DB),
    inverseOnSurface = Color(0xFF2E322C),
    inversePrimary = Color(0xFF2F6B3C),
)

/**
 * M3 Expressive 的圆角。
 *
 * classic M3 是 4/8/12/16/28；Expressive 扩到 8 档并把上限拉到
 * largeIncreased=20 / extraLargeIncreased=32 / extraExtraLarge=48。
 *
 * 那个 8 参构造函数在 Kotlin 侧是 `internal`（字节码 public 但 Kotlin 不可见），
 * 所以这里把 Expressive 的圆角值灌进公开的 5 个档位；
 * material3 1.4.0 的组件内部本来就在用完整的 8 档刻度
 * （`ShapeDefaults` 里有 LargeIncreased / ExtraExtraLarge / CornerFull）。
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Expressive 的字体更粗、更醒目。 */
private val ExpressiveTypography: Typography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

// ---------------------------------------------------------------------------
// MIUI X / HyperOS 风味
//
// 与上面那套 Material 3 Expressive 的差别只在三件事，但它决定了整体观感：
//   · 配色：灰底 + **白卡**（M3 那套是浅底 + 更深一档的卡片），强调色换成 MIUI 蓝
//   · 圆角：整体再放大一档，卡片 20dp 起
//   · 字体：标题更大更粗
//
// 之所以能只靠换 Theme 就完成"另一套界面"：所有页面都用 M3 组件、
// 并且都从 MaterialTheme 取色 / 圆角 / 字体，没有任何一处写死。
// ---------------------------------------------------------------------------

private val MiuixLight = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF00276B),

    secondary = Color(0xFF5B6472),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6EC),
    onSecondaryContainer = Color(0xFF171C24),

    tertiary = Color(0xFF7A5AF8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9E2FF),
    onTertiaryContainer = Color(0xFF21005D),

    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE0E0),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF141619),
    surface = Color(0xFFF2F3F5),
    onSurface = Color(0xFF141619),
    surfaceVariant = Color(0xFFE7E9ED),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceDim = Color(0xFFE3E5E9),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    // 顶栏取 surfaceContainerLow：贴近底色 → 扁平的 MIUI 观感
    surfaceContainerLow = Color(0xFFF7F8FA),
    // 卡片取 surfaceContainerHighest：纯白
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),

    outline = Color(0xFF8A9099),
    outlineVariant = Color(0xFFE0E2E7),
    inverseSurface = Color(0xFF2A2D33),
    inverseOnSurface = Color(0xFFF2F3F5),
    inversePrimary = Color(0xFF9CC3FF),
)

private val MiuixDark = darkColorScheme(
    primary = Color(0xFF9CC3FF),
    onPrimary = Color(0xFF00306B),
    primaryContainer = Color(0xFF1B4C99),
    onPrimaryContainer = Color(0xFFDCE9FF),

    secondary = Color(0xFFBFC6D2),
    onSecondary = Color(0xFF293040),
    secondaryContainer = Color(0xFF3F4756),
    onSecondaryContainer = Color(0xFFE3E6EC),

    tertiary = Color(0xFFCDBDFF),
    onTertiary = Color(0xFF37265E),
    tertiaryContainer = Color(0xFF4F3D7A),
    onTertiaryContainer = Color(0xFFE9E2FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0F1114),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF0F1114),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF3A3F47),
    onSurfaceVariant = Color(0xFFA8AEB8),
    surfaceDim = Color(0xFF0F1114),
    surfaceBright = Color(0xFF31353C),
    surfaceContainerLowest = Color(0xFF0A0C0F),
    surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF22262C),
    surfaceContainerHighest = Color(0xFF272B32),

    outline = Color(0xFF8A9099),
    outlineVariant = Color(0xFF33383F),
    inverseSurface = Color(0xFFE6E8EB),
    inverseOnSurface = Color(0xFF2A2D33),
    inversePrimary = Color(0xFF3482FF),
)

/** MIUI X 的圆角整体比 Expressive 再大一圈。 */
private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** MIUI X 的标题更大更粗。 */
private val MiuixTypography: Typography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun QauTheme(
    /** 界面风格：只换配色 / 圆角 / 字体，不动界面结构。 */
    style: UiStyle = UiStyle.MATERIAL3,
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Material You 动态取色：从手机壁纸生成配色（Android 12 / API 31 起支持）。
     * 只对 [UiStyle.MATERIAL3] 生效 —— MIUI X 的价值就在那套固定配色，
     * 跟了壁纸就跟默认风格分不出来了。
     */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when (style) {
        UiStyle.MIUIX -> if (darkTheme) MiuixDark else MiuixLight

        UiStyle.MATERIAL3 -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> DarkColors
            else -> LightColors
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = if (style == UiStyle.MIUIX) MiuixShapes else ExpressiveShapes,
        typography = if (style == UiStyle.MIUIX) MiuixTypography else ExpressiveTypography,
        content = content,
    )
}

/** 课程色板：按名字取色，保证同一门课颜色稳定。 */
private val COURSE_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF9C27B0),
    Color(0xFF009688), Color(0xFFE91E63), Color(0xFF3F51B5), Color(0xFF795548),
    Color(0xFF00BCD4), Color(0xFF8BC34A), Color(0xFFFF5722), Color(0xFF607D8B),
)

fun courseColor(seed: Int): Color =
    COURSE_COLORS[((seed % COURSE_COLORS.size) + COURSE_COLORS.size) % COURSE_COLORS.size]

fun courseColorFor(name: String): Color {
    var h = 0
    for (c in name) h = h * 31 + c.code
    return courseColor(h)
}
