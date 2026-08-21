package com.ducknovis.discautoquests.tasks

import com.ducknovis.discautoquests.data.DiscordClient
import com.ducknovis.discautoquests.data.ProgressEvent
import com.ducknovis.discautoquests.data.Quest
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.random.Random

suspend fun runVideoTask(
    client: DiscordClient,
    quest: Quest,
    emit: (ProgressEvent) -> Unit
) {
    val target = if (quest.target > 0) quest.target else 900
    var progress = quest.progress
    val enrolledMs = quest.enrolledAt?.let {
        try {
            Instant.parse(it).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    } ?: System.currentTimeMillis()

    while (progress < target) {
        val maxAllowed = ((System.currentTimeMillis() - enrolledMs) / 1000).toInt() + 10
        val diff = maxAllowed - progress
        val step = minOf(diff, 7)
        if (step > 0) {
            progress = minOf(target, progress + step)
            try {
                client.postVideoProgress(quest.id, progress + Random.nextDouble())
            } catch (e: Exception) {
                emit(ProgressEvent.Log(quest.id, ProgressEvent.Level.ERROR, "Video progress failed: ${e.message}"))
                throw e
            }
            emit(
                ProgressEvent.Progress(
                    questId = quest.id,
                    progress = progress,
                    remaining = maxOf(0, target - progress)
                )
            )
        }
        if (progress >= target) break
        delay(1000)
    }
    quest.completedAt = Instant.now().toString()
    quest.progress = progress
}
