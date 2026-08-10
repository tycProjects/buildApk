package com.blackglass.filemanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GlassColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    secondary = MutedWhite,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = PureWhite,
    surface = CardBlack,
    onSurface = PureWhite,
    surfaceVariant = NearBlack,
    onSurfaceVariant = MutedWhite,
    outline = GlassBorder,
    error = DangerRed,
    onError = PureWhite
)

@Composable
fun GlassFileManagerTheme(
    // The app intentionally always renders the black & white glass theme,
    // regardless of system light/dark setting, to keep the identity consistent.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme() // reserved for future light-mode toggle
    MaterialTheme(
        colorScheme = GlassColorScheme,
        typography = AppTypography,
        content = content
    )
}
