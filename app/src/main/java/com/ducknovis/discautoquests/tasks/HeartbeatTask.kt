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
    val target = if (quest.target > 0) quest.target else 60 * 15
    var progress = quest.progress
    val appId = quest.applicationId

    emit(
        ProgressEvent.Log(
            quest.id,
            ProgressEvent.Level.INFO,
            "Heartbeat ${quest.taskType?.apiValue} target=${target}s app=${appId ?: "?"}"
        )
    )

    var stagnant = 0
    while (progress < target) {
        val terminal = progress + 60 >= target
        val result = try {
            client.postHeartbeat(quest.id, appId, quest.taskType, terminal)
        } catch (e: Exception) {
            emit(ProgressEvent.Log(quest.id, ProgressEvent.Level.ERROR, "Heartbeat failed: ${e.message}"))
            throw e
        }

        val newProgress = when {
            result.progress > progress -> result.progress
            else -> minOf(target, progress + 60)
        }
        if (newProgress <= progress) stagnant++ else stagnant = 0
        progress = minOf(target, newProgress)

        emit(
            ProgressEvent.Progress(
                questId = quest.id,
                progress = progress,
                remaining = maxOf(0, target - progress)
            )
        )
        emit(
            ProgressEvent.Log(
                quest.id,
                ProgressEvent.Level.INFO,
                "Progress $progress/$target (${quest.name})"
            )
        )

        if (result.completed || progress >= target) break
        if (stagnant >= 5) {
            emit(
                ProgressEvent.Log(
                    quest.id,
                    ProgressEvent.Level.WARN,
                    "Progress stuck — stop heartbeat"
                )
            )
            break
        }
        delay(20_000) // 20s between heartbeats (closer to real client)
    }
    quest.completedAt = Instant.now().toString()
    quest.progress = progress
}
