package com.ryan.vietsubai.ui.screens.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.ui.components.JobProgressRow
import com.ryan.vietsubai.ui.components.SectionCard
import com.ryan.vietsubai.ui.components.StaggeredAppear

@Composable
fun QueueSection(downloads: List<JobRow>, renders: List<JobRow>) {
    val items = downloads.take(3) + renders.take(3)
    if (items.isEmpty()) return

    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Text("Hàng đợi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        items.forEachIndexed { index, job ->
            StaggeredAppear(index = index, baseDelayMillis = 40) {
                JobProgressRow(
                    title = job.title,
                    subtitle = "${job.status} · ${job.progress}%",
                    progress = job.progress,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }
    }
}
