package com.ryan.vietsubai.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.work.*
import com.google.common.collect.ImmutableList
import com.ryan.vietsubai.data.AppDatabase
import com.ryan.vietsubai.data.RenderJobEntity
import com.ryan.vietsubai.model.SubtitleSegment
import com.ryan.vietsubai.model.BlurCropRegion
import com.ryan.vietsubai.tts.TtsRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class ProcessingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val db = AppDatabase.get(appContext)

    companion object {
        // On-device TTS engines are fairly light, but this caps how many render in parallel so a
        // long subtitle track doesn't spin up dozens of TextToSpeech instances at once.
        private const val TTS_CONCURRENCY = 4
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("render", "Vietsub AI Render", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, "render")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Vietsub AI")
            .setContentText("Đang render video…")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(2001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(2001, notification)
        }
    }

    override suspend fun doWork(): Result = coroutineScope {
        setForeground(getForegroundInfo())
        val sourceUri = inputData.getString("source_uri") ?: return@coroutineScope Result.failure()
        val trimStart = inputData.getLong("trim_start", 0L)
        val trimEnd = inputData.getLong("trim_end", 0L)
        val jobId = inputData.getString("job_id") ?: "render_${id}"
        val burnSubtitles = inputData.getBoolean("burn_subtitles", false)
        val removeOriginalSubtitle = inputData.getBoolean("remove_original_subtitle", false)
        val voice = inputData.getString("voice") ?: "vi-VN-HoaiMyNeural"
        val subtitles = SubtitleSerializer.fromJson(inputData.getString("subtitles_json"))
        val fontUri = inputData.getString("font_uri").orEmpty()
        val subtitleLeft = inputData.getFloat("subtitle_left", 0.08f)
        val subtitleTop = inputData.getFloat("subtitle_top", 0.72f)
        val subtitleRight = inputData.getFloat("subtitle_right", 0.92f)
        val subtitleBottom = inputData.getFloat("subtitle_bottom", 0.96f)
        val cropPreset = inputData.getString("crop_preset") ?: "original"
        val cropZoom = inputData.getFloat("crop_zoom", 1f)
        val outputFps = inputData.getInt("output_fps", 30).coerceIn(1, 120)
        val style = com.ryan.vietsubai.model.SubtitleStyle(
            fontSize = inputData.getFloat("style_font_size", 20f),
            textColor = inputData.getLong("style_text_color", 0xFFFFFFFF),
            strokeColor = inputData.getLong("style_stroke_color", 0xFF000000),
            strokeWidth = inputData.getFloat("style_stroke_width", 3f),
            shadow = inputData.getFloat("style_shadow", 6f),
            backgroundColor = inputData.getLong("style_background_color", 0L),
            backgroundRadius = inputData.getFloat("style_background_radius", 12f),
            align = inputData.getInt("style_align", 1),
            bold = inputData.getBoolean("style_bold", true),
            italic = inputData.getBoolean("style_italic", false),
            lineSpacing = inputData.getFloat("style_line_spacing", 1.2f),
            letterSpacing = inputData.getFloat("style_letter_spacing", 0f),
            animation = inputData.getString("style_animation") ?: "fade",
            animationMs = inputData.getInt("style_animation_ms", 180)
        )

        try {
            update(jobId, 5, "prepare", "running"); setProgress(workDataOf("stage" to "prepare", "progress" to 5))
            val meta = withContext(Dispatchers.IO) { readVideoMeta(sourceUri) }

            update(jobId, 30, "dubbing · TTS", "running"); setProgress(workDataOf("stage" to "dubbing", "progress" to 30))
            val dubbedAudioFile = if (subtitles.isNotEmpty()) {
                withContext(Dispatchers.IO) { synthesizeDubbedTrack(subtitles, meta.durationMs, voice) }
            } else null

            update(jobId, 65, "render/mux", "running"); setProgress(workDataOf("stage" to "render", "progress" to 65))
            // Transformer must be built/driven from a thread that has a prepared Looper (the main
            // thread qualifies); it dispatches its own encode/decode work internally.
            val output = withContext(Dispatchers.Main) {
                mux(
                    sourceUri = sourceUri,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    dubbedAudioFile = dubbedAudioFile,
                    burnSubtitles = burnSubtitles && subtitles.isNotEmpty(),
                    subtitles = subtitles,
                    fontUri = fontUri,
                    subtitleRegion = floatArrayOf(subtitleLeft, subtitleTop, subtitleRight, subtitleBottom),
                    meta = meta,
                    jobId = jobId,
                    outputFps = outputFps,
                    cropPreset = cropPreset,
                    cropZoom = cropZoom,
                    removeOriginalSubtitle = removeOriginalSubtitle,
                    style = style
                )
            }

            update(jobId, 100, "done", "done")
            setProgress(workDataOf("stage" to "done", "progress" to 100, "output_uri" to output.toString()))
            Result.success(workDataOf("output_uri" to output.toString()))
        } catch (t: Throwable) {
            update(jobId, 0, "error: ${t.message}", "failed")
            Result.retry()
        }
    }

    private fun readVideoMeta(sourceUri: String): VideoMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(applicationContext, Uri.parse(sourceUri))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val (width, height) = if (rotation == 90 || rotation == 270) rawHeight to rawWidth else rawWidth to rawHeight
            VideoMeta(duration, width, height)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Renders every subtitle line locally via TTS and lays the clips out on one continuous track.
     * Lines are independent, so they're rendered concurrently (bounded so we don't spin up dozens
     * of TextToSpeech engines at once) instead of one at a time — for a 100-line video this turns a
     * long serial wait into a handful of overlapping batches.
     */
    private suspend fun synthesizeDubbedTrack(subtitles: List<SubtitleSegment>, totalDurationMs: Long, voiceTag: String): File? = coroutineScope {
        val locale = localeFromVoiceTag(voiceTag)
        val cacheDir = File(applicationContext.cacheDir, "tts_render_${System.currentTimeMillis()}").apply { mkdirs() }
        val semaphore = Semaphore(TTS_CONCURRENCY)
        val clipFiles = subtitles.mapIndexed { index, seg -> index to seg }
            .filter { (_, seg) -> (seg.translation ?: seg.text).isNotBlank() }
            .map { (index, seg) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val text = seg.translation ?: seg.text
                        val out = File(cacheDir, "seg_$index.wav")
                        // Each TtsRenderer owns its own on-device TTS engine instance, so rendering
                        // concurrently is safe as long as concurrency is capped (see TTS_CONCURRENCY).
                        if (TtsRenderer(applicationContext).renderToFile(text, out, locale)) index to out else null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        if (clipFiles.isEmpty()) {
            cacheDir.deleteRecursively()
            return@coroutineScope null
        }
        val composed = File(applicationContext.cacheDir, "dubbed_${System.currentTimeMillis()}.wav")
        val ok = AudioTrackComposer.compose(totalDurationMs, subtitles, clipFiles, composed)
        cacheDir.deleteRecursively()
        if (ok) composed else null
    }

    private fun localeFromVoiceTag(voiceTag: String): Locale {
        // Voice tags look like "vi-VN-HoaiMyNeural"; only the language-region prefix matters for
        // the on-device TTS engine, which doesn't know about named cloud voices.
        val parts = voiceTag.split("-")
        return if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale("vi", "VN")
    }

    // NOTE: Composition/EditedMediaItemSequence constructor shapes and the exact Transformer.Listener
    // callback signatures have varied slightly between Media3 Transformer releases. This is written
    // against the media3-transformer 1.3.1 API declared in app/build.gradle; if Android Studio's
    // Gradle sync reports a signature mismatch here, adjust the call/override to match what's on the
    // classpath — the mux logic itself (mute original audio when dubbing, layer the dubbed WAV as a
    // second sequence, apply the subtitle overlay as a video effect) stays the same.
    private fun cropEffect(preset: String, width: Int, height: Int): Crop? {
        if (preset == "original") return null
        val target = when (preset) {
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "1:1" -> 1f
            "4:5" -> 4f / 5f
            else -> return null
        }
        val source = width.toFloat() / height.coerceAtLeast(1)
        return if (source > target) {
            val visible = target / source
            val side = (1f - visible) / 2f
            Crop(-1f + side * 2f, 1f - side * 2f, -1f, 1f)
        } else {
            val visible = source / target
            val side = (1f - visible) / 2f
            Crop(-1f, 1f, -1f + side * 2f, 1f - side * 2f)
        }
    }

    private suspend fun mux(
        sourceUri: String,
        trimStart: Long,
        trimEnd: Long,
        dubbedAudioFile: File?,
        burnSubtitles: Boolean,
        subtitles: List<SubtitleSegment>,
        fontUri: String,
        subtitleRegion: FloatArray,
        meta: VideoMeta,
        jobId: String,
        outputFps: Int,
        cropPreset: String,
        cropZoom: Float,
        removeOriginalSubtitle: Boolean,
        style: com.ryan.vietsubai.model.SubtitleStyle
    ): Uri = suspendCancellableCoroutine { cont ->
        val outputDir = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Vietsub AI").apply { mkdirs() }
        val outputFile = File(outputDir, "$jobId.mp4")

        val effects = mutableListOf<androidx.media3.common.Effect>()
        cropEffect(cropPreset, meta.width, meta.height)?.let { effects += it }
        if (cropZoom > 1.01f) effects += androidx.media3.effect.ScaleAndRotateTransformation.Builder().setScale(cropZoom, cropZoom).build()
        if (removeOriginalSubtitle) {
            val cover = BlurCropRegion("subtitle_cover", subtitleRegion[0], subtitleRegion[1], subtitleRegion[2], subtitleRegion[3])
            effects += OverlayEffect(ImmutableList.of(RegionCoverOverlay(listOf(cover), meta.width, meta.height)))
        }
        if (burnSubtitles) effects += OverlayEffect(ImmutableList.of(SubtitleBitmapOverlay(subtitles, meta.width, meta.height, fontUri, subtitleRegion, style)))
        val videoEffects = effects

        val mediaItem = MediaItem.Builder().setUri(Uri.parse(sourceUri)).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(trimStart).apply { if (trimEnd > trimStart) setEndPositionMs(trimEnd) }.build()
        ).build()
        val videoItem = EditedMediaItem.Builder(mediaItem)
            .setFrameRate(outputFps)
            .setRemoveAudio(dubbedAudioFile != null)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        val videoSequence = EditedMediaItemSequence(listOf(videoItem))

        val composition = if (dubbedAudioFile != null) {
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(dubbedAudioFile))).build()
            val audioSequence = EditedMediaItemSequence(listOf(audioItem))
            Composition.Builder(listOf(videoSequence, audioSequence)).build()
        } else {
            Composition.Builder(listOf(videoSequence)).build()
        }

        val transformer = Transformer.Builder(applicationContext)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Uri.fromFile(outputFile))
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.cancel(exportException)
                }
            })
            .build()

        transformer.start(composition, outputFile.absolutePath)
        cont.invokeOnCancellation { runCatching { transformer.cancel() } }
    }

    private suspend fun update(id: String, p: Int, stage: String, status: String) {
        db.renderJobDao().upsert(RenderJobEntity(id, inputData.getString("project_name") ?: "Video", p, stage, status))
    }
}

data class VideoMeta(val durationMs: Long, val width: Int, val height: Int)
