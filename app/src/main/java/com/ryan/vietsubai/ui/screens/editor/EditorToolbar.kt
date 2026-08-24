package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun EditorToolbar(
    onTrim: () -> Unit,
    onText: () -> Unit,
    onSubtitle: () -> Unit,
    onAudio: () -> Unit,
    onBlur: () -> Unit,
    onFont: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolChip("Cut", Icons.Default.ContentCut, onTrim)
        ToolChip("Text", Icons.Default.TextFields, onText)
        ToolChip("Subtitle", Icons.Default.Subtitles, onSubtitle)
        ToolChip("Audio", Icons.Default.MusicNote, onAudio)
        ToolChip("Blur crop", Icons.Default.BlurOn, onBlur)
        ToolChip("Font", Icons.Default.FontDownload, onFont)
        ToolChip("Undo", Icons.Default.Undo, onUndo)
        ToolChip("Redo", Icons.Default.Redo, onRedo)
    }
}

@Composable
private fun ToolChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
