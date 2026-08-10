package com.blackglass.filemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.blackglass.filemanager.data.FileItem
import com.blackglass.filemanager.data.ViewMode
import com.blackglass.filemanager.ui.components.BreadcrumbBar
import com.blackglass.filemanager.ui.components.ConfirmDeleteDialog
import com.blackglass.filemanager.ui.components.FileDetailsDialog
import com.blackglass.filemanager.ui.components.FileGridTile
import com.blackglass.filemanager.ui.components.FileRow
import com.blackglass.filemanager.ui.components.SearchAndSortBar
import com.blackglass.filemanager.ui.components.SelectionActionBar
import com.blackglass.filemanager.ui.components.StorageUsageBar
import com.blackglass.filemanager.ui.components.TextInputDialog
import com.blackglass.filemanager.ui.theme.JetBlack
import com.blackglass.filemanager.ui.theme.PureBlack
import com.blackglass.filemanager.ui.theme.PureWhite
import com.blackglass.filemanager.viewmodel.ClipboardAction
import com.blackglass.filemanager.viewmodel.FileViewModel
import java.io.File

@Composable
fun FileListScreen(
    viewModel: FileViewModel,
    usedBytes: Long,
    totalBytes: Long,
    onOpenFile: (FileItem) -> Unit,
    onShareFiles: (List<FileItem>) -> Unit,
    onBackAtRoot: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val items = viewModel.displayItems

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val msg = state.errorMessage ?: state.infoMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessages()
        }
    }

    val selectedItems = viewModel.selectedItems()
    val isSelecting = selectedItems.isNotEmpty()

    Scaffold(
        containerColor = PureBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = PureWhite,
                    contentColor = PureBlack
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New")
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = { showFabMenu = false; showCreateFolderDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("New file") },
                        leadingIcon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
                        onClick = { showFabMenu = false; showCreateFileDialog = true }
                    )
                    if (state.clipboard != null) {
                        DropdownMenuItem(
                            text = { Text("Paste here") },
                            leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                            onClick = { showFabMenu = false; viewModel.pasteClipboard() }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(PureBlack, JetBlack)))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    StorageUsageBar(
                        usedBytes = usedBytes,
                        totalBytes = totalBytes,
                        label = "Storage used"
                    )
                    BreadcrumbBar(
                        segments = viewModel.breadcrumbSegments(),
                        onSegmentClick = { viewModel.navigateTo(it) },
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (isSelecting) {
                    SelectionActionBar(
                        count = selectedItems.size,
                        onCopy = { viewModel.setClipboard(ClipboardAction.Copy(selectedItems.first().file)) },
                        onMove = { viewModel.setClipboard(ClipboardAction.Move(selectedItems.first().file)) },
                        onShare = { onShareFiles(selectedItems) },
                        onRename = if (selectedItems.size == 1) {
                            { renameTarget = selectedItems.first() }
                        } else null,
                        onInfo = if (selectedItems.size == 1) {
                            { detailsTarget = selectedItems.first() }
                        } else null,
                        onDelete = { showDeleteConfirm = true },
                        onClose = { viewModel.clearSelection() },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                } else {
                    SearchAndSortBar(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        sortMode = state.sortMode,
                        onCycleSort = {
                            val modes = com.blackglass.filemanager.data.SortMode.entries
                            val next = modes[(modes.indexOf(state.sortMode) + 1) % modes.size]
                            viewModel.setSortMode(next)
                        },
                        viewMode = state.viewMode,
                        onToggleView = { viewModel.toggleViewMode() },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "No matches" else "This folder is empty",
                            color = com.blackglass.filemanager.ui.theme.MutedWhite,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else if (state.viewMode == ViewMode.LIST) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                        items(items, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                isSelected = state.selected.contains(item.path),
                                onClick = {
                                    if (isSelecting) viewModel.toggleSelection(item)
                                    else if (item.isDirectory) viewModel.openDirectory(item)
                                    else onOpenFile(item)
                                },
                                onLongClick = { viewModel.toggleSelection(item) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        gridItems(items, key = { it.path }) { item ->
                            FileGridTile(
                                item = item,
                                isSelected = state.selected.contains(item.path),
                                onClick = {
                                    if (isSelecting) viewModel.toggleSelection(item)
                                    else if (item.isDirectory) viewModel.openDirectory(item)
                                    else onOpenFile(item)
                                },
                                onLongClick = { viewModel.toggleSelection(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        TextInputDialog(
            title = "New folder",
            confirmLabel = "Create",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false }
        )
    }
    if (showCreateFileDialog) {
        TextInputDialog(
            title = "New file",
            confirmLabel = "Create",
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { name -> viewModel.createFile(name); showCreateFileDialog = false }
        )
    }
    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name -> viewModel.rename(target, name); renameTarget = null; viewModel.clearSelection() }
        )
    }
    detailsTarget?.let { target ->
        FileDetailsDialog(item = target, onDismiss = { detailsTarget = null })
    }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            count = selectedItems.size,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { viewModel.deleteSelected(); showDeleteConfirm = false }
        )
    }
}
