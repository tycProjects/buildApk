package com.ryan.vietsubai.config

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.vietsubai.model.*
import com.ryan.vietsubai.security.SecretStore
import kotlinx.coroutines.flow.first

private val Context.appConfigStore by preferencesDataStore("vietsub_ai_config")

class ConfigStore(private val context: Context) {
    private val secrets = SecretStore(context)
    private fun key(prefix: String, field: String) = stringPreferencesKey("${prefix}_$field")
    suspend fun load(): AppConfig {
        val p = context.appConfigStore.data.first()
        fun provider(id: String, name: String, defaultUrl: String = "", defaultModel: String = "") =
            AiProviderConfig(id, name, p[key(id,"url")] ?: defaultUrl, secrets.get("api_${id}"), p[key(id,"model")] ?: defaultModel, p[key(id,"enabled")]?.toBooleanStrictOrNull() ?: true)
        return AppConfig(
            gemini = provider("gemini", "Gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
            groq = provider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            stt = provider("stt", "Groq STT", "https://api.groq.com/openai/v1", "whisper-large-v3-turbo"),
            tts = provider("tts", "TTS"), ocr = provider("ocr", "OCR"),
            mediaResolverUrl = p[stringPreferencesKey("media_resolver_url")] ?: "",
            targetLanguage = p[stringPreferencesKey("target_language")] ?: "vi",
            voice = p[stringPreferencesKey("voice")] ?: "vi-VN-HoaiMyNeural",
            subtitle = SubtitleSettings(
                source = runCatching { SubtitleSource.valueOf(p[stringPreferencesKey("subtitle_source")] ?: "STT") }.getOrDefault(SubtitleSource.STT),
                sourceLanguage = p[stringPreferencesKey("subtitle_source_language")] ?: "auto",
                targetLanguage = p[stringPreferencesKey("subtitle_target_language")] ?: "vi",
                translationPrompt = p[stringPreferencesKey("subtitle_prompt")] ?: "Dịch tự nhiên, đúng ngữ cảnh, giữ nguyên ý nghĩa và độ dài phù hợp để lồng tiếng."
            ))
    }
    suspend fun save(c: AppConfig) { context.appConfigStore.edit { p ->
        fun put(x: AiProviderConfig) { p[key(x.id,"url")]=x.baseUrl; p[key(x.id,"model")]=x.model; p[key(x.id,"enabled")]=x.enabled.toString(); secrets.put("api_${x.id}", x.apiKey) }
        put(c.gemini); put(c.groq); put(c.stt); put(c.tts); put(c.ocr)
        p[stringPreferencesKey("media_resolver_url")] = c.mediaResolverUrl
        p[stringPreferencesKey("target_language")] = c.targetLanguage; p[stringPreferencesKey("voice")] = c.voice
        p[stringPreferencesKey("subtitle_source")] = c.subtitle.source.name
        p[stringPreferencesKey("subtitle_source_language")] = c.subtitle.sourceLanguage
        p[stringPreferencesKey("subtitle_target_language")] = c.subtitle.targetLanguage
        p[stringPreferencesKey("subtitle_prompt")] = c.subtitle.translationPrompt
    }}
}
