package com.ryan.vietsubai.ui.screens.editor

import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.ryan.vietsubai.editor.EditorController
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.theme.BrandAmber

@Composable
fun EditorPreview(draft: EditorDraft, controller: EditorController, vm: VietsubAIViewModel, playheadMs: Long) {
    val activeText = draft.subtitleSegments.firstOrNull { playheadMs / 1000.0 >= it.start && playheadMs / 1000.0 <= it.end }?.let { it.translation ?: it.text } ?: draft.subtitleText
    val aspect = when (draft.crop.preset) { "16:9" -> 16f / 9f; "9:16" -> 9f / 16f; "1:1" -> 1f; "4:5" -> 4f / 5f; else -> null }
    BoxWithConstraints((Modifier.fillMaxSize()).then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier)) {
        AndroidView(
            factory = { context -> PlayerViewCompat.create(context, controller) },
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = draft.crop.zoom,
                scaleY = draft.crop.zoom,
                translationX = draft.crop.offsetX * 500f,
                translationY = draft.crop.offsetY * 500f,
            ),
        )
        AnimatedVisibility(
            visible = draft.subtitleEnabled && activeText.isNotBlank(),
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .offset(
                    x = maxWidth * draft.subtitleRegion.left,
                    y = maxHeight * draft.subtitleRegion.top,
                )
                .width(maxWidth * (draft.subtitleRegion.right - draft.subtitleRegion.left).coerceAtLeast(.1f))
                .height(maxHeight * (draft.subtitleRegion.bottom - draft.subtitleRegion.top).coerceAtLeast(.08f))
                .align(Alignment.TopStart),
        ) {
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        setTextColor(draft.subtitleStyle.textColor.toInt())
                        textSize = draft.subtitleStyle.fontSize
                        gravity = when (draft.subtitleStyle.align) { 0 -> android.view.Gravity.START; 2 -> android.view.Gravity.END; else -> android.view.Gravity.CENTER }
                        setShadowLayer(draft.subtitleStyle.shadow, 0f, 2f, android.graphics.Color.BLACK)
                        setPadding(8, 4, 8, 4)
                    }
                },
                update = { view ->
                    view.text = activeText
                    val base = draft.fonts.firstOrNull { it.id == draft.activeFontId }
                        ?.let { runCatching { Typeface.createFromFile(android.net.Uri.parse(it.uri).path ?: "") }.getOrNull() }
                        ?: Typeface.DEFAULT
                    view.typeface = Typeface.create(base, when { draft.subtitleStyle.bold && draft.subtitleStyle.italic -> Typeface.BOLD_ITALIC; draft.subtitleStyle.bold -> Typeface.BOLD; draft.subtitleStyle.italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (draft.selectedTool == "subtitleCrop") SubtitleRegionOverlay(draft, vm)
        if (draft.selectedTool == "blur") BlurOverlay(draft = draft, vm = vm)
    }
}

@Composable
private fun SubtitleRegionOverlay(draft: EditorDraft, vm: VietsubAIViewModel) {
    val r = draft.subtitleRegion
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        Box(
            Modifier
                .offset(x = maxWidth * r.left, y = maxHeight * r.top)
                .width(maxWidth * (r.right - r.left).coerceAtLeast(.08f))
                .height(maxHeight * (r.bottom - r.top).coerceAtLeast(.08f))
                .border(2.dp, Color(0xFF4DE7FF), RoundedCornerShape(10.dp))
                .background(Color(0x334DE7FF), RoundedCornerShape(10.dp))
                .pointerInput(r) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val dx = (drag.x / with(density) { maxWidth.toPx() }).coerceIn(-.2f, .2f)
                        val dy = (drag.y / with(density) { maxHeight.toPx() }).coerceIn(-.2f, .2f)
                        vm.setEditor {
                            val rr = it.subtitleRegion
                            val w = rr.right - rr.left
                            val h = rr.bottom - rr.top
                            val nl = (rr.left + dx).coerceIn(0f, 1f - w)
                            val nt = (rr.top + dy).coerceIn(0f, 1f - h)
                            it.copy(subtitleRegion = rr.copy(left = nl, right = nl + w, top = nt, bottom = nt + h))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("VÙNG PHỤ ĐỀ", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyPreview(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text("Chưa có video", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Import video ngay tại vùng preview", color = Color.LightGray)
            Button(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import video")
            }
        }
    }
}

@Composable
fun BlurOverlay(draft: EditorDraft, vm: VietsubAIViewModel) {
    val region = draft.blurRegions.lastOrNull() ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .pointerInput(region.id) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val dx = drag.x / 1000f
                    val dy = drag.y / 600f
                    vm.setEditor { editor ->
                        val old = editor.blurRegions.last()
                        editor.copy(blurRegions = editor.blurRegions.dropLast(1) + old.copy(
                            left = (old.left + dx).coerceIn(0f, 0.8f),
                            right = (old.right + dx).coerceIn(0.2f, 1f),
                            top = (old.top + dy).coerceIn(0f, 0.8f),
                            bottom = (old.bottom + dy).coerceIn(0.2f, 1f),
                        ))
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize(.5f).align(Alignment.Center).border(2.dp, BrandAmber, RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
            Text("BLUR REGION", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private object PlayerViewCompat {
    fun create(context: android.content.Context, controller: EditorController): androidx.media3.ui.PlayerView =
        androidx.media3.ui.PlayerView(context).apply { player = controller.player; useController = false }
}
