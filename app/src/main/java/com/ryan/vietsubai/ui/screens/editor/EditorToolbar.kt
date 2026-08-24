package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.ui.theme.BrandCyan
import com.ryan.vietsubai.ui.theme.BrandIndigo

/** Compact editor tool strip. Undo/redo live in the top bar, so this row only contains editing tools. */
@Composable
fun EditorToolbar(
    selectedTool: String,
    onTrim: () -> Unit,
    onText: () -> Unit,
    onSubtitle: () -> Unit,
    onAudio: () -> Unit,
    onBlur: () -> Unit,
    onFont: () -> Unit,
    onSubtitleCrop: () -> Unit,
    onStyle: () -> Unit,
    onCrop: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolChip("Cắt", Icons.Default.ContentCut, selectedTool == "trim", onTrim)
        ToolChip("Text", Icons.Default.TextFields, selectedTool == "text", onText)
        ToolChip("Sub", Icons.Default.Subtitles, selectedTool == "subtitle", onSubtitle)
        ToolChip("Audio", Icons.Default.MusicNote, selectedTool == "audio", onAudio)

        androidx.compose.foundation.layout.Box {
            FilterChip(
                selected = selectedTool == "blur" || moreExpanded,
                onClick = { moreExpanded = true },
                leadingIcon = { Icon(Icons.Default.MoreHoriz, null) },
                label = { Text("Thêm") },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandIndigo.copy(alpha = .30f),
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = BrandCyan,
                ),
            )
            DropdownMenu(
                expanded = moreExpanded,
                onDismissRequest = { moreExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Blur / Che vùng") },
                    leadingIcon = { Icon(Icons.Default.BlurOn, null) },
                    onClick = { moreExpanded = false; onBlur() },
                )
                DropdownMenuItem(
                    text = { Text("Font / Kiểu chữ") },
                    leadingIcon = { Icon(Icons.Default.FontDownload, null) },
                    onClick = { moreExpanded = false; onFont() },
                )
                DropdownMenuItem(
                    text = { Text("Vùng hiển thị phụ đề") },
                    leadingIcon = { Icon(Icons.Default.CropFree, null) },
                    onClick = { moreExpanded = false; onSubtitleCrop() },
                )
                DropdownMenuItem(
                    text = { Text("Style phụ đề") },
                    leadingIcon = { Icon(Icons.Default.TextFields, null) },
                    onClick = { moreExpanded = false; onStyle() },
                )
                DropdownMenuItem(
                    text = { Text("Crop video") },
                    leadingIcon = { Icon(Icons.Default.CropFree, null) },
                    onClick = { moreExpanded = false; onCrop() },
                )
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, null) },
        label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = BrandIndigo.copy(alpha = .30f),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = BrandCyan,
        ),
    )
}
