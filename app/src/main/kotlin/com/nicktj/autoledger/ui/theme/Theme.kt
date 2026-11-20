package com.nicktj.autoledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 颜色（Subnook 设计系统）
// ---------------------------------------------------------------------------

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E8FF),
    onPrimaryContainer = Color(0xFF1E1B6B),
    inversePrimary = Color(0xFFA5B4FC),
    secondary = Color(0xFF64748B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF0F6),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F5F9),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceTint = Color(0xFF4F46E5),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF8FAFC),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFFCBD2DE),
    outlineVariant = Color(0xFFE5E8EF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2E6EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerHigh = Color(0xFFE9ECF3),
    surfaceContainerHighest = Color(0xFFE2E6EE),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF2E2A6E),
    onPrimaryContainer = Color(0xFFE0E7FF),
    inversePrimary = Color(0xFF4F46E5),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B0C10),
    secondaryContainer = Color(0xFF22252D),
    onSecondaryContainer = Color(0xFFE5E7EB),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF0B0C10),
    onBackground = Color(0xFFE8EAEE),
    surface = Color(0xFF0B0C10),
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = Color(0xFF1C1F26),
    onSurfaceVariant = Color(0xFF9AA1AD),
    surfaceTint = Color(0xFFA5B4FC),
    inverseSurface = Color(0xFFE8EAEE),
    inverseOnSurface = Color(0xFF0B0C10),
    error = Color(0xFFFF6B6F),
    onError = Color(0xFF2A0A0B),
    errorContainer = Color(0xFF5C1A1C),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3A3F4A),
    outlineVariant = Color(0xFF262A32),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2E37),
    surfaceDim = Color(0xFF0B0C10),
    surfaceContainerLowest = Color(0xFF111318),
    surfaceContainerLow = Color(0xFF15171D),
    surfaceContainer = Color(0xFF1A1D23),
    surfaceContainerHigh = Color(0xFF22252D),
    surfaceContainerHighest = Color(0xFF2A2E37),
)

// ---------------------------------------------------------------------------
// 设计系统扩展色
// ---------------------------------------------------------------------------

@Immutable
data class ExtraColors(
    /** Hero 区域渐变。 */
    val heroBrush: Brush,
    /** 强调色渐变。 */
    val accentBrush: Brush,
    /** 卡片柔和阴影颜色（深色主题下为透明，用描边代替）。 */
    val cardShadow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dark: Boolean,
)

private val LightExtra = ExtraColors(
    heroBrush = Brush.linearGradient(
        listOf(Color(0xFF4338CA), Color(0xFF6D28D9), Color(0xFF9333EA)),
    ),
    accentBrush = Brush.linearGradient(
        listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
    ),
    cardShadow = Color(0x1A1E1B4B),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFE5484D),
    dark = false,
)

private val DarkExtra = ExtraColors(
    heroBrush = Brush.linearGradient(
        listOf(Color(0xFF312E81), Color(0xFF4C1D95), Color(0xFF6D28D9)),
    ),
    accentBrush = Brush.linearGradient(
        listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
    ),
    cardShadow = Color.Transparent,
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFF6B6F),
    dark = true,
)

val LocalExtra = staticCompositionLocalOf { LightExtra }

// ---------------------------------------------------------------------------
// 字体与形状
// ---------------------------------------------------------------------------

private val AutoLedgerTypography = Typography(
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val AutoLedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 等宽数字，用于金额展示。 */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

// ---------------------------------------------------------------------------
// 主题入口
// ---------------------------------------------------------------------------

@Composable
fun AutoLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extra = if (darkTheme) DarkExtra else LightExtra
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AutoLedgerTypography,
            shapes = AutoLedgerShapes,
            content = content,
        )
    }
}
