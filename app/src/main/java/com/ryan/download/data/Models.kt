package com.ryan.download.data

data class VideoInfo(
    val id: String = "",
    val title: String = "",
    val uploader: String = "",
    val thumbnail: String = "",
    val duration: Long = 0,
    val formats: List<VideoFormat> = emptyList(),
    val webpageUrl: String = ""
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val filesize: Long? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val isAudioOnly: Boolean = false,
    val note: String = ""
)

enum class DownloadState {
    IDLE, ANALYZING, READY, DOWNLOADING, COMPLETED, ERROR
}

data class DownloadItem(
    val id: String,
    val title: String,
    val thumbnail: String,
    val url: String,
    val progress: Float = 0f,
    val state: DownloadState = DownloadState.IDLE,
    val filePath: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
