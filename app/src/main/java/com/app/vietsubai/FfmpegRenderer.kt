package com.app.vietsubai

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionState
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FfmpegRenderer(private val context: Context) {
    suspend fun burnIn(videoUri: Uri, cues: List<SubtitleCue>, bitrate: String = "2500k", resolution: String = "original", preset: String = "veryfast", style: SubtitleStyle = SubtitleStyle(), format: SubtitleFormat = SubtitleFormat.SRT, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "render").apply { mkdirs() }
        val input = File(workDir, "input.mp4")
        val subtitleFile = File(workDir, "translated.${format.name.lowercase()}")
        val output = File(workDir, "translated-${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(videoUri)!!.use { inputStream -> input.outputStream().use { inputStream.copyTo(it) } }
        subtitleFile.writeText(SubtitleSerializer.serialize(cues, format, style), Charsets.UTF_8)

        val durationMs = probeDurationMs(input)
        val subtitlePath = subtitleFile.absolutePath.replace("\\", "/").replace(":", "\\:").replace("'", "\\'")
        val subtitleFilter = "subtitles='$subtitlePath':force_style='${style.forceStyle()}'"
        val scaleFilter = when (resolution) {
            "640x360" -> "scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2"
            "1280x720" -> "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2"
            "1920x1080" -> "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2"
            else -> ""
        }
        val safeBitrate = if (Regex("^\\d{3,5}k$").matches(bitrate)) bitrate else "2500k"
        val safePreset = preset.takeIf { it in setOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium") } ?: "veryfast"
        val vf = listOf(scaleFilter, subtitleFilter).filter { it.isNotBlank() }.joinToString(",")
        val command = "-y -i '${input.absolutePath}' -vf \"$vf\" -c:v libx264 -preset $safePreset -b:v $safeBitrate -maxrate $safeBitrate -bufsize ${safeBitrate.removeSuffix("k").toInt() * 2}k -pix_fmt yuv420p -c:a aac -b:a 128k -movflags +faststart '${output.absolutePath}'"
        val session = FFmpegKit.executeAsync(command, { session ->
            check(ReturnCode.isSuccess(session.returnCode)) { "FFmpeg failed: ${session.failStackTrace}" }
        }, { log -> }, { stats: Statistics ->
            if (durationMs > 0) onProgress((stats.time * 100 / durationMs).toInt().coerceIn(0, 99))
        })
        while (session.state != SessionState.COMPLETED && session.state != SessionState.FAILED) Thread.sleep(100)
        check(output.exists() && output.length() > 0) { "Không tạo được video output" }
        onProgress(100)
        output
    }

    private fun probeDurationMs(file: File): Long {
        val mediaInfo = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(file.absolutePath).mediaInformation
        return ((mediaInfo?.duration?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }
}
