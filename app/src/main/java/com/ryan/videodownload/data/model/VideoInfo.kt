package com.ryan.videodownload.data.model

import java.util.UUID

enum class DownloadStatus {
    IDLE, ANALYZING, READY, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

enum class Platform {
    YOUTUBE, TIKTOK, INSTAGRAM, FACEBOOK, TWITTER, VIMEO, OTHER
}

data class VideoQuality(
    val quality: String,          // e.g. "1080p", "720p", "480p", "Audio"
    val format: String,           // mp4, webm, m4a
    val sizeBytes: Long? = null,
    val url: String,
    val hasAudio: Boolean = true,
    val fps: Int? = null
) {
    fun sizeFormatted(): String {
        if (sizeBytes == null) return "—"
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024)
        else String.format("%.1f MB", mb)
    }
}

data class VideoInfo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val thumbnailUrl: String? = null,
    val duration: Long = 0,           // seconds
    val platform: Platform = Platform.OTHER,
    val originalUrl: String,
    val author: String? = null,
    val qualities: List<VideoQuality> = emptyList(),
    val description: String? = null
) {
    fun durationFormatted(): String {
        val h = duration / 3600
        val m = (duration % 3600) / 60
        val s = duration % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val videoInfo: VideoInfo,
    val selectedQuality: VideoQuality,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Float = 0f,           // 0.0 - 1.0
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun progressPercent(): Int = (progress * 100).toInt()

    fun speedFormatted(): String {
        if (speedBytesPerSec <= 0) return "—"
        val kb = speedBytesPerSec / 1024.0
        return if (kb >= 1024) String.format("%.1f MB/s", kb / 1024)
        else String.format("%.0f KB/s", kb)
    }

    fun sizeProgress(): String {
        val down = downloadedBytes / (1024.0 * 1024.0)
        val total = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else 0.0
        return if (total > 0) String.format("%.1f / %.1f MB", down, total)
        else String.format("%.1f MB", down)
    }
}
