package com.ryan.vietsubai.ai

import com.ryan.vietsubai.data.TranslationMemoryDao
import com.ryan.vietsubai.data.TranslationMemoryEntity
import java.security.MessageDigest

class TranslationMemory(private val dao: TranslationMemoryDao) {
    private fun key(text:String, source:String, target:String, prompt:String): String {
        val raw = "$source|$target|$prompt|$text".trim()
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    suspend fun get(text:String, source:String, target:String, prompt:String): String? = dao.get(key(text,source,target,prompt))?.translatedText
    suspend fun put(text:String, translated:String, source:String, target:String, provider:String, prompt:String) {
        dao.put(TranslationMemoryEntity(cacheKey=key(text,source,target,prompt),sourceText=text,translatedText=translated,sourceLanguage=source,targetLanguage=target,provider=provider))
    }
}
