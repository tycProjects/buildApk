package com.ryan.videodownload.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Lưu video đã tải vào album / thư viện ảnh-video của máy (MediaStore).
 * Android 10+ dùng MediaStore + RELATIVE_PATH; API cũ dùng MediaScanner.
 */
object GalleryHelper {

    /**
     * Sao chép file video vào Movies/VideoDownloader (hiển thị trong Gallery / Photos).
     * @return Uri của video trong MediaStore, hoặc null nếu thất bại.
     */
    fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        displayName: String,
        title: String = displayName
    ): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null

        val mimeType = when (sourceFile.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceFile, displayName, title, mimeType)
        } else {
            saveViaLegacy(context, sourceFile, displayName, mimeType)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        title: String,
        mimeType: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.TITLE, title)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.SIZE, sourceFile.length())
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/VideoDownloader"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacy(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val destDir = File(moviesDir, "VideoDownloader")
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, displayName)
        return try {
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            null
        }
    }
}
