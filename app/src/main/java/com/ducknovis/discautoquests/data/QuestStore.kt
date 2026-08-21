package com.ducknovis.discautoquests.data

import com.ducknovis.discautoquests.tasks.runHeartbeatTask
import com.ducknovis.discautoquests.tasks.runVideoTask
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant

class QuestStore(
    private val client: DiscordClient,
    private val maxParallel: Int = 2,
    private val emit: (ProgressEvent) -> Unit
) {
    private var quests: List<Quest> = emptyList()
    private val semaphore = Semaphore(maxParallel)

    suspend fun load(): List<Quest> {
        quests = client.getQuests()
        return quests
    }

    fun all(): List<Quest> = quests

    fun pending(): List<Quest> {
        val now = System.currentTimeMillis()
        return quests.filter { q ->
            q.claimedAt.isNullOrBlank() &&
                q.completedAt.isNullOrBlank() &&
                (q.expiresAt.isNullOrBlank() || try {
                    Instant.parse(q.expiresAt).toEpochMilli() > now
                } catch (_: Exception) {
                    true
                })
        }
    }

    fun claimable(): List<Quest> =
        quests.filter { !it.completedAt.isNullOrBlank() && it.claimedAt.isNullOrBlank() }

    suspend fun claimAll() {
        val list = claimable()
        if (list.isEmpty()) {
            emit(ProgressEvent.Log(level = ProgressEvent.Level.INFO, message = "Không có quest nào chờ claim"))
            return
        }
        emit(ProgressEvent.Log(level = ProgressEvent.Level.INFO, message = "Đang claim ${list.size} quest..."))
        for (q in list) {
            try {
                val claimedAt = client.claimReward(q.id)
                q.claimedAt = claimedAt ?: Instant.now().toString()
                val rewardText = when {
                    q.reward.isOrbs -> "${q.reward.orbQuantity ?: "?"} Orbs"
                    else -> q.reward.name ?: "reward"
                }
                emit(ProgressEvent.Status(q.id, QuestStatus.CLAIMED))
                emit(
                    ProgressEvent.Log(
                        q.id,
                        ProgressEvent.Level.INFO,
                        "Claimed: ${q.name} → $rewardText"
                    )
                )
            } catch (e: Exception) {
                emit(
                    ProgressEvent.Log(
                        q.id,
                        ProgressEvent.Level.ERROR,
                        "Claim failed [${q.name}]: ${e.message}"
                    )
                )
            }
        }
        // Refresh balance after claims
        try {
            val bal = client.getBalance()
            emit(ProgressEvent.Balance(bal))
        } catch (_: Exception) {
        }
    }

    /** Enroll unenrolled quests (prefer Orbs) before running tasks */
    suspend fun enrollPending() {
        val toEnroll = pending().filter { it.enrolledAt.isNullOrBlank() }
            .sortedByDescending { it.reward.isOrbs }
        for (q in toEnroll) {
            try {
                val enrolled = client.enroll(q.id)
                q.enrolledAt = enrolled ?: Instant.now().toString()
                emit(
                    ProgressEvent.Log(
                        q.id,
                        ProgressEvent.Level.INFO,
                        "Enrolled: ${q.name}"
                    )
                )
            } catch (e: Exception) {
                emit(
                    ProgressEvent.Log(
                        q.id,
                        ProgressEvent.Level.ERROR,
                        "Enroll failed [${q.name}]: ${e.message}"
                    )
                )
            }
        }
    }

    suspend fun runPending() = coroutineScope {
        // Claim any already-completed first
        claimAll()

        // Enroll then run
        enrollPending()

        val jobs = pending().map { quest ->
            async {
                semaphore.withPermit {
                    emit(ProgressEvent.Status(quest.id, QuestStatus.RUNNING))
                    try {
                        execute(quest)
                        if (!quest.completedAt.isNullOrBlank()) {
                            emit(ProgressEvent.Status(quest.id, QuestStatus.DONE))
                        }
                    } catch (e: Exception) {
                        emit(
                            ProgressEvent.Log(
                                quest.id,
                                ProgressEvent.Level.ERROR,
                                e.message ?: "Error"
                            )
                        )
                        emit(ProgressEvent.Status(quest.id, QuestStatus.FAILED))
                    }
                }
            }
        }
        jobs.awaitAll()

        // Claim after completion
        // Reload from server so completed_at from API is accurate
        try {
            load()
        } catch (_: Exception) {
        }
        claimAll()
    }

    private suspend fun execute(quest: Quest) {
        if (quest.enrolledAt.isNullOrBlank()) {
            try {
                val enrolled = client.enroll(quest.id)
                quest.enrolledAt = enrolled ?: Instant.now().toString()
            } catch (e: Exception) {
                emit(
                    ProgressEvent.Log(
                        quest.id,
                        ProgressEvent.Level.ERROR,
                        "Enroll failed: ${e.message}"
                    )
                )
            }
        }

        when (quest.taskType) {
            TaskType.WATCH_VIDEO, TaskType.WATCH_VIDEO_ON_MOBILE ->
                runVideoTask(client, quest, emit)

            TaskType.PLAY_ON_DESKTOP, TaskType.STREAM_ON_DESKTOP, TaskType.PLAY_ACTIVITY ->
                runHeartbeatTask(client, quest, emit)

            null -> {
                emit(
                    ProgressEvent.Log(
                        quest.id,
                        ProgressEvent.Level.WARN,
                        "Unsupported task type — skip"
                    )
                )
                emit(ProgressEvent.Status(quest.id, QuestStatus.SKIPPED))
            }
        }
    }
}
