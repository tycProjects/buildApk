package com.ryan.videodownload.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Gradient Colors
val GradientStart = Color(0xFF6B46C1)      // Deep Purple
val GradientMid = Color(0xFF9333EA)        // Purple
val GradientEnd = Color(0xFFEC4899)        // Pink

val GradientBlueStart = Color(0xFF0EA5E9)  // Sky Blue
val GradientBlueEnd = Color(0xFF6366F1)    // Indigo

val GradientSuccessStart = Color(0xFF10B981)
val GradientSuccessEnd = Color(0xFF059669)

val GradientErrorStart = Color(0xFFEF4444)
val GradientErrorEnd = Color(0xFFDC2626)

// Background
val DarkBackground = Color(0xFF0F0F1A)
val DarkSurface = Color(0xFF1A1A2E)
val DarkSurfaceVariant = Color(0xFF252542)
val DarkCard = Color(0xFF1E1E32)

// Text
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Accent
val AccentPurple = Color(0xFFA855F7)
val AccentPink = Color(0xFFEC4899)
val AccentCyan = Color(0xFF22D3EE)

// Gradients
val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(GradientStart, GradientMid, GradientEnd)
)

val PrimaryVerticalGradient = Brush.verticalGradient(
    colors = listOf(GradientStart, GradientEnd)
)

val BlueGradient = Brush.horizontalGradient(
    colors = listOf(GradientBlueStart, GradientBlueEnd)
)

val SuccessGradient = Brush.horizontalGradient(
    colors = listOf(GradientSuccessStart, GradientSuccessEnd)
)

val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F0F1A),
        Color(0xFF1A1025),
        Color(0xFF0F0F1A)
    )
)

val CardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E1E32),
        Color(0xFF252542)
    )
)
