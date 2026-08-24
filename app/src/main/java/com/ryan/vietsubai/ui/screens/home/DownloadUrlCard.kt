package com.ryan.vietsubai.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.ui.components.SectionCard
import com.ryan.vietsubai.ui.theme.BrandIndigo
import com.ryan.vietsubai.ui.theme.BrandPurple
import com.ryan.vietsubai.ui.theme.BrandCyan

@Composable
fun DownloadUrlCard(downloadMessage: String, onDownload: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        contentColor = Color.White,
        cornerRadius = 28.dp,
        gradient = listOf(BrandIndigo, BrandPurple, BrandCyan),
    ) {
        Text("Tải video từ URL", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("YouTube · TikTok · Facebook · Instagram · direct media", color = Color.White.copy(.78f))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("Dán URL video…", color = Color.White.copy(.55f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(.45f),
                cursorColor = Color.White,
            ),
        )
        Button(
            onClick = { onDownload(url) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandIndigo),
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("Tải video", fontWeight = FontWeight.Bold)
        }
        AnimatedVisibility(visible = downloadMessage.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Text(downloadMessage, color = Color.White, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Medium)
        }
    }
}
