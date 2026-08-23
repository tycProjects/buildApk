package com.ryan.vietsubai.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.R
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.components.StaggeredAppear
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
        modifier = Modifier.fillMaxSize().background(PaperLight).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HomeHeader() }

        item { DownloadUrlCard(downloadMessage = downloadMessage, onDownload = vm::downloadUrl) }

        item { SubtitleSetupCard(settings = config.subtitle, onSave = vm::saveSubtitleSettings) }

        item {
            QueueSection(
                downloads = downloads.map { JobRow("Tải · ${it.title}", it.status, it.progress) },
                renders = renders.map { JobRow("Render · ${it.projectName}", it.status, it.progress) },
            )
        }

        item {
            Text("Dự án gần đây", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        itemsIndexed(projects.take(12), key = { _, project -> project.id }) { index, project ->
            StaggeredAppear(index = index) {
                ProjectRow(project = project, onOpen = { vm.openProject(project) })
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.vietsub_ai_logo),
            contentDescription = "Vietsub AI",
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Vietsub AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("AI video studio · Gemini + Groq", color = MutedGray)
        }
    }
}

/** Simple UI-only representation of a queue entry (download or render job). */
data class JobRow(val title: String, val status: String, val progress: Int)
