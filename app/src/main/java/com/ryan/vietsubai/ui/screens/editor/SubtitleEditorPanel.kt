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
    onSeek: (Long) -> Unit,
    onOcrFullVideo: () -> Unit,
    onRewriteAll: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtitle editor", color = Color.White, fontWeight = FontWeight.Bold)
            Text("${draft.subtitleSegments.size} đoạn", color = Color.Gray)
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Switch(checked = draft.ocrAutoMerge, onCheckedChange = { v -> vm.setEditor { it.copy(ocrAutoMerge = v) } })
            Text("Auto merge OCR", color = Color.LightGray)
            Text("${draft.ocrScanFps} fps", color = Color.Gray, modifier = Modifier.padding(start = 10.dp))
        }

        if (draft.subtitleSegments.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                itemsIndexed(draft.subtitleSegments) { index, segment ->
                    Card(
                        modifier = Modifier.fillMaxWidth().bouncyClickable {
                            vm.setEditor { it.copy(selectedSubtitleIndex = index, subtitleText = segment.translation ?: segment.text) }
                            onSeek((segment.start * 1000).toLong())
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
            OutlinedButton(onClick = onOcrFullVideo, enabled = !aiBusy) { Text("OCR toàn video") }
            OutlinedButton(onClick = onRewriteAll, enabled = !aiBusy) { Text("Rewrite tất cả") }
            OutlinedButton(onClick = vm::autoSyncSubtitles) { Text("Auto Sync") }

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
            OutlinedButton(onClick = { vm.splitSelectedSubtitle(
                (draft.subtitleSegments.getOrNull(draft.selectedSubtitleIndex)?.let { ((it.start + it.end) / 2.0 * 1000).toLong() } ?: 0L)
            ) }) { Text("Split") }
            OutlinedButton(onClick = { vm.mergeSelectedWithNext() }) { Text("Merge") }
            Text(draft.ttsSyncLabel, color = Color.Gray)
        }
    }
}
