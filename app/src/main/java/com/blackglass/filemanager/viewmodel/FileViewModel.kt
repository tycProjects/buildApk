package com.blackglass.filemanager.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackglass.filemanager.data.FileItem
import com.blackglass.filemanager.data.FileRepository
import com.blackglass.filemanager.data.SortDirection
import com.blackglass.filemanager.data.SortMode
import com.blackglass.filemanager.data.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ClipboardAction { data class Copy(val file: File) : ClipboardAction(); data class Move(val file: File) : ClipboardAction() }

data class FileUiState(
    val currentDir: File,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val selected: Set<String> = emptySet(),
    val clipboard: ClipboardAction? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class FileViewModel(private val rootDir: File) : ViewModel() {

    private val backStack = ArrayDeque<File>()

    private val _state = MutableStateFlow(FileUiState(currentDir = rootDir))
    val state: StateFlow<FileUiState> = _state.asStateFlow()

    var displayItems by mutableStateOf<List<FileItem>>(emptyList())
        private set

    init {
        refresh()
    }

    private fun applyFilterSort(items: List<FileItem>): List<FileItem> {
        val query = _state.value.searchQuery.trim()
        val filtered = if (query.isBlank()) items else items.filter {
            it.name.contains(query, ignoreCase = true)
        }
        val dirsFirst = filtered.sortedWith(
            compareByDescending<FileItem> { it.isDirectory }
        )
        val sorted = when (_state.value.sortMode) {
            SortMode.NAME -> dirsFirst.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            SortMode.DATE -> dirsFirst.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }.thenBy { it.lastModified }
            )
            SortMode.SIZE -> dirsFirst.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }.thenBy { it.sizeBytes }
            )
            SortMode.TYPE -> dirsFirst.sortedWith(
                compareByDescending<FileItem> { it.isDirectory }.thenBy { it.extension }
            )
        }
        return if (_state.value.sortDirection == SortDirection.DESCENDING) {
            // keep folders first even when reversed
            val (dirs, files) = sorted.partition { it.isDirectory }
            dirs + files.reversed()
        } else sorted
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val items = withContext(Dispatchers.IO) {
                FileRepository.listChildren(_state.value.currentDir)
            }
            val current = _state.value.copy(isLoading = false, items = items)
            _state.value = current
            displayItems = applyFilterSort(items)
        }
    }

    fun openDirectory(item: FileItem) {
        if (!item.isDirectory) return
        backStack.addLast(_state.value.currentDir)
        _state.value = _state.value.copy(currentDir = item.file, searchQuery = "", selected = emptySet())
        refresh()
    }

    fun navigateTo(target: File) {
        if (target == _state.value.currentDir) return
        backStack.addLast(_state.value.currentDir)
        _state.value = _state.value.copy(currentDir = target, searchQuery = "", selected = emptySet())
        refresh()
    }

    fun goBack(): Boolean {
        if (backStack.isEmpty()) return false
        val prev = backStack.removeLast()
        _state.value = _state.value.copy(currentDir = prev, searchQuery = "", selected = emptySet())
        refresh()
        return true
    }

    /**
     * Breadcrumb trail from the storage root down to the current directory.
     * Stops at [rootDir] (external storage root) so the trail never walks
     * above the folder the app is allowed to start browsing from.
     */
    fun breadcrumbSegments(): List<File> {
        val segments = mutableListOf<File>()
        var f: File? = _state.value.currentDir
        while (f != null) {
            segments.add(0, f)
            if (f.absolutePath == rootDir.absolutePath) break
            f = f.parentFile
        }
        return segments
    }

    fun setSearchQuery(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
        displayItems = applyFilterSort(_state.value.items)
    }

    fun setSortMode(mode: SortMode) {
        val dir = if (mode == _state.value.sortMode) {
            if (_state.value.sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
        } else SortDirection.ASCENDING
        _state.value = _state.value.copy(sortMode = mode, sortDirection = dir)
        displayItems = applyFilterSort(_state.value.items)
    }

    fun toggleViewMode() {
        _state.value = _state.value.copy(
            viewMode = if (_state.value.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        )
    }

    fun toggleSelection(item: FileItem) {
        val current = _state.value.selected
        val updated = if (current.contains(item.path)) current - item.path else current + item.path
        _state.value = _state.value.copy(selected = updated)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    fun selectedItems(): List<FileItem> =
        displayItems.filter { _state.value.selected.contains(it.path) }

    fun createFolder(name: String) {
        val result = FileRepository.createFolder(_state.value.currentDir, name)
        result.onSuccess { refresh() }.onFailure {
            _state.value = _state.value.copy(errorMessage = it.message)
        }
    }

    fun createFile(name: String) {
        val result = FileRepository.createFile(_state.value.currentDir, name)
        result.onSuccess { refresh() }.onFailure {
            _state.value = _state.value.copy(errorMessage = it.message)
        }
    }

    fun rename(item: FileItem, newName: String) {
        val result = FileRepository.rename(item.file, newName)
        result.onSuccess { refresh() }.onFailure {
            _state.value = _state.value.copy(errorMessage = it.message)
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val items = selectedItems()
            withContext(Dispatchers.IO) {
                items.forEach { FileRepository.delete(it.file) }
            }
            clearSelection()
            refresh()
        }
    }

    fun setClipboard(action: ClipboardAction) {
        _state.value = _state.value.copy(clipboard = action, infoMessage = "Ready to paste here")
        clearSelection()
    }

    fun pasteClipboard() {
        val action = _state.value.clipboard ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (action) {
                    is ClipboardAction.Copy -> FileRepository.copy(action.file, _state.value.currentDir)
                    is ClipboardAction.Move -> FileRepository.move(action.file, _state.value.currentDir)
                }
            }
            _state.value = _state.value.copy(clipboard = null)
            refresh()
        }
    }

    fun consumeMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }
}
