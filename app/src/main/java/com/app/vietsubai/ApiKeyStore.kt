package com.app.vietsubai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(context: Context) {
    companion object { private const val FILE = "secure_api_keys"; private const val GEMINI = "gemini_key"; private const val GROQ = "groq_key" }
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun gemini(): String = prefs.getString(GEMINI, "").orEmpty()
    fun groq(): String = prefs.getString(GROQ, "").orEmpty()
    fun save(gemini: String, groq: String) { prefs.edit().putString(GEMINI, gemini.trim()).putString(GROQ, groq.trim()).apply() }
    fun clear() { prefs.edit().clear().apply() }
    fun isConfigured(): Boolean = gemini().isNotBlank() && groq().isNotBlank()
}
