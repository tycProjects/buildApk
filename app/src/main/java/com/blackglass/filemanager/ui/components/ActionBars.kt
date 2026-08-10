package com.blackglass.filemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.blackglass.filemanager.data.SortMode
import com.blackglass.filemanager.data.ViewMode
import com.blackglass.filemanager.ui.theme.FaintWhite
import com.blackglass.filemanager.ui.theme.MutedWhite
import com.blackglass.filemanager.ui.theme.PureWhite

@Composable
fun SearchAndSortBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: SortMode,
    onCycleSort: () -> Unit,
    viewMode: ViewMode,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = 4.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = FaintWhite,
                modifier = Modifier.padding(start = 10.dp)
            )
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search this folder", color = FaintWhite) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = PureWhite,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite
                )
            )
            IconButton(onClick = onCycleSort) {
                Text(sortMode.label.take(1), color = MutedWhite, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                    contentDescription = "Toggle view",
                    tint = MutedWhite
                )
            }
        }
    }
}

@Composable
fun SelectionActionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onRename: (() -> Unit)?,
    onInfo: (() -> Unit)?,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$count selected", color = PureWhite, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear selection",
                    tint = MutedWhite,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClose() }.padding(6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionIcon(Icons.Filled.ContentCopy, "Copy", onCopy)
                ActionIcon(Icons.Filled.DriveFileMove, "Move", onMove)
                ActionIcon(Icons.Filled.Share, "Share", onShare)
                if (onRename != null) ActionIcon(Icons.Filled.Edit, "Rename", onRename)
                if (onInfo != null) ActionIcon(Icons.Filled.Info, "Info", onInfo)
                ActionIcon(Icons.Filled.Delete, "Delete", onDelete)
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = PureWhite)
        Text(label, color = MutedWhite, style = MaterialTheme.typography.labelSmall)
    }
}
