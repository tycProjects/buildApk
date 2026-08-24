package com.ryan.vietsubai.media

import com.ryan.vietsubai.model.SubtitleSegment
import com.ryan.vietsubai.tts.TtsSynchronizer
import java.io.File

/**
 * Places each rendered TTS clip at its subtitle's start time inside one continuous PCM16 mono
 * track spanning the whole video, producing a single WAV file that [ProcessingWorker] then hands
 * to Media3 Transformer as the dubbed audio track.
 */
object AudioTrackComposer {
    private const val FALLBACK_SAMPLE_RATE = 22050

    fun compose(
        totalDurationMs: Long,
        segments: List<SubtitleSegment>,
        clipFiles: Map<Int, File>,
        outFile: File
    ): Boolean {
        if (clipFiles.isEmpty()) return false
        val sampleRate = clipFiles.values.firstNotNullOfOrNull { WavUtils.read(it)?.sampleRate } ?: FALLBACK_SAMPLE_RATE
        val totalSamples = ((totalDurationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(1)
        val mix = ShortArray(totalSamples)
        segments.forEachIndexed { index, seg ->
            val file = clipFiles[index] ?: return@forEachIndexed
            val raw = WavUtils.read(file) ?: return@forEachIndexed
            if (raw.samples.isEmpty()) return@forEachIndexed
            val startMs = (seg.start * 1000).toLong()
            val endMs = (seg.end * 1000).toLong()
            val generatedMs = (raw.samples.size.toDouble() / raw.sampleRate * 1000).toLong()
            val plan = TtsSynchronizer.plan(startMs, endMs, generatedMs)
            val fitted = WavUtils.resample(raw, plan.playbackRate)
            val startSample = ((startMs / 1000.0) * sampleRate).toInt().coerceAtLeast(0)
            val windowSamples = (((endMs - startMs).coerceAtLeast(0) / 1000.0) * sampleRate).toInt()
            val maxCopy = (mix.size - startSample).coerceAtLeast(0)
            val copyLength = fitted.samples.size.coerceAtMost(maxCopy).let { if (windowSamples > 0) it.coerceAtMost(windowSamples + (sampleRate / 5)) else it }
            for (i in 0 until copyLength) {
                mix[startSample + i] = fitted.samples[i]
            }
        }
        WavUtils.write(outFile, PcmAudio(sampleRate, 1, mix))
        return true
    }
}
