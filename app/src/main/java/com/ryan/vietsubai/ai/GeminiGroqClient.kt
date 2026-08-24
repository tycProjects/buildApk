package com.ryan.vietsubai.ai

import android.content.Context
import android.net.Uri
import com.ryan.vietsubai.model.AiProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Direct native Gemini + Groq clients. No Flask/server relay. */
class GeminiClient(private val config: AiProviderConfig) {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val base = (config.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }).trimEnd('/')

    suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        val model = config.model.ifBlank { "gemini-2.5-flash" }
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val req = Request.Builder()
            .url("$base/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", config.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Gemini ${r.code}: ${r.body?.string()}")
            val json = JSONObject(r.body!!.string())
            json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                ?: error("Gemini returned no text")
        }
    }

    suspend fun translate(text: String, targetLanguage: String, style: String = "natural"): String = generateText("Translate the following subtitle to $targetLanguage. Preserve meaning, names and tone. Style: $style. Return only the translated subtitle.\n\n$text")
}

class GroqClient(private val config: AiProviderConfig) {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
    private val base = (config.baseUrl.ifBlank { "https://api.groq.com/openai/v1" }).trimEnd('/')

    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        val model = config.model.ifBlank { "llama-3.3-70b-versatile" }
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val req = Request.Builder().url("$base/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Groq ${r.code}: ${r.body?.string()}")
            JSONObject(r.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    /** Groq exposes OpenAI-compatible audio transcription endpoints. */
    suspend fun transcribe(file: File, language: String? = null): String = withContext(Dispatchers.IO) {
        val model = config.model.ifBlank { "whisper-large-v3-turbo" }
        val media = file.asRequestBody("audio/*".toMediaType())
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, media)
            .addFormDataPart("model", model)
            .apply { if (!language.isNullOrBlank() && language != "auto") addFormDataPart("language", language) }
            .build()
        val req = Request.Builder().url("$base/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${config.apiKey}").post(form).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Groq STT ${r.code}: ${r.body?.string()}")
            JSONObject(r.body!!.string()).optString("text")
        }
    }
}

class AiRouter(private val gemini: GeminiClient, private val groq: GroqClient) {
    suspend fun translate(text: String, target: String): String = gemini.translate(text, target)
    suspend fun fastRewrite(text: String, target: String): String = groq.chat("Rewrite this subtitle naturally in $target, concise for dubbing. Return only the rewritten line.\n$text")
}
