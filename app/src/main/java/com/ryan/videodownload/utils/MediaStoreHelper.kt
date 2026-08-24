package com.ryan.videodownload.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Lưu video vào album máy (Gallery / Photos) qua MediaStore.
 * Hoạt động đúng với Scoped Storage (Android 10+).
 */
object MediaStoreHelper {

    /**
     * Copy file video đã tải vào thư mục Movies/VideoDownloader trên máy
     * và đăng ký với MediaStore để hiện trong album.
     *
     * @return Uri của video trong MediaStore, hoặc null nếu thất bại.
     */
    fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String = "video/mp4"
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null

        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_MOVIES + "/VideoDownloader"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.SIZE, sourceFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "VideoDownloader"
                )
                if (!destDir.exists()) destDir.mkdirs()
                val destFile = File(destDir, displayName)
                put(MediaStore.Video.Media.DATA, destFile.absolutePath)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            // Xóa entry lỗi
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    fun mimeFromFormat(format: String): String = when (format.lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> "video/mp4"
    }
}
