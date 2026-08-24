package com.ryan.videodownload.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import com.ryan.videodownload.data.downloader.FastDownloader
import com.ryan.videodownload.data.downloader.VideoExtractor
import com.ryan.videodownload.data.model.DownloadStatus
import com.ryan.videodownload.data.model.DownloadTask
import com.ryan.videodownload.data.model.VideoInfo
import com.ryan.videodownload.data.model.VideoQuality
import com.ryan.videodownload.utils.MediaStoreHelper
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class DownloadRepository(private val context: Context) {

    private val extractor = VideoExtractor()
    private val downloader = FastDownloader(maxConnections = 4)

    val tasks: StateFlow<List<DownloadTask>> = downloader.tasks

    suspend fun analyzeUrl(url: String): Result<VideoInfo> {
        return extractor.extract(url)
    }

    fun detectPlatform(url: String) = extractor.detectPlatform(url)

    suspend fun download(
        videoInfo: VideoInfo,
        quality: VideoQuality,
        onProgress: (DownloadTask) -> Unit = {}
    ): Result<File> {
        val task = DownloadTask(
            videoInfo = videoInfo,
            selectedQuality = quality,
            status = DownloadStatus.DOWNLOADING
        )

        val dir = getDownloadDir()
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created && !dir.exists()) {
                return Result.failure(Exception("Không tạo được thư mục lưu: ${dir.absolutePath}"))
            }
        }

        val result = downloader.startDownload(task, dir, onProgress)

        // Sau khi tải xong → tự động lưu vào album máy (Gallery)
        result.onSuccess { file ->
            val displayName = file.name
            val mime = MediaStoreHelper.mimeFromFormat(quality.format)
            val galleryUri = MediaStoreHelper.saveVideoToGallery(
                context = context,
                sourceFile = file,
                displayName = displayName,
                mimeType = mime
            )
            if (galleryUri == null) {
                android.util.Log.w("DownloadRepository", "Không lưu được vào album: ${file.absolutePath}")
            }
        }

        return result
    }

    fun cancel(taskId: String) {
        downloader.cancel(taskId)
    }

    /**
     * Thư mục lưu video tạm (app-specific) trước khi copy sang Gallery.
     *
     * - Android 10+ (API 29+): app-specific external
     *   → Android/data/<package>/files/Download/VideoDownloader
     * - Android 9 trở xuống: public Downloads/VideoDownloader
     */
    fun getDownloadDir(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "VideoDownloader")
                ?: File(context.filesDir, "downloads")
        } else {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "VideoDownloader"
            )
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                publicDir
            } else {
                File(context.getExternalFilesDir(null), "downloads")
            }
        }
    }

    fun getDownloadDirPath(): String = getDownloadDir().absolutePath
}
