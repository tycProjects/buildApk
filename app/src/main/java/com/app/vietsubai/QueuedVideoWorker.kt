package com.app.vietsubai

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QueuedVideoWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return@withContext failure("Thiếu video")
        val historyId = inputData.getString(KEY_HISTORY_ID) ?: return@withContext failure("Thiếu mã lịch sử")
        val mode = inputData.getString(KEY_MODE) ?: "stt"
        val source = inputData.getString(KEY_SOURCE) ?: "auto"
        val target = inputData.getString(KEY_TARGET) ?: "vi"
        val bitrate = inputData.getString(KEY_BITRATE) ?: "2500k"
        val resolution = inputData.getString(KEY_RESOLUTION) ?: "original"
        val preset = inputData.getString(KEY_PRESET) ?: "veryfast"
        val style = SubtitleStyle(
            inputData.getString(KEY_FONT) ?: "Arial",
            inputData.getInt(KEY_FONT_SIZE, 22),
            inputData.getString(KEY_PRIMARY_COLOR) ?: "#FFFFFF",
            inputData.getString(KEY_OUTLINE_COLOR) ?: "#000000",
            inputData.getInt(KEY_OUTLINE, 2),
            inputData.getInt(KEY_ALIGNMENT, 2)
        )
        val format = runCatching { SubtitleFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "SRT") }.getOrDefault(SubtitleFormat.SRT)
        val history = HistoryStore(applicationContext)
        try {
            setForeground(ExportNotification.create(applicationContext, id, "Đang xử lý video trong hàng đợi"))
            history.update(historyId, "RUNNING")
            val keys = ApiKeyStore(applicationContext)
            check(keys.isConfigured()) { "Chưa cấu hình Gemini/Groq API key" }
            val api = AiSubtitleApi(keys.gemini(), keys.groq())
            val cues = DirectSubtitlePipeline(applicationContext, api).run(uri, mode, source, target) { percent, message ->
                history.update(historyId, "RUNNING")
                setProgressAsync(workDataOf("progress" to percent, "message" to message))
            }
            val output = FfmpegRenderer(applicationContext).burnIn(uri, cues, bitrate, resolution, preset, style, format) { percent ->
                setProgressAsync(workDataOf("progress" to percent, "message" to "Burn-in $percent%"))
            }
            history.update(historyId, "SUCCESS", output.absolutePath)
            Result.success(workDataOf(KEY_OUTPUT to output.absolutePath))
        } catch (e: CancellationException) {
            history.update(historyId, "CANCELLED", error = "Đã hủy")
            throw e
        } catch (e: Throwable) {
            history.update(historyId, "FAILED", error = e.message)
            if (runAttemptCount < 2) Result.retry() else failure(e.message ?: "Lỗi không xác định")
        }
    }

    private fun failure(message: String): Result = Result.failure(workDataOf("error" to message))

    companion object {
        const val KEY_URI = "uri"
        const val KEY_HISTORY_ID = "history_id"
        const val KEY_MODE = "mode"
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
        const val KEY_BITRATE = "bitrate"
        const val KEY_RESOLUTION = "resolution"
        const val KEY_PRESET = "preset"
        const val KEY_FONT = "font"
        const val KEY_FORMAT = "format"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_PRIMARY_COLOR = "primary_color"
        const val KEY_OUTLINE_COLOR = "outline_color"
        const val KEY_OUTLINE = "outline"
        const val KEY_ALIGNMENT = "alignment"
        const val KEY_OUTPUT = "output"
    }
}
