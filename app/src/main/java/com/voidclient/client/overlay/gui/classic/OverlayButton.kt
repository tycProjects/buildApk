package com.voidclient.client.overlay.gui.classic

import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voidclient.client.overlay.OverlayManager
import com.voidclient.client.overlay.OverlayWindow
import kotlin.math.min

/**
 * YDK floating launcher button.
 * Drag the circle to move it; tap it to open the client ClickGUI.
 */
class OverlayButton : OverlayWindow() {

    private val _layoutParams by lazy {
        super.layoutParams.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            windowAnimations = android.R.style.Animation_Toast
            x = loadPosition("x", 24)
            y = loadPosition("y", 140)
        }
    }

    override val layoutParams: WindowManager.LayoutParams
        get() = _layoutParams

    private val overlayClickGUI by lazy { OverlayClickGUI() }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        LaunchedEffect(isLandscape) {
            _layoutParams.x = min(width.coerceAtLeast(1), _layoutParams.x)
            _layoutParams.y = min(height.coerceAtLeast(1), _layoutParams.y)
            windowManager.updateViewLayout(composeView, _layoutParams)
        }

        Box(
            modifier = Modifier
                .size(58.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
                        _layoutParams.x += drag.x.toInt()
                        _layoutParams.y += drag.y.toInt()
                        _layoutParams.x = _layoutParams.x.coerceAtLeast(0)
                        _layoutParams.y = _layoutParams.y.coerceAtLeast(0)
                        windowManager.updateViewLayout(composeView, _layoutParams)
                        savePosition()
                    }
                }
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFB86BFF),
                            Color(0xFF6A1B9A)
                        )
                    ),
                    shape = CircleShape
                )
                .border(2.dp, Color(0xFFE8C7FF), CircleShape)
                .clickable { OverlayManager.toggleOverlayWindow(overlayClickGUI) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "YDK",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    private fun loadPosition(key: String, fallback: Int): Int =
        OverlayManager.currentContext?.getSharedPreferences("ydk_overlay", Context.MODE_PRIVATE)
            ?.getInt(key, fallback) ?: fallback

    private fun savePosition() {
        OverlayManager.currentContext
            ?.getSharedPreferences("ydk_overlay", Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt("x", _layoutParams.x)
            ?.putInt("y", _layoutParams.y)
            ?.apply()
    }
}
