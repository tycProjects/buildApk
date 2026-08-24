package com.ryan.vietsubai.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local fallback TTS. AI TTS providers can implement the same contract later. */
class TtsService(context: Context) {
    private val tts=TextToSpeech(context,null)
    fun estimateDurationMs(text:String, locale:Locale=Locale("vi","VN")):Long {
        tts.language=locale
        val rate=1.0f; tts.setSpeechRate(rate)
        val words=text.trim().split(Regex("\\s+")).count{it.isNotBlank()}
        return (words*430L).coerceAtLeast(300L)
    }
    fun sync(text:String,startMs:Long,endMs:Long):TtsSyncPlan = TtsSynchronizer.plan(startMs,endMs,estimateDurationMs(text))
    fun shutdown(){tts.shutdown()}
}
