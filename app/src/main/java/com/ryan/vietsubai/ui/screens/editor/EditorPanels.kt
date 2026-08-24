package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel

@Composable
fun TrimControls(draft: EditorDraft, vm: VietsubAIViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Trim clip", color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.setEditor { it.copy(trimStartMs = it.playheadMs) } }, modifier = Modifier.weight(1f)) {
                Text("Set start")
            }
            OutlinedButton(onClick = { vm.setEditor { it.copy(trimEndMs = it.playheadMs) } }, modifier = Modifier.weight(1f)) {
                Text("Set end")
            }
        }
    }
}

@Composable
fun BlurControls(draft: EditorDraft, vm: VietsubAIViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Blur crop · ${draft.blurRegions.size} vùng", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.addBlurRegion() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Thêm vùng")
            }
            OutlinedButton(onClick = { vm.setEditor { it.copy(ocrScanFps = (it.ocrScanFps + 1f).coerceAtMost(10f)) } }) {
                Text("OCR ${draft.ocrScanFps} fps")
            }
        }
    }
}

@Composable
fun EditorActionsRow(
    aiBusy: Boolean,
    onRender: () -> Unit,
    onOcrFrame: () -> Unit,
    onTranslateAll: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onRender, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Save, contentDescription = null)
            Text("Render")
        }
        OutlinedButton(onClick = onOcrFrame, modifier = Modifier.weight(1f)) { Text("OCR frame") }
        OutlinedButton(onClick = onTranslateAll, enabled = !aiBusy, modifier = Modifier.weight(1f)) {
            Text(if (aiBusy) "Dịch…" else "Dịch tất cả")
        }
    }
}
