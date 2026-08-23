package com.ryan.download.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ryan.download.data.VideoFormat
import com.ryan.download.ui.theme.*

@Composable
fun LuxGradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0D0D0F), Color(0xFF15151A), Color(0xFF1A1510)))
        ), content = content
    )
}

@Composable
fun LuxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LuxSurface.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) { Column(modifier = Modifier.padding(20.dp), content = content) }
}

@Composable
fun LuxButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, icon: @Composable (() -> Unit)? = null
) {
    val scale by animateFloatAsState(if (enabled) 1f else 0.95f, spring(stiffness = Spring.StiffnessMedium), label = "")
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.scale(scale).height(56.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LuxGold, contentColor = Color(0xFF1A1200),
            disabledContainerColor = LuxGold.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier = Modifier.size(22.dp), color = Color(0xFF1A1200), strokeWidth = 2.5.dp)
        else {
            icon?.let { it(); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun UrlInputField(value: String, onValueChange: (String) -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Dán liên kết YouTube, TikTok, Instagram…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        leadingIcon = { Icon(Icons.Default.Link, null, tint = LuxGold) },
        trailingIcon = { TextButton(onClick = onPaste) { Text("Dán", color = LuxGold, fontWeight = FontWeight.Medium) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LuxGold, unfocusedBorderColor = LuxSurfaceVariant,
            focusedContainerColor = LuxSurfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = LuxSurfaceVariant.copy(alpha = 0.3f),
            cursorColor = LuxGold, focusedTextColor = Color.White, unfocusedTextColor = Color.White
        ),
        singleLine = true
    )
}

@Composable
fun VideoPreviewCard(title: String, uploader: String, thumbnail: String, duration: Long, modifier: Modifier = Modifier) {
    LuxCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)).background(LuxSurfaceVariant)) {
                if (thumbnail.isNotBlank()) AsyncImage(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.PlayArrow, null, Modifier.size(40.dp).align(Alignment.Center), tint = LuxGold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(uploader, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (duration > 0) {
                    Spacer(Modifier.height(4.dp))
                    val h = duration / 3600; val m = (duration % 3600) / 60; val s = duration % 60
                    Text(if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s), style = MaterialTheme.typography.labelMedium, color = LuxGold)
                }
            }
        }
    }
}

@Composable
fun FormatChip(format: VideoFormat, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) LuxGold else LuxSurfaceVariant
    val fg = if (selected) Color(0xFF1A1200) else Color.White
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp), color = bg,
        modifier = Modifier.border(if (selected) 0.dp else 1.dp, if (selected) Color.Transparent else LuxGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
            Column {
                Text(format.resolution.ifBlank { format.note }, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                if (format.note.isNotBlank() && format.resolution.isNotBlank())
                    Text(format.note, color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ProgressCard(progress: Float, stateText: String) {
    LuxCard(modifier = Modifier.fillMaxWidth()) {
        Text(stateText, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = LuxGold, trackColor = LuxSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = LuxGold)
    }
}
