package com.ryan.vietsubai.ai

data class TranslationRequest(
    val segments: List<RemoteSegment>,
    val targetLanguage: String,
    val provider: String = "auto",
    val style: String = "default",
    val glossary: String = ""
)
data class RemoteSegment(val start: Double, val end: Double, val text: String)
data class TranslationResponse(val segments: List<RemoteSegment>)
