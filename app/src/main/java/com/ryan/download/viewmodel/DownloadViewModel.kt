package com.ryan.download.viewmodel

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.download.data.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class UiState(
    val url: String = "",
    val videoInfo: VideoInfo? = null,
    val selectedFormat: VideoFormat? = null,
    val downloadState: DownloadState = DownloadState.IDLE,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    application: Application,
    private val downloadDao: DownloadDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val history: StateFlow<List<DownloadItem>> = downloadDao.getAll()
        .map { list ->
            list.map {
                DownloadItem(
                    id = it.id, title = it.title, thumbnail = it.thumbnail, url = it.url,
                    progress = 1f,
                    state = try { DownloadState.valueOf(it.state) } catch (_: Exception) { DownloadState.COMPLETED },
                    filePath = it.filePath, timestamp = it.timestamp
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateUrl(url: String) = _uiState.update { it.copy(url = url, errorMessage = null) }

    fun analyzeUrl() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập URL") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(downloadState = DownloadState.ANALYZING, errorMessage = null, videoInfo = null, selectedFormat = null)
            }
            try {
                val ytdlInfo = YoutubeDL.getInstance().getInfo(url)
                val formats = listOf(
                    VideoFormat("best", "mp4", "Best", note = "Tốt nhất"),
                    VideoFormat("bv*+ba/b", "mp4", "Best Video+Audio", note = "Gộp tốt nhất"),
                    VideoFormat("bv*[height<=1080]+ba/b", "mp4", "1080p", note = "Full HD"),
                    VideoFormat("bv*[height<=720]+ba/b", "mp4", "720p", note = "HD"),
                    VideoFormat("bv*[height<=480]+ba/b", "mp4", "480p", note = "SD"),
                    VideoFormat("bestaudio", "m4a", "Audio Only", isAudioOnly = true, note = "Chỉ âm thanh")
                )
                val info = VideoInfo(
                    id = ytdlInfo.id ?: UUID.randomUUID().toString(),
                    title = ytdlInfo.title ?: "Unknown",
                    uploader = ytdlInfo.uploader ?: "",
                    thumbnail = ytdlInfo.thumbnail ?: "",
                    duration = ytdlInfo.duration ?: 0L,
                    formats = formats, webpageUrl = url
                )
                _uiState.update {
                    it.copy(videoInfo = info, selectedFormat = formats.firstOrNull { !it.isAudioOnly }, downloadState = DownloadState.READY)
                }
            } catch (e: Exception) {
                Log.e("TaiVideo", "Analyze failed", e)
                _uiState.update { it.copy(downloadState = DownloadState.ERROR, errorMessage = e.message ?: "Không thể phân tích URL") }
            }
        }
    }

    fun selectFormat(format: VideoFormat) = _uiState.update { it.copy(selectedFormat = format) }

    fun startDownload() {
        val state = _uiState.value
        val info = state.videoInfo ?: return
        val format = state.selectedFormat ?: return
        val url = state.url
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(downloadState = DownloadState.DOWNLOADING, progress = 0f, errorMessage = null) }
            try {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TaiVideo")
                if (!dir.exists()) dir.mkdirs()
                val request = YoutubeDLRequest(url)
                request.addOption("-f", format.formatId)
                request.addOption("-o", "${dir.absolutePath}/%(title).180B.%(ext)s")
                request.addOption("--no-playlist"); request.addOption("--no-mtime"); request.addOption("--newline")
                request.addOption("--concurrent-fragments", "8")
                request.addOption("--fragment-retries", "10"); request.addOption("--retries", "5")
                request.addOption("--buffer-size", "64K"); request.addOption("--http-chunk-size", "10M")
                request.addOption("--downloader", "aria2c")
                request.addOption("--downloader-args", "aria2c:\"-x 16 -s 16 -k 1M --max-connection-per-server=16\"")
                request.addOption("--prefer-free-formats"); request.addOption("--no-check-certificates")
                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                    _uiState.update { it.copy(progress = (progress / 100f).coerceIn(0f, 1f)) }
                }
                downloadDao.insert(DownloadEntity(UUID.randomUUID().toString(), info.title, info.thumbnail, url, dir.absolutePath, DownloadState.COMPLETED.name))
                _uiState.update { it.copy(downloadState = DownloadState.COMPLETED, progress = 1f) }
            } catch (e: Exception) {
                Log.e("TaiVideo", "Download failed, fallback", e)
                tryFallback(url, format, info)
            }
        }
    }

    private suspend fun tryFallback(url: String, format: VideoFormat, info: VideoInfo) {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TaiVideo")
            if (!dir.exists()) dir.mkdirs()
            val request = YoutubeDLRequest(url)
            request.addOption("-f", format.formatId)
            request.addOption("-o", "${dir.absolutePath}/%(title).180B.%(ext)s")
            request.addOption("--no-playlist"); request.addOption("--concurrent-fragments", "4"); request.addOption("--newline")
            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                _uiState.update { it.copy(progress = (progress / 100f).coerceIn(0f, 1f)) }
            }
            downloadDao.insert(DownloadEntity(UUID.randomUUID().toString(), info.title, info.thumbnail, url, dir.absolutePath, DownloadState.COMPLETED.name))
            _uiState.update { it.copy(downloadState = DownloadState.COMPLETED, progress = 1f) }
        } catch (e: Exception) {
            _uiState.update { it.copy(downloadState = DownloadState.ERROR, errorMessage = e.message ?: "Tải thất bại") }
        }
    }

    fun clearHistory() = viewModelScope.launch { downloadDao.clearAll() }
    fun reset() = _uiState.update {
        it.copy(videoInfo = null, selectedFormat = null, downloadState = DownloadState.IDLE, progress = 0f, errorMessage = null)
    }
}
