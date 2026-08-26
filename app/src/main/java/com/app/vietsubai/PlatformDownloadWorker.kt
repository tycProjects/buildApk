package com.app.vietsubai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PlatformDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = ExportNotification.create(applicationContext, id, "Đang chuẩn bị tải video")

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val input = inputData.getString(KEY_URL)?.trim().orEmpty()
        if (input.isBlank()) return@withContext failure("URL trống")
        setForeground(ExportNotification.create(applicationContext, id, "Đang kiểm tra URL video"))
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(input)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "video/*,application/octet-stream;q=0.9,*/*;q=0.5")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext failure("Máy chủ trả về HTTP ${response.code}. Link có thể đã hết hạn.")
                val body = response.body ?: return@withContext failure("URL không có dữ liệu")
                val contentType = body.contentType()?.toString().orEmpty().lowercase()
                if (contentType.contains("text/html") || contentType.contains("application/json")) {
                    return@withContext failure("URL này là trang Douyin/Bilibili, không phải file video trực tiếp. Link b23.tv có thể đã hết hạn; hãy dùng link media trực tiếp hoặc link b23.tv còn hiệu lực.")
                }
                val length = body.contentLength()
                val outputDir = File(applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: applicationContext.filesDir, "downloads").apply { mkdirs() }
                val output = File(outputDir, "vietsub-${System.currentTimeMillis()}.mp4")
                body.byteStream().use { inputStream ->
                    output.outputStream().buffered(256 * 1024).use { outputStream ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val count = inputStream.read(buffer)
                            if (count < 0) break
                            outputStream.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 400L) {
                                lastReport = now
                                val percent = if (length > 0) (downloaded * 100 / length).toInt().coerceIn(0, 99) else 0
                                setProgress(workDataOf("progress" to percent, "downloaded" to downloaded, "total" to length))
                                setForeground(ExportNotification.create(applicationContext, id, "Đang tải video: $percent%"))
                            }
                        }
                    }
                }
                if (output.length() < 1024L) {
                    output.delete()
                    return@withContext failure("Dữ liệu tải xuống quá nhỏ, không phải video hợp lệ")
                }
                setProgress(workDataOf("progress" to 100, "downloaded" to output.length(), "total" to length))
                Result.success(workDataOf(KEY_OUTPUT to output.absolutePath))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            failure(e.message ?: "Không thể tải video")
        }
    }

    private fun failure(message: String): Result = Result.failure(workDataOf("error" to message))

    companion object {
        const val KEY_URL = "url"
        const val KEY_OUTPUT = "output"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"
    }
}
