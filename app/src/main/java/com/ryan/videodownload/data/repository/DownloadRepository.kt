package com.ryan.videodownload.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.ryan.videodownload.data.downloader.FastDownloader
import com.ryan.videodownload.data.downloader.VideoExtractor
import com.ryan.videodownload.data.model.DownloadStatus
import com.ryan.videodownload.data.model.DownloadTask
import com.ryan.videodownload.data.model.VideoInfo
import com.ryan.videodownload.data.model.VideoQuality
import com.ryan.videodownload.utils.GalleryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(private val context: Context) {

    private val extractor = VideoExtractor()
    private val downloader = FastDownloader(maxConnections = 4)

    val tasks: StateFlow<List<DownloadTask>> = downloader.tasks

    suspend fun analyzeUrl(url: String): Result<VideoInfo> {
        return extractor.extract(url)
    }

    fun detectPlatform(url: String) = extractor.detectPlatform(url)

    /**
     * Tải video rồi tự động lưu vào album máy (Movies/VideoDownloader).
     * @return Pair(file app-local, galleryUri) hoặc failure.
     */
    suspend fun download(
        videoInfo: VideoInfo,
        quality: VideoQuality,
        onProgress: (DownloadTask) -> Unit = {}
    ): Result<Pair<File, Uri?>> {
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

        val downloadResult = downloader.startDownload(task, dir, onProgress)
        return downloadResult.fold(
            onSuccess = { file ->
                val galleryUri = withContext(Dispatchers.IO) {
                    GalleryHelper.saveVideoToGallery(
                        context = context,
                        sourceFile = file,
                        displayName = file.name,
                        title = videoInfo.title
                    )
                }
                Result.success(file to galleryUri)
            },
            onFailure = { Result.failure(it) }
        )
    }

    fun cancel(taskId: String) {
        downloader.cancel(taskId)
    }

    /**
     * Thư mục lưu tạm / app-specific:
     * - Android 10+: app-specific external (không cần permission)
     * - Android 9-: public Downloads (cần WRITE_EXTERNAL_STORAGE)
     * Sau khi tải xong sẽ copy sang Gallery (Movies/VideoDownloader).
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
