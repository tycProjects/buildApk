package com.ryan.vietsubai.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.ryan.vietsubai.model.SubtitleSettings
import com.ryan.vietsubai.model.SubtitleSource
import com.ryan.vietsubai.ui.components.SectionCard
import com.ryan.vietsubai.ui.theme.BrandIndigo
import com.ryan.vietsubai.ui.theme.MutedGray

@Composable
fun SubtitleSetupCard(settings: SubtitleSettings, onSave: (SubtitleSettings) -> Unit) {
    var source by remember(settings.source) { mutableStateOf(settings.source) }
    var sourceLanguage by remember(settings.sourceLanguage) { mutableStateOf(settings.sourceLanguage) }
    var targetLanguage by remember(settings.targetLanguage) { mutableStateOf(settings.targetLanguage) }
    var prompt by remember(settings.translationPrompt) { mutableStateOf(settings.translationPrompt) }

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text("Thiết lập dịch phụ đề", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Trích xuất → dịch → TTS", color = MutedGray)

        Text("Nguồn phụ đề", fontWeight = FontWeight.Bold)
        SubtitleSource.values().forEach { option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = source == option,
                    onClick = { source = option },
                    colors = RadioButtonDefaults.colors(selectedColor = BrandIndigo),
                )
                Text(labelFor(option))
            }
        }

        OutlinedTextField(sourceLanguage, { sourceLanguage = it }, label = { Text("Ngôn ngữ gốc") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetLanguage, { targetLanguage = it }, label = { Text("Dịch sang") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(prompt, { prompt = it }, label = { Text("Prompt phong cách dịch") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = { onSave(SubtitleSettings(source, sourceLanguage, targetLanguage, prompt)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Lưu thiết lập dịch") }
    }
}

private fun labelFor(option: SubtitleSource) = when (option) {
    SubtitleSource.SRT_TRANSLATED -> "Nhập SRT (bản dịch)"
    SubtitleSource.SRT_ORIGINAL -> "Nhập SRT (bản gốc, cần dịch)"
    SubtitleSource.OCR -> "OCR (chữ trên video bằng vùng crop)"
    SubtitleSource.STT -> "STT (giọng nói video)"
}
