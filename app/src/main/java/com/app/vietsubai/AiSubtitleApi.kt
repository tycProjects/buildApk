package com.app.vietsubai

import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

class AiSubtitleApi(
    private val geminiKey: String,
    private val groqKey: String,
    private val geminiModel: String = "gemini-2.0-flash",
    private val groqModel: String = "whisper-large-v3-turbo"
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun transcribe(audio: File, sourceLanguage: String): MutableList<SubtitleCue> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/flac".toMediaType()))
            .addFormDataPart("model", groqModel)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .apply { if (sourceLanguage != "auto") addFormDataPart("language", sourceLanguage) }
            .build()
        val response = client.newCall(Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions").header("Authorization", "Bearer $groqKey").post(body).build()).execute()
        check(response.isSuccessful) { "Groq STT ${response.code}: ${response.body?.string()}" }
        val root = JsonParser.parseString(response.body!!.string()).asJsonObject
        return root.getAsJsonArray("segments").map { s -> val o=s.asJsonObject; SubtitleCue(o["id"]?.asInt ?: 0, (o["start"].asDouble*1000).toLong(), (o["end"].asDouble*1000).toLong(), o["text"].asString.trim()) }.toMutableList()
    }

    fun translate(cues: List<SubtitleCue>, source: String, target: String): MutableList<SubtitleCue> {
        val prompt = "Translate subtitle text from $source to $target. Keep startMs and endMs unchanged. Return ONLY a JSON array of objects {index,startMs,endMs,text}. Preserve line breaks and do not add commentary. Input: ${gson.toJson(cues)}"
        val text = geminiText(prompt)
        val arr = JsonParser.parseString(text.stripCodeFence()).asJsonArray
        return arr.map { x -> val o=x.asJsonObject; SubtitleCue(o["index"].asInt, o["startMs"].asLong, o["endMs"].asLong, o["text"].asString) }.toMutableList()
    }

    fun ocrFrame(bitmap: Bitmap, timestampMs: Long): SubtitleCue? {
        val stream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        val b64 = Base64.getEncoder().encodeToString(stream.toByteArray())
        val prompt = "Read only the visible video subtitle. Return ONLY JSON {text:string, confidence:number}. If no subtitle exists return {text:\"\",confidence:0}. Do not invent text."
        val text = geminiText(prompt, b64, "image/jpeg").stripCodeFence()
        val o = JsonParser.parseString(text).asJsonObject; val subtitle = o["text"]?.asString.orEmpty()
        return if (subtitle.isBlank()) null else SubtitleCue(0, timestampMs, timestampMs + 3500, subtitle)
    }

    private fun geminiText(prompt: String, base64: String? = null, mime: String? = null): String {
        val parts = mutableListOf<Map<String, Any>>(mapOf("text" to prompt))
        if (base64 != null) parts += mapOf("inline_data" to mapOf("mime_type" to mime!!, "data" to base64))
        val payload = mapOf("contents" to listOf(mapOf("role" to "user", "parts" to parts)), "generationConfig" to mapOf("temperature" to 0.1, "responseMimeType" to "application/json"))
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$geminiKey").post(gson.toJson(payload).toRequestBody("application/json".toMediaType())).build()
        val response = client.newCall(request).execute(); check(response.isSuccessful) { "Gemini ${response.code}: ${response.body?.string()}" }
        val root = JsonParser.parseString(response.body!!.string()).asJsonObject
        return root["candidates"].asJsonArray[0].asJsonObject["content"].asJsonObject["parts"].asJsonArray[0].asJsonObject["text"].asString
    }
    private fun String.stripCodeFence() = replace(Regex("^```(?:json)?\\s*|\\s*```$"), "").trim()
}
