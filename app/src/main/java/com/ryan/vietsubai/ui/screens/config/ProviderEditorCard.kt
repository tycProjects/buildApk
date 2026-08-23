package com.ryan.vietsubai.ui.screens.config

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.AiProviderConfig
import com.ryan.vietsubai.ui.components.SectionCard
import com.ryan.vietsubai.ui.theme.MutedGray

@Composable
fun ProviderEditorCard(title: String, provider: AiProviderConfig, onSave: (AiProviderConfig) -> Unit) {
    var apiKey by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
    var baseUrl by remember(provider.baseUrl) { mutableStateOf(provider.baseUrl) }
    var model by remember(provider.model) { mutableStateOf(provider.model) }

    SectionCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Text(title, fontWeight = FontWeight.Bold)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Vai trò: ${roleLabel(provider.id)}", color = MutedGray)
        TextButton(onClick = { onSave(provider.copy(baseUrl = baseUrl, apiKey = apiKey, model = model)) }) {
            Text("Lưu")
        }
    }
}

private fun roleLabel(id: String) = when (id) {
    "stt" -> "Groq STT"
    "gemini" -> "Gemini dịch"
    else -> "Groq tốc độ cao"
}
