package com.ryan.vietsubai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = SurfaceWhite,
    primaryContainer = ColorCompat(BrandIndigo, 0.14f),
    onPrimaryContainer = BrandIndigoDeep,
    secondary = BrandPurple,
    onSecondary = SurfaceWhite,
    tertiary = BrandCyan,
    background = PaperLight,
    onBackground = InkBlack,
    surface = SurfaceWhite,
    onSurface = InkBlack,
    surfaceVariant = DividerGray,
    onSurfaceVariant = MutedGray,
    error = ErrorRed,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9D8CFF),
    onPrimary = Color(0xFF21155E),
    primaryContainer = Color(0xFF4537A0),
    onPrimaryContainer = Color(0xFFE8E2FF),
    secondary = Color(0xFFD38BFF),
    onSecondary = Color(0xFF35113F),
    tertiary = Color(0xFF6FE2FF),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkMuted,
    error = ErrorRed,
)

private val ShapesVietsub = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val TypographyVietsub = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun VietsubAITheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = ShapesVietsub,
        typography = TypographyVietsub,
        content = content,
    )
}

// Keeps ColorScheme declarations concise without changing the public palette.
private fun ColorCompat(color: androidx.compose.ui.graphics.Color, alpha: Float) = color.copy(alpha = alpha)
