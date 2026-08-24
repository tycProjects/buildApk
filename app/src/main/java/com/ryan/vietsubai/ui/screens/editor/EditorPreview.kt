package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.ryan.vietsubai.editor.EditorController
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.theme.BrandAmber

@Composable
fun EditorPreview(draft: EditorDraft, controller: EditorController, vm: VietsubAIViewModel) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context -> PlayerView(context).apply { player = controller.player; useController = false } },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = draft.subtitleEnabled && draft.subtitleText.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                draft.subtitleText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(24.dp),
            )
        }
        if (draft.selectedTool == "blur") BlurOverlay(draft = draft, vm = vm)
    }
}

@Composable
fun EmptyPreview(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            Text("Chưa có video", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Import video ngay tại vùng preview", color = Color.LightGray)
            Button(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import video")
            }
        }
    }
}

@Composable
fun BlurOverlay(draft: EditorDraft, vm: VietsubAIViewModel) {
    val region = draft.blurRegions.lastOrNull() ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .pointerInput(region.id) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val dx = drag.x / 1000f
                    val dy = drag.y / 600f
                    vm.setEditor { editor ->
                        val old = editor.blurRegions.last()
                        editor.copy(
                            blurRegions = editor.blurRegions.dropLast(1) + old.copy(
                                left = (old.left + dx).coerceIn(0f, 0.8f),
                                right = (old.right + dx).coerceIn(0.2f, 1f),
                                top = (old.top + dy).coerceIn(0f, 0.8f),
                                bottom = (old.bottom + dy).coerceIn(0.2f, 1f),
                            ),
                        )
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.5f)
                .align(Alignment.Center)
                .border(2.dp, BrandAmber, RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("BLUR REGION", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
