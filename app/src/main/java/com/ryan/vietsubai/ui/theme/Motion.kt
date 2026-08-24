package com.ryan.vietsubai.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Central place for animation timings/easings so every screen feels consistent.
 */
object Motion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 450

    fun <T> emphasized(durationMillis: Int = MEDIUM) =
        tween<T>(durationMillis = durationMillis, easing = Emphasized)
}
