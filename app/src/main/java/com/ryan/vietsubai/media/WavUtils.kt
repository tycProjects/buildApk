package com.ryan.vietsubai.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcmAudio(val sampleRate: Int, val channels: Int, val samples: ShortArray)

/**
 * Minimal RIFF/WAVE PCM16 reader/writer. Android's `TextToSpeech.synthesizeToFile` always writes a
 * canonical 44-byte-header PCM16 WAV, so a tiny hand-rolled parser is enough here and avoids
 * pulling in a media-extraction dependency just to read our own generated clips.
 */
object WavUtils {
    fun read(file: File): PcmAudio? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF") { "Not a RIFF/WAVE file: ${file.name}" }
            val channels = buf.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = buf.getInt(24)
            val bitsPerSample = buf.getShort(34).toInt()
            val declaredDataSize = buf.getInt(40)
            val available = (raf.length() - 44).toInt().coerceAtLeast(0)
            val dataSize = declaredDataSize.coerceIn(0, available)
            val raw = ByteArray(dataSize)
            raf.readFully(raw)
            val samples = if (bitsPerSample == 16) {
                val shortBuf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                ShortArray(shortBuf.remaining()).also { shortBuf.get(it) }
            } else {
                ShortArray(0)
            }
            PcmAudio(sampleRate, channels, samples)
        }
    }.getOrNull()

    fun write(file: File, audio: PcmAudio) {
        val dataSize = audio.samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(audio.channels.toShort())
        buf.putInt(audio.sampleRate)
        buf.putInt(audio.sampleRate * audio.channels * 2)
        buf.putShort((audio.channels * 2).toShort())
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in audio.samples) buf.putShort(s)
        file.writeBytes(buf.array())
    }

    /**
     * Cheap linear-interpolation resampler used to fit a rendered TTS clip into its subtitle
     * window. `rate` > 1 speeds the clip up (shortens it), matching [com.ryan.vietsubai.tts.TtsSyncPlan.playbackRate].
     * This changes pitch slightly, a standard trade-off for lightweight on-device dubbing.
     */
    fun resample(audio: PcmAudio, rate: Float): PcmAudio {
        if (rate == 1f || audio.samples.isEmpty()) return audio
        val newLength = (audio.samples.size / rate).toInt().coerceAtLeast(1)
        val out = ShortArray(newLength)
        for (i in 0 until newLength) {
            val srcPos = i * rate
            val i0 = srcPos.toInt().coerceIn(0, audio.samples.size - 1)
            val i1 = (i0 + 1).coerceAtMost(audio.samples.size - 1)
            val frac = srcPos - i0
            out[i] = (audio.samples[i0] * (1 - frac) + audio.samples[i1] * frac).toInt().toShort()
        }
        return audio.copy(samples = out)
    }
}
