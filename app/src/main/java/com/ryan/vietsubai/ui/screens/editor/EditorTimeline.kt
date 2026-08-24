package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.theme.BrandAmber
import com.ryan.vietsubai.ui.theme.EditorTrackBg

@Composable
fun EditorSeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    Slider(
        value = position.coerceIn(0, duration).toFloat(),
        onValueChange = { onSeek(it.toLong()) },
        valueRange = 0f..duration.toFloat(),
        colors = SliderDefaults.colors(thumbColor = BrandAmber, activeTrackColor = BrandAmber),
    )
}

@Composable
fun EditorTimelineTracks(draft: EditorDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val tracks = listOf(
            "VIDEO" to 1f,
            "TEXT" to 0.65f,
            "AUDIO" to 0.8f,
            "SUBTITLE" to if (draft.subtitleSegments.isEmpty()) 0.1f else 0.75f,
        )
        tracks.forEach { (name, fill) ->
            val animatedFill by animateFloatAsState(targetValue = fill, animationSpec = tween(400), label = "trackFill")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.Gray, modifier = Modifier.width(62.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EditorTrackBg),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedFill)
                            .fillMaxHeight()
                            .background(BrandAmber.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
fun EditorTransport(
    draft: EditorDraft,
    onPlayPause: () -> Unit,
    onSpeed: (Float) -> Unit,
    onVolume: (Float) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPlayPause, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Play")
        }
        OutlinedButton(onClick = { onSpeed(if (draft.speed == 1f) 1.5f else 1f) }, modifier = Modifier.weight(1f)) {
            Text("${draft.speed}x")
        }
        OutlinedButton(onClick = { onVolume((draft.volume - 0.1f).coerceAtLeast(0f)) }, modifier = Modifier.weight(1f)) {
            Text("Vol")
        }
    }
}
