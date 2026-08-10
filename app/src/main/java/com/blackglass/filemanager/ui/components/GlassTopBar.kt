package com.blackglass.filemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackglass.filemanager.ui.theme.FaintWhite
import com.blackglass.filemanager.ui.theme.MutedWhite
import com.blackglass.filemanager.ui.theme.PureWhite
import com.blackglass.filemanager.ui.theme.SelectionTint
import java.io.File

@Composable
fun BreadcrumbBar(
    segments: List<File>,
    onSegmentClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            val label = if (index == 0) "" else segment.name.ifBlank { "/" }
            if (index == 0) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Root",
                    tint = if (index == segments.lastIndex) PureWhite else MutedWhite,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSegmentClick(segment) }
                        .padding(6.dp)
                )
            } else {
                Text(
                    text = label,
                    color = if (index == segments.lastIndex) PureWhite else MutedWhite,
                    fontWeight = if (index == segments.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSegmentClick(segment) }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            if (index != segments.lastIndex) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = FaintWhite,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StorageUsageBar(
    usedBytes: Long,
    totalBytes: Long,
    label: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                color = MutedWhite,
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SelectionTint),
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(PureWhite)
                ) {}
            }
        }
    }
}
