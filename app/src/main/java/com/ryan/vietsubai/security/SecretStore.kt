package com.ryan.vietsubai.security

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("vietsub_ai_secrets", Context.MODE_PRIVATE) }
    private val alias = "vietsub_ai_api_keys"
    private val key: SecretKey
        get() {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply { init(256) }.generateKey()
        }

    fun put(name: String, value: String) {
        if (value.isBlank()) { prefs.edit().remove(name).apply(); return }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array(), Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String = runCatching {
        val raw = prefs.getString(name, null) ?: return ""
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(12); buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining()); buffer.get(encrypted)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            .doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrDefault("")
}
