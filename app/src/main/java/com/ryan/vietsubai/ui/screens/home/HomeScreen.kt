package com.ryan.vietsubai.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.R
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.components.StaggeredAppear
import com.ryan.vietsubai.ui.theme.BrandCyan
import com.ryan.vietsubai.ui.theme.BrandIndigo
import com.ryan.vietsubai.ui.theme.BrandPink
import com.ryan.vietsubai.ui.theme.MutedGray
import com.ryan.vietsubai.ui.theme.PaperLight

@Composable
fun HomeScreen(vm: VietsubAIViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloads by vm.downloadJobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val renders by vm.renderJobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val config by vm.config.collectAsStateWithLifecycle()
    val downloadMessage by vm.downloadMessage.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HomeHero() }
        item { DownloadUrlCard(downloadMessage = downloadMessage, onDownload = vm::downloadUrl) }
        item { SubtitleSetupCard(settings = config.subtitle, onSave = vm::saveSubtitleSettings) }
        item {
            QueueSection(
                downloads = downloads.map { JobRow("Tải · ${it.title}", it.status, it.progress) },
                renders = renders.map { JobRow("Render · ${it.projectName}", it.status, it.progress) },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Dự án gần đây", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        itemsIndexed(projects.take(12), key = { _, project -> project.id }) { index, project ->
            StaggeredAppear(index = index) { ProjectRow(project = project, onOpen = { vm.openProject(project) }) }
        }
    }
}

@Composable
private fun HomeHero() {
    val transition = rememberInfiniteTransition(label = "heroGradient")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse),
        label = "gradientShift",
    )
    val brush = Brush.linearGradient(
        0f to BrandIndigo,
        (.45f + .2f * shift).coerceIn(0f, 1f) to BrandPink,
        1f to BrandCyan,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(brush)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.vietsub_ai_logo),
                    contentDescription = "Vietsub AI",
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Vietsub AI", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("AI video studio", color = androidx.compose.ui.graphics.Color.White.copy(.82f))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = shift * 10f },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Biến video thành phụ đề đẹp trong vài chạm.",
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("Gemini + Groq · nhanh · gọn · hiện đại", color = androidx.compose.ui.graphics.Color.White.copy(.78f))
        }
    }
}

data class JobRow(val title: String, val status: String, val progress: Int)
