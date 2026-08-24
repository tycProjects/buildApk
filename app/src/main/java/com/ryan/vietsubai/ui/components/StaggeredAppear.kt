package com.ryan.vietsubai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Wraps [content] so it fades + slides in shortly after composition, with a
 * delay proportional to [index]. Used to give lists (project rows, queue
 * items, etc.) a smooth, staggered "cascade" appearance instead of popping
 * in all at once.
 */
@Composable
fun StaggeredAppear(
    index: Int = 0,
    baseDelayMillis: Int = 60,
    content: @Composable () -> Unit,
) {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(index.toLong() * baseDelayMillis)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 },
    ) {
        content()
    }
}
