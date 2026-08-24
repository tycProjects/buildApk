package com.ryan.vietsubai.ai

import com.ryan.vietsubai.model.SubtitleSegment
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit

class SubtitleAiPipeline(private val gemini: GeminiClient, private val groq: GroqClient, private val memory: TranslationMemory) {
    suspend fun translateSegments(items: List<SubtitleSegment>, source: String, target: String, prompt: String): List<SubtitleSegment> = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(3)
        items.map { item -> async(Dispatchers.IO) {
            semaphore.withPermit {
                val cached = memory.get(item.text, source, target, prompt)
                val translated = cached ?: gemini.translate(item.text, target, prompt).also { memory.put(item.text,it,source,target,"gemini",prompt) }
                item.copy(translation = translated)
            }
        }}.awaitAll()
    }
    suspend fun rewrite(text: String, target: String): String = groq.chat("Rewrite this subtitle naturally in $target, concise for dubbing. Return only the line.\n$text")
}
