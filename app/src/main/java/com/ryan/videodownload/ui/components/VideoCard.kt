package com.ryan.videodownload.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ryan.videodownload.data.model.DownloadStatus
import com.ryan.videodownload.data.model.DownloadTask
import com.ryan.videodownload.data.model.Platform
import com.ryan.videodownload.ui.theme.AccentCyan
import com.ryan.videodownload.ui.theme.AccentPink
import com.ryan.videodownload.ui.theme.AccentPurple
import com.ryan.videodownload.ui.theme.DarkCard
import com.ryan.videodownload.ui.theme.GradientErrorStart
import com.ryan.videodownload.ui.theme.GradientSuccessStart
import com.ryan.videodownload.ui.theme.TextMuted
import com.ryan.videodownload.ui.theme.TextPrimary
import com.ryan.videodownload.ui.theme.TextSecondary

@Composable
fun VideoInfoCard(
    title: String,
    author: String?,
    duration: String,
    platform: Platform,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(platform)
                Spacer(Modifier.width(8.dp))
                if (author != null) {
                    Text(
                        text = author,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = duration,
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onPlay: ((DownloadTask) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val canPlay = task.status == DownloadStatus.COMPLETED &&
            !task.filePath.isNullOrBlank() &&
            onPlay != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .then(
                if (canPlay) Modifier.clickable { onPlay?.invoke(task) }
                else Modifier
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (task.videoInfo.thumbnailUrl != null) {
                    AsyncImage(
                        model = task.videoInfo.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.PlayCircle,
                        null,
                        tint = AccentPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.videoInfo.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${task.selectedQuality.quality} • ${task.selectedQuality.format.uppercase()}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (task.status == DownloadStatus.COMPLETED) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Đã lưu vào album • Chạm để xem",
                        color = AccentCyan,
                        fontSize = 11.sp
                    )
                }
            }

            if (canPlay) {
                IconButton(
                    onClick = { onPlay?.invoke(task) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Xem video",
                        tint = AccentPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                StatusIcon(task.status)
            }
        }

        if (task.status == DownloadStatus.DOWNLOADING) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentPurple,
                trackColor = Color.White.copy(0.1f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.sizeProgress(),
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = "${task.progressPercent()}% • ${task.speedFormatted()}",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (task.status == DownloadStatus.FAILED && task.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = task.errorMessage,
                color = GradientErrorStart,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatusIcon(status: DownloadStatus) {
    when (status) {
        DownloadStatus.COMPLETED -> Icon(
            Icons.Default.CheckCircle,
            null,
            tint = GradientSuccessStart,
            modifier = Modifier.size(22.dp)
        )
        DownloadStatus.FAILED -> Icon(
            Icons.Default.Error,
            null,
            tint = GradientErrorStart,
            modifier = Modifier.size(22.dp)
        )
        DownloadStatus.DOWNLOADING -> {
            // Progress handled below
        }
        else -> {}
    }
}

@Composable
fun PlatformBadge(platform: Platform) {
    val (label, color) = when (platform) {
        Platform.YOUTUBE -> "YT" to Color(0xFFFF0000)
        Platform.TIKTOK -> "TT" to Color(0xFF00F2EA)
        Platform.INSTAGRAM -> "IG" to AccentPink
        Platform.FACEBOOK -> "FB" to Color(0xFF1877F2)
        Platform.TWITTER -> "X" to Color.White
        Platform.VIMEO -> "VI" to Color(0xFF1AB7EA)
        Platform.OTHER -> "URL" to AccentPurple
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
