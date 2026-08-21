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

    /** Chưa claim + chưa complete + chưa hết hạn */
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

    /** Có thể chạy được (có task type hỗ trợ) */
    fun runnable(): List<Quest> =
        pending().filter { it.taskType != null }

    fun claimable(): List<Quest> =
        quests.filter { !it.completedAt.isNullOrBlank() && it.claimedAt.isNullOrBlank() }

    suspend fun claimAll() {
        val list = claimable()
        if (list.isEmpty()) {
            emit(ProgressEvent.Log(level = ProgressEvent.Level.INFO, message = "Không có quest chờ claim"))
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
        try {
            emit(ProgressEvent.Balance(client.getBalance()))
        } catch (_: Exception) {
        }
    }

    suspend fun enrollPending() {
        val toEnroll = runnable().filter { it.enrolledAt.isNullOrBlank() }
            .sortedByDescending { it.reward.isOrbs }
        for (q in toEnroll) {
            try {
                val enrolled = client.enroll(q.id)
                q.enrolledAt = enrolled ?: Instant.now().toString()
                emit(ProgressEvent.Log(q.id, ProgressEvent.Level.INFO, "Enrolled: ${q.name}"))
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
        // 1) Claim completed
        claimAll()

        // 2) Dump debug for each quest
        for (q in quests) {
            val state = when {
                !q.claimedAt.isNullOrBlank() -> "claimed"
                !q.completedAt.isNullOrBlank() -> "completed"
                !q.enrolledAt.isNullOrBlank() -> "enrolled"
                else -> "available"
            }
            emit(
                ProgressEvent.Log(
                    q.id,
                    ProgressEvent.Level.INFO,
                    "[$state] ${q.name} | task=${q.taskType?.apiValue ?: "none"} | tasks=${q.availableTasks} | ${q.progress}/${q.target}"
                )
            )
        }

        val runList = runnable().sortedByDescending { it.reward.isOrbs }
        if (runList.isEmpty()) {
            val unsupported = pending().filter { it.taskType == null }
            if (unsupported.isNotEmpty()) {
                emit(
                    ProgressEvent.Log(
                        level = ProgressEvent.Level.WARN,
                        message = "${unsupported.size} quest không hỗ trợ task type: ${unsupported.map { it.name + "(" + it.availableTasks + ")" }}"
                    )
                )
            } else {
                emit(
                    ProgressEvent.Log(
                        level = ProgressEvent.Level.INFO,
                        message = "Không còn quest để làm (tất cả đã claim/complete hoặc hết hạn)"
                    )
                )
            }
            return@coroutineScope
        }

        emit(
            ProgressEvent.Log(
                level = ProgressEvent.Level.INFO,
                message = "Sẽ chạy ${runList.size} quest: ${runList.map { it.name }}"
            )
        )

        // 3) Enroll
        enrollPending()

        // 4) Run tasks
        val jobs = runList.map { quest ->
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

        // 5) Reload + claim
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
                        "Skip unsupported: ${quest.availableTasks}"
                    )
                )
                emit(ProgressEvent.Status(quest.id, QuestStatus.SKIPPED))
            }
        }
    }
}
