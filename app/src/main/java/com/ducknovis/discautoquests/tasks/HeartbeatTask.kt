package com.ducknovis.discautoquests.tasks

import com.ducknovis.discautoquests.data.DiscordClient
import com.ducknovis.discautoquests.data.ProgressEvent
import com.ducknovis.discautoquests.data.Quest
import kotlinx.coroutines.delay
import java.time.Instant

suspend fun runHeartbeatTask(
    client: DiscordClient,
    quest: Quest,
    emit: (ProgressEvent) -> Unit
) {
    val target = if (quest.target > 0) quest.target else 60 * 10
    var progress = quest.progress
    val appId = quest.applicationId ?: quest.id

    while (progress < target) {
        val terminal = progress + 60 >= target
        val completed = try {
            client.postHeartbeat(quest.id, appId, terminal)
        } catch (e: Exception) {
            emit(ProgressEvent.Log(quest.id, ProgressEvent.Level.ERROR, "Heartbeat failed: ${e.message}"))
            throw e
        }
        progress = minOf(target, progress + 60)
        emit(
            ProgressEvent.Progress(
                questId = quest.id,
                progress = progress,
                remaining = maxOf(0, target - progress)
            )
        )
        if (completed || progress >= target) break
        delay(60_000)
    }
    quest.completedAt = Instant.now().toString()
    quest.progress = progress
}
