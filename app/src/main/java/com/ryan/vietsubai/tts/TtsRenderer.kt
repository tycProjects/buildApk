package com.ryan.vietsubai.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Renders a single subtitle line to a WAV file using the device's built-in TTS engine.
 * This is a fully local/offline fallback voice: no Gemini/Groq/network calls involved, so
 * render jobs can dub a video even without any AI provider configured.
 */
class TtsRenderer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun renderToFile(text: String, outFile: File, locale: Locale = Locale("vi", "VN")): Boolean {
        if (text.isBlank()) return false
        return suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appContext) { status ->
                val tts = engine
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                tts.language = locale
                val utteranceId = "seg_${System.nanoTime()}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId && cont.isActive) cont.resume(true)
                        tts.shutdown()
                    }
                    override fun onError(id: String?) {
                        if (id == utteranceId && cont.isActive) cont.resume(false)
                        tts.shutdown()
                    }
                })
                val result = tts.synthesizeToFile(text, Bundle(), outFile, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    if (cont.isActive) cont.resume(false)
                    tts.shutdown()
                }
            }
            cont.invokeOnCancellation { runCatching { engine?.shutdown() } }
        }
    }
}
