package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.components.bouncyClickable
import com.ryan.vietsubai.ui.theme.MutedGray
import kotlinx.coroutines.launch

@Composable
fun SubtitleEditorPanel(
    draft: EditorDraft,
    vm: VietsubAIViewModel,
    onImportSrt: () -> Unit,
    aiBusy: Boolean,
    setBusy: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtitle editor", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${draft.subtitleSegments.size} đoạn", color = Color.Gray)
        }

        if (draft.subtitleSegments.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                itemsIndexed(draft.subtitleSegments) { index, segment ->
                    Card(
                        modifier = Modifier.fillMaxWidth().bouncyClickable {
                            vm.setEditor { it.copy(selectedSubtitleIndex = index, subtitleText = segment.translation ?: segment.text) }
                        },
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("${"%.2f".format(segment.start)}s → ${"%.2f".format(segment.end)}s", color = MutedGray)
                            Text(segment.translation ?: segment.text)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft.subtitleText,
            onValueChange = { text ->
                vm.setEditor { editor ->
                    val list = editor.subtitleSegments.toMutableList()
                    if (editor.selectedSubtitleIndex in list.indices) {
                        list[editor.selectedSubtitleIndex] = list[editor.selectedSubtitleIndex].copy(translation = text)
                    }
                    editor.copy(subtitleText = text, subtitleSegments = list)
                }
            },
            label = { Text("Nội dung") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onImportSrt) { Text("Import SRT") }

            Button(
                enabled = !aiBusy,
                onClick = {
                    setBusy(true)
                    scope.launch {
                        vm.geminiTranslate(draft.subtitleText).onSuccess { translated ->
                            vm.setEditor { editor ->
                                val list = editor.subtitleSegments.toMutableList()
                                if (editor.selectedSubtitleIndex in list.indices) {
                                    list[editor.selectedSubtitleIndex] = list[editor.selectedSubtitleIndex].copy(translation = translated)
                                }
                                editor.copy(subtitleText = translated, subtitleSegments = list)
                            }
                        }
                        setBusy(false)
                    }
                },
            ) { Text("Gemini") }

            OutlinedButton(
                enabled = !aiBusy,
                onClick = {
                    setBusy(true)
                    scope.launch {
                        vm.groqRewrite(draft.subtitleText).onSuccess { rewritten ->
                            vm.setEditor { it.copy(subtitleText = rewritten) }
                        }
                        setBusy(false)
                    }
                },
            ) { Text("Groq rewrite") }

            OutlinedButton(onClick = { vm.syncCurrentTts() }) { Text("Sync TTS") }
            Text(draft.ttsSyncLabel, color = Color.Gray)
        }
    }
}
