package com.ryan.vietsubai.ai

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Native replacement for the Flask API layer.
 * API keys are kept locally in Android DataStore/Keystore-backed storage in the next hardening step.
 * The clients below deliberately expose provider-neutral interfaces so the UI never depends on Flask.
 */
interface TranslationProvider {
    suspend fun translate(request: TranslationRequest, apiKey: String): TranslationResponse
}

class OpenAiCompatibleProvider(private val endpoint: String) : TranslationProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(request: TranslationRequest, apiKey: String): TranslationResponse {
        // Provider adapter intentionally isolated here. Add the provider's JSON body/response mapping
        // without changing the editor or processing pipeline.
        throw UnsupportedOperationException("Provider adapter not configured: $endpoint")
    }
}
