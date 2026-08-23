package com.ryan.download.ui.theme
import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LuxDarkColorScheme = darkColorScheme(
    primary = LuxGold, onPrimary = LuxOnPrimary, primaryContainer = LuxPrimary,
    onPrimaryContainer = LuxGoldLight, secondary = LuxSecondary, onSecondary = Color.White,
    tertiary = LuxGoldLight, background = LuxDark, onBackground = Color(0xFFF5F5F5),
    surface = LuxSurface, onSurface = Color(0xFFF5F5F5), surfaceVariant = LuxSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0B0), error = LuxError, onError = Color.White
)

@Composable
fun TaiVideoTheme(darkTheme: Boolean = true, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        dynamicDarkColorScheme(LocalContext.current) else LuxDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
