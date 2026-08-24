package com.ryan.vietsubai.editor

import kotlin.math.max

data class TimelineViewport(val pixelsPerSecond: Float = 80f, val scrollMs: Long = 0)
class EditorTimelineController {
    fun split(clip: VideoClip, atMs: Long): Pair<VideoClip, VideoClip>? {
        if (atMs <= clip.startMs || atMs >= clip.endMs) return null
        return clip.copy(id = clip.id * 10 + 1, endMs = atMs) to clip.copy(id = clip.id * 10 + 2, startMs = atMs)
    }
    fun trim(clip: VideoClip, startMs: Long, endMs: Long): VideoClip = clip.copy(startMs = max(clip.startMs, startMs), endMs = max(startMs + 1, endMs.coerceAtMost(clip.endMs)))
}
