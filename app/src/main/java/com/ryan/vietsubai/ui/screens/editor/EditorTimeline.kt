package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.theme.BrandCyan
import com.ryan.vietsubai.ui.theme.BrandIndigo
import com.ryan.vietsubai.ui.theme.BrandPink
import com.ryan.vietsubai.ui.theme.EditorTrackBg
import kotlin.math.roundToLong

@Composable
fun EditorSeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    Slider(value = position.coerceIn(0, duration).toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), colors = SliderDefaults.colors(thumbColor = BrandCyan, activeTrackColor = BrandIndigo, inactiveTrackColor = EditorTrackBg))
}

@Composable
fun EditorTimelineTracks(draft: EditorDraft, duration: Long, position: Long, onSeek: (Long) -> Unit, vm: VietsubAIViewModel? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Track("VIDEO", duration, position, 1f, onSeek)
        Track("SUBTITLE", duration, position, if (draft.subtitleSegments.isEmpty()) 0f else .88f, onSeek) {
            draft.subtitleSegments.forEachIndexed { index, segment ->
                val left = (segment.start * 1000 / duration.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
                val width = ((segment.end - segment.start) * 1000 / duration.coerceAtLeast(1)).toFloat().coerceIn(.02f, 1f - left)
                Box(Modifier.fillMaxHeight().fillMaxWidth(width).align(Alignment.CenterStart).offset(x = 0.dp).background(Brush.horizontalGradient(listOf(BrandIndigo, BrandPink)), RoundedCornerShape(4.dp)).pointerInput(segment.start) {
                    detectTapGestures { onSeek((segment.start * 1000).toLong()); vm?.setEditor { it.copy(selectedSubtitleIndex = index, subtitleText = segment.translation ?: segment.text) } }
                }.pointerInput(segment.start, segment.end) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val delta = (drag.x / size.width * duration).roundToLong()
                        vm?.let { model ->
                            val old = model.editor.value.subtitleSegments.getOrNull(index) ?: return@detectDragGestures
                            val len = old.end - old.start
                            val ns = (old.start + delta / 1000.0).coerceIn(0.0, (duration / 1000.0) - len)
                            model.updateSubtitleSegment(index, ns, ns + len)
                        }
                    }
                }) {
                    if (index == draft.selectedSubtitleIndex) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)))
                }
            }
        }
        Track("AUDIO", duration, position, .72f, onSeek)
        if (draft.blurRegions.isNotEmpty()) Track("BLUR", duration, position, .55f, onSeek)
    }
}

@Composable
private fun Track(name: String, duration: Long, position: Long, fill: Float, onSeek: (Long) -> Unit, content: @Composable (BoxScope.() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color(0xFF858CA0), modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall)
        BoxWithConstraints(Modifier.fillMaxWidth().height(27.dp).background(EditorTrackBg, RoundedCornerShape(7.dp)).pointerInput(duration) { detectTapGestures { offset -> onSeek((duration * (offset.x / size.width)).toLong().coerceIn(0, duration)) } }) {
            if (fill > 0f) Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Brush.horizontalGradient(listOf(BrandIndigo.copy(.7f), BrandCyan.copy(.55f))), RoundedCornerShape(7.dp)))
            content?.invoke(this)
            val x = (position.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.CenterStart).offset(x = maxWidth * x).background(BrandCyan))
        }
    }
}

@Composable
fun EditorTransport(draft: EditorDraft, onPlayPause: () -> Unit, onSpeed: (Float) -> Unit, onVolume: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPlayPause, modifier = Modifier.weight(1.35f)) { Icon(Icons.Default.PlayArrow, null); Text("  Phát") }
        OutlinedButton(onClick = { onSpeed(if (draft.speed >= 1.5f) 1f else 1.5f) }, modifier = Modifier.weight(1f)) { Text("${draft.speed}x") }
        OutlinedButton(onClick = { onVolume((draft.volume - .1f).coerceAtLeast(0f)) }, modifier = Modifier.weight(1f)) { Text("Âm ${(draft.volume * 100).toInt()}%") }
    }
}
