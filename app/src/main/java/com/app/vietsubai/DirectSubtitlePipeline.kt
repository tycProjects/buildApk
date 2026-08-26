package com.app.vietsubai

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DirectSubtitlePipeline(private val context: Context, private val api: AiSubtitleApi) {
    suspend fun run(videoUri: Uri, mode: String, source: String, target: String, progress: (Int, String) -> Unit): MutableList<SubtitleCue> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "ai-${System.currentTimeMillis()}").apply { mkdirs() }
        val video = File(dir, "input.mp4")
        context.contentResolver.openInputStream(videoUri)!!.use { input -> video.outputStream().use { input.copyTo(it) } }
        val raw = if (mode == "stt") stt(video, dir, source, progress) else ocr(video, dir, progress)
        progress(85, "Gemini đang dịch phụ đề...")
        val translated = api.translate(raw, source, target)
        progress(100, "Hoàn tất nhận dạng và dịch")
        translated
    }

    private fun stt(video: File, dir: File, source: String, progress: (Int, String) -> Unit): MutableList<SubtitleCue> {
        val audio = File(dir, "audio.flac")
        val session = FFmpegKit.execute("-y -i '${video.absolutePath}' -vn -ac 1 -ar 16000 -c:a flac '${audio.absolutePath}'")
        check(ReturnCode.isSuccess(session.returnCode)) { "Không tách được audio" }
        progress(55, "Groq Whisper đang nhận dạng...")
        return api.transcribe(audio, source)
    }

    private fun ocr(video: File, dir: File, progress: (Int, String) -> Unit): MutableList<SubtitleCue> {
        val retriever = MediaMetadataRetriever().apply { setDataSource(video.absolutePath) }
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val result = mutableListOf<SubtitleCue>(); val step = 4000L; var time = 0L
        while (time < duration) {
            val frame = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) api.ocrFrame(frame, time)?.let { cue -> if (result.none { it.text == cue.text && kotlin.math.abs(it.startMs - cue.startMs) < 2500 }) result += cue }
            progress((time * 75 / duration.coerceAtLeast(1)).toInt(), "Gemini OCR khung hình ${time / 1000}s")
            time += step
        }
        retriever.release(); return result
    }
}
