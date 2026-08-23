package com.ryan.vietsubai.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.ui.components.SectionCard
import com.ryan.vietsubai.ui.theme.BrandAmber
import com.ryan.vietsubai.ui.theme.InkBlack

@Composable
fun DownloadUrlCard(downloadMessage: String, onDownload: (String) -> Unit) {
    var url by remember { mutableStateOf("") }

    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = InkBlack,
        contentColor = Color.White,
        cornerRadius = 26.dp,
    ) {
        Text("Tải video từ URL", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("YouTube · TikTok · Facebook · Instagram · direct media", color = Color(0xFFCCC8BF))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("Dán URL video…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = BrandAmber,
                cursorColor = BrandAmber,
            ),
        )

        Button(
            onClick = { onDownload(url) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BrandAmber, contentColor = InkBlack),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Tải video")
        }

        AnimatedVisibility(visible = downloadMessage.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Text(downloadMessage, color = Color(0xFFFFD67A), modifier = Modifier.padding(top = 4.dp))
        }
    }
}
