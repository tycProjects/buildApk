package com.ryan.vietsubai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val VietsubAIColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = SurfaceWhite,
    primaryContainer = BrandIndigo.copy(alpha = 0.12f),
    onPrimaryContainer = BrandIndigoDeep,
    secondary = BrandAmber,
    onSecondary = InkBlack,
    background = PaperLight,
    onBackground = InkBlack,
    surface = SurfaceWhite,
    onSurface = InkBlack,
    surfaceVariant = DividerGray,
    onSurfaceVariant = MutedGray,
    error = ErrorRed,
)

private val VietsubAIShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val VietsubAITypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun VietsubAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VietsubAIColorScheme,
        shapes = VietsubAIShapes,
        typography = VietsubAITypography,
        content = content,
    )
}
