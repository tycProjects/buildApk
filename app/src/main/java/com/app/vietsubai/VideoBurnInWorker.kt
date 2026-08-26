package com.app.vietsubai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit

class VideoBurnInWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val callbackScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    companion object {
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_SRT = "srt"
        const val KEY_OUTPUT = "output_path"
        const val KEY_BITRATE = "video_bitrate"
        const val KEY_RESOLUTION = "video_resolution"
        const val KEY_PRESET = "video_preset"
        const val KEY_FONT = "font"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_PRIMARY_COLOR = "primary_color"
        const val KEY_OUTLINE_COLOR = "outline_color"
        const val KEY_OUTLINE = "outline"
        const val KEY_ALIGNMENT = "alignment"
        const val KEY_FORMAT = "format"
        private const val CHANNEL_ID = "video_export"
        private const val NOTIFICATION_ID = 2401
    }

    override suspend fun doWork(): Result {
        val video = inputData.getString(KEY_VIDEO_URI)?.let(Uri::parse) ?: return Result.failure(workDataOf("error" to "Thiếu video"))
        val srt = inputData.getString(KEY_SRT).orEmpty()
        val bitrate = inputData.getString(KEY_BITRATE) ?: "2500k"
        val resolution = inputData.getString(KEY_RESOLUTION) ?: "original"
        val preset = inputData.getString(KEY_PRESET) ?: "veryfast"
        if (srt.isBlank()) return Result.failure(workDataOf("error" to "SRT rỗng"))
        return try {
            setForeground(createForegroundInfo(0, "Đang chuẩn bị FFmpeg..."))
            val cues = SrtParser.parse(srt)
            val output = FfmpegRenderer(applicationContext).burnIn(video, cues, bitrate, resolution, preset) { percent ->
                callbackScope.launch {
                    setProgress(workDataOf("progress" to percent))
                    setForeground(createForegroundInfo(percent, "Đang burn-in phụ đề: $percent%"))
                }
            }
            Result.success(workDataOf(KEY_OUTPUT to output.absolutePath, "progress" to 100))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            Result.failure(workDataOf("error" to "Đã hủy export"))
        } catch (error: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf("error" to (error.message ?: "FFmpeg failed")))
        }
    }


    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Video export", NotificationManager.IMPORTANCE_LOW))
        }
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Vietsub AI")
            .setContentText(text)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Hủy", cancelIntent)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

}
