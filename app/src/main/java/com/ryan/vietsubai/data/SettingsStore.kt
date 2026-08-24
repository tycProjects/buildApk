package com.ryan.vietsubai.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.vietsubai.model.ProjectSettings
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore("vietsub_ai_settings")

class SettingsStore(private val context: Context) {
    private val target = stringPreferencesKey("target_language")
    private val source = stringPreferencesKey("source_language")
    private val provider = stringPreferencesKey("translation_provider")
    private val voice = stringPreferencesKey("voice")
    private val rate = floatPreferencesKey("tts_rate")

    suspend fun load(): ProjectSettings {
        val p = context.settingsDataStore.data.first()
        return ProjectSettings(
            targetLanguage = p[target] ?: "vi",
            sourceLanguage = p[source] ?: "auto",
            translationProvider = p[provider] ?: "auto",
            voice = p[voice] ?: "vi-VN-HoaiMyNeural",
            ttsRate = p[rate] ?: 1f
        )
    }

    suspend fun save(s: ProjectSettings) {
        context.settingsDataStore.edit {
            it[target] = s.targetLanguage
            it[source] = s.sourceLanguage
            it[provider] = s.translationProvider
            it[voice] = s.voice
            it[rate] = s.ttsRate
        }
    }
}
