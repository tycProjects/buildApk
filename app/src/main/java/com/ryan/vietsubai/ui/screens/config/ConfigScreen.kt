package com.ryan.vietsubai.ui.screens.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.components.SectionCard
import com.ryan.vietsubai.ui.theme.BrandIndigo
import com.ryan.vietsubai.ui.theme.BrandPurple
import com.ryan.vietsubai.ui.theme.ErrorRed
import com.ryan.vietsubai.ui.theme.MutedGray
import com.ryan.vietsubai.ui.theme.SuccessGreen

@Composable
fun ConfigScreen(vm: VietsubAIViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()
    var resolver by remember(config.mediaResolverUrl) { mutableStateOf(config.mediaResolverUrl) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Tùy chỉnh giao diện và kết nối AI", color = MutedGray)
                }
            }
        }

        item {
            SectionCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Giao diện", fontWeight = FontWeight.Bold)
                        Text(if (darkTheme) "Dark mode · dịu mắt ban đêm" else "Light mode · sáng và rõ nét", color = MutedGray)
                    }
                    Switch(checked = darkTheme, onCheckedChange = vm::toggleDarkTheme)
                }
            }
        }

        item {
            ProviderEditorCard("Google Gemini · dịch / ngữ cảnh", config.gemini) { vm.saveConfig(config.copy(gemini = it)) }
        }
        item {
            ProviderEditorCard("Groq · rewrite nhanh", config.groq) { vm.saveConfig(config.copy(groq = it)) }
        }
        item {
            ProviderEditorCard("Groq Whisper · STT", config.stt) { vm.saveConfig(config.copy(stt = it)) }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("Media Resolver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("URL HTTPS dùng để phân giải video nền tảng.", color = MutedGray)
                OutlinedTextField(
                    value = resolver,
                    onValueChange = { resolver = it },
                    label = { Text("Media Resolver HTTPS URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.testGemini() }, modifier = Modifier.weight(1f)) { Text("Test Gemini") }
                    OutlinedButton(onClick = { vm.testGroq() }, modifier = Modifier.weight(1f)) { Text("Test Groq") }
                }
                AnimatedVisibility(visible = status.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(status, color = if (status.startsWith("✓")) SuccessGreen else ErrorRed)
                }
                Button(
                    onClick = { vm.saveConfig(config.copy(mediaResolverUrl = resolver)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Lưu cấu hình", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
