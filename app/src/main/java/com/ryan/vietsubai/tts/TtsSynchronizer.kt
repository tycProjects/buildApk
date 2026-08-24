package com.ryan.vietsubai.tts

import kotlin.math.abs

data class TtsSyncPlan(val targetMs: Long, val sourceMs: Long, val playbackRate: Float, val padMs: Long, val trimmedMs: Long)
object TtsSynchronizer {
    fun plan(startMs: Long, endMs: Long, generatedMs: Long): TtsSyncPlan {
        val target = (endMs - startMs).coerceAtLeast(100)
        if (generatedMs <= 0) return TtsSyncPlan(target, generatedMs, 1f, target, 0)
        val ratio = generatedMs.toFloat() / target
        return when {
            ratio > 1.12f -> TtsSyncPlan(target, generatedMs, ratio.coerceIn(1f, 1.35f), 0, (generatedMs-target).coerceAtLeast(0))
            ratio < 0.88f -> TtsSyncPlan(target, generatedMs, 1f, target-generatedMs, 0)
            else -> TtsSyncPlan(target, generatedMs, 1f, (target-generatedMs).coerceAtLeast(0), 0)
        }
    }
}
