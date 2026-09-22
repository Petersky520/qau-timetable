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

@Composable
fun QauTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You 动态取色：从手机壁纸生成配色（Android 12 / API 31 起支持）。 */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
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
