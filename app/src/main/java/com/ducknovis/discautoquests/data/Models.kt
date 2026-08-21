package com.ducknovis.discautoquests.data

enum class TaskType(val apiValue: String) {
    WATCH_VIDEO("WATCH_VIDEO"),
    WATCH_VIDEO_ON_MOBILE("WATCH_VIDEO_ON_MOBILE"),
    PLAY_ON_DESKTOP("PLAY_ON_DESKTOP"),
    STREAM_ON_DESKTOP("STREAM_ON_DESKTOP"),
    PLAY_ACTIVITY("PLAY_ACTIVITY");

    companion object {
        fun from(value: String?): TaskType? =
            entries.firstOrNull { it.apiValue == value }
    }
}

enum class QuestStatus {
    PENDING, RUNNING, DONE, CLAIMED, FAILED, SKIPPED
}

enum class SessionStatus {
    IDLE, RUNNING, DONE, ERROR
}

data class QuestReward(
    val type: Int = -1,
    val orbQuantity: Int? = null,
    val name: String? = null
) {
    val isOrbs: Boolean get() = type == 4 || (orbQuantity != null && orbQuantity > 0)
}

data class Quest(
    val id: String,
    val name: String,
    val expiresAt: String? = null,
    val taskType: TaskType? = null,
    val target: Int = 900,
    var progress: Int = 0,
    val applicationId: String? = null,
    var enrolledAt: String? = null,
    var claimedAt: String? = null,
    var completedAt: String? = null,
    val reward: QuestReward = QuestReward(),
    val availableTasks: List<String> = emptyList()
)

data class Session(
    val id: String,
    var token: String,
    var status: SessionStatus = SessionStatus.IDLE,
    var orbs: Int? = null
)

sealed class ProgressEvent {
    data class Progress(val questId: String, val progress: Int, val remaining: Int) : ProgressEvent()
    data class Status(val questId: String, val status: QuestStatus) : ProgressEvent()
    data class Log(val questId: String? = null, val level: Level, val message: String) : ProgressEvent()
    data class Balance(val orbs: Int) : ProgressEvent()

    enum class Level { INFO, WARN, ERROR }
}
