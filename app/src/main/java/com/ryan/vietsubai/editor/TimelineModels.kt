package com.ryan.vietsubai.editor

data class VideoClip(val id: Long, val startMs: Long, val endMs: Long, val source: String)
data class TextClip(val id: Long, val startMs: Long, val endMs: Long, val text: String)
data class AudioClip(val id: Long, val startMs: Long, val endMs: Long, val source: String, val volume: Float = 1f)
data class EditorTimeline(val durationMs: Long = 60_000, val clips: List<VideoClip> = emptyList(), val texts: List<TextClip> = emptyList(), val audios: List<AudioClip> = emptyList())
