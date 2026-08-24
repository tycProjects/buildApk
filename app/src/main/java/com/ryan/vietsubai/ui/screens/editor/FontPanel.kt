package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel

@Composable
fun FontPanel(draft: EditorDraft, vm: VietsubAIViewModel) {
    Column {
        Text("Font custom", color = Color.White)
        if (draft.fonts.isEmpty()) {
            Text("Chưa có font. Dùng nút Font để import TTF/OTF.", color = Color.Gray)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.fonts.forEach { font ->
                    FilterChip(
                        selected = draft.activeFontId == font.id,
                        onClick = { vm.setEditor { it.copy(activeFontId = font.id) } },
                        label = { Text(font.name) },
                    )
                }
            }
        }
    }
}
