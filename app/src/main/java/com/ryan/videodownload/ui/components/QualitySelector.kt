package com.ryan.videodownload.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryan.videodownload.data.model.VideoInfo
import com.ryan.videodownload.data.model.VideoQuality
import com.ryan.videodownload.ui.theme.AccentPurple
import com.ryan.videodownload.ui.theme.DarkBackground
import com.ryan.videodownload.ui.theme.DarkSurface
import com.ryan.videodownload.ui.theme.DarkSurfaceVariant
import com.ryan.videodownload.ui.theme.PrimaryGradient
import com.ryan.videodownload.ui.theme.TextMuted
import com.ryan.videodownload.ui.theme.TextPrimary
import com.ryan.videodownload.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityBottomSheet(
    videoInfo: VideoInfo,
    selectedQuality: VideoQuality?,
    onSelect: (VideoQuality) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Chọn chất lượng",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = videoInfo.title,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(280.dp)
            ) {
                items(videoInfo.qualities) { quality ->
                    QualityItem(
                        quality = quality,
                        isSelected = quality == selectedQuality,
                        onClick = { onSelect(quality) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            GradientButton(
                text = "Tải xuống ngay",
                onClick = onDownload,
                enabled = selectedQuality != null,
                gradient = PrimaryGradient
            )
        }
    }
}

@Composable
private fun QualityItem(
    quality: VideoQuality,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) AccentPurple.copy(alpha = 0.15f)
                else DarkSurfaceVariant
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) AccentPurple else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.HighQuality,
            contentDescription = null,
            tint = if (isSelected) AccentPurple else TextMuted,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.padding(start = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quality.quality,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${quality.format.uppercase()} • ${quality.sizeFormatted()}" +
                        if (quality.fps != null) " • ${quality.fps}fps" else "",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentPurple),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
