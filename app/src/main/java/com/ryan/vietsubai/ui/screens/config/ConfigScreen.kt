package com.ryan.vietsubai.ui.screens.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.theme.ErrorRed
import com.ryan.vietsubai.ui.theme.MutedGray
import com.ryan.vietsubai.ui.theme.PaperLight
import com.ryan.vietsubai.ui.theme.SuccessGreen

@Composable
fun ConfigScreen(vm: VietsubAIViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var resolver by remember(config.mediaResolverUrl) { mutableStateOf(config.mediaResolverUrl) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PaperLight).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Config", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("API key được mã hóa bằng Android Keystore", color = MutedGray)
        }

        item { ProviderEditorCard("Google Gemini · dịch / ngữ cảnh", config.gemini) { vm.saveConfig(config.copy(gemini = it)) } }
        item { ProviderEditorCard("Groq · rewrite nhanh", config.groq) { vm.saveConfig(config.copy(groq = it)) } }
        item { ProviderEditorCard("Groq Whisper · STT", config.stt) { vm.saveConfig(config.copy(stt = it)) } }

        item {
            OutlinedTextField(
                value = resolver,
                onValueChange = { resolver = it },
                label = { Text("Media Resolver HTTPS URL") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { vm.testGemini() }, modifier = Modifier.weight(1f)) { Text("Test Gemini") }
                OutlinedButton(onClick = { vm.testGroq() }, modifier = Modifier.weight(1f)) { Text("Test Groq") }
            }
        }

        item {
            AnimatedVisibility(visible = status.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(status, color = if (status.startsWith("✓")) SuccessGreen else ErrorRed)
            }
        }

        item {
            Button(
                onClick = { vm.saveConfig(config.copy(mediaResolverUrl = resolver)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Lưu cấu hình") }
        }
    }
}
