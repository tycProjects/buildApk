package com.blackglass.filemanager.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blackglass.filemanager.ui.theme.GlassBorder
import com.blackglass.filemanager.ui.theme.GlassFillBottom
import com.blackglass.filemanager.ui.theme.GlassFillTop

/**
 * A frosted-glass style container: a soft translucent gradient fill, a subtle
 * light border, and (on API 31+) a real background blur to sample whatever is
 * behind it. On older APIs it gracefully degrades to a plain translucent card,
 * which still reads as "glassy" against the black background.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderWidth: Dp = 1.dp,
    contentPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(radius = 18.dp)
                    } else Modifier
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(GlassFillTop, GlassFillBottom)
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .border(width = borderWidth, color = GlassBorder, shape = shape)
        )
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/** Simpler glass chip used for small pills/buttons. */
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom))
            )
            .border(width = 1.dp, color = GlassBorder, shape = shape)
    ) {
        content()
    }
}
