package com.ryan.videodownload.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.videodownload.data.model.DownloadStatus
import com.ryan.videodownload.data.model.DownloadTask
import com.ryan.videodownload.data.model.VideoInfo
import com.ryan.videodownload.data.model.VideoQuality
import com.ryan.videodownload.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val urlInput: String = "",
    val isAnalyzing: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val selectedQuality: VideoQuality? = null,
    val currentTask: DownloadTask? = null,
    val history: List<DownloadTask> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showQualitySheet: Boolean = false,
    /** Task đang xem video trong app (null = đóng player) */
    val playingTask: DownloadTask? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.tasks.collect { tasks ->
                _uiState.update { state ->
                    val active = tasks.find {
                        it.status == DownloadStatus.DOWNLOADING ||
                                it.status == DownloadStatus.ANALYZING
                    }
                    state.copy(
                        history = tasks.sortedByDescending { it.createdAt },
                        // Luôn lấy task đang tải mới nhất để thanh tiến trình cập nhật realtime
                        currentTask = active ?: state.currentTask?.takeIf {
                            it.status == DownloadStatus.DOWNLOADING ||
                                    it.status == DownloadStatus.ANALYZING
                        }
                    )
                }
            }
        }
    }

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(urlInput = url, errorMessage = null) }
    }

    fun analyzeUrl() {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập URL") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    videoInfo = null,
                    selectedQuality = null
                )
            }

            repository.analyzeUrl(url)
                .onSuccess { info ->
                    if (info.qualities.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                videoInfo = info,
                                selectedQuality = null,
                                showQualitySheet = false,
                                errorMessage = "Đã nhận «${info.title}» nhưng không có link tải. Thử lại sau."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isAnalyzing = false,
                                videoInfo = info,
                                selectedQuality = info.qualities.firstOrNull(),
                                showQualitySheet = true,
                                errorMessage = null
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            errorMessage = e.message ?: "Không thể phân tích URL"
                        )
                    }
                }
        }
    }

    fun selectQuality(quality: VideoQuality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    fun startDownload() {
        val state = _uiState.value
        val info = state.videoInfo ?: return
        val quality = state.selectedQuality ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(showQualitySheet = false, errorMessage = null, successMessage = null) }

            repository.download(info, quality) { task ->
                _uiState.update { s -> s.copy(currentTask = task) }
            }.onSuccess { (file, galleryUri) ->
                val msg = if (galleryUri != null) {
                    "Đã tải xong và lưu vào album máy: ${file.name}"
                } else {
                    "Đã tải xong: ${file.name} (không lưu được vào album)"
                }
                _uiState.update { it.copy(successMessage = msg) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Tải xuống thất bại")
                }
            }
        }
    }

    fun dismissQualitySheet() {
        _uiState.update { it.copy(showQualitySheet = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun setSharedUrl(url: String) {
        _uiState.update { it.copy(urlInput = url) }
        analyzeUrl()
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
    }

    /** Mở trình phát video trong app */
    fun playVideo(task: DownloadTask) {
        if (task.status == DownloadStatus.COMPLETED && !task.filePath.isNullOrBlank()) {
            _uiState.update { it.copy(playingTask = task) }
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(playingTask = null) }
    }
}
