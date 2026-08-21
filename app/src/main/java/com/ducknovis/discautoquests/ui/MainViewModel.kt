package com.ducknovis.discautoquests.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ducknovis.discautoquests.data.ProgressEvent
import com.ducknovis.discautoquests.data.QuestForegroundService
import com.ducknovis.discautoquests.data.QuestStatus
import com.ducknovis.discautoquests.data.Runner
import com.ducknovis.discautoquests.data.SecureTokenStore
import com.ducknovis.discautoquests.data.Session
import com.ducknovis.discautoquests.data.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class QuestUiItem(
    val id: String,
    val name: String,
    val status: QuestStatus,
    val reward: String,
    val remaining: Int
)

data class UiState(
    val tokenInput: String = "",
    val tokenLocked: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val activeSessionId: String? = null,
    val questsBySession: Map<String, List<QuestUiItem>> = emptyMap(),
    val logsBySession: Map<String, List<String>> = emptyMap(),
    val runningSessionId: String? = null,
    val orbs: Int? = null,
    val showWarning: Boolean = true,
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = SecureTokenStore(application)
    private val appContext = application.applicationContext

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var runJob: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        val saved = tokenStore.getToken().orEmpty()
        val id = UUID.randomUUID().toString()
        val session = Session(id = id, token = saved, status = SessionStatus.IDLE)
        _state.update {
            it.copy(
                tokenInput = saved,
                sessions = listOf(session),
                activeSessionId = id
            )
        }
    }

    fun dismissWarning() {
        _state.update { it.copy(showWarning = false) }
    }

    fun onTokenChange(value: String) {
        if (_state.value.tokenLocked) return
        _state.update { it.copy(tokenInput = value) }
    }

    fun addSession() {
        val token = _state.value.tokenInput.trim()
        val id = UUID.randomUUID().toString()
        val session = Session(id = id, token = token, status = SessionStatus.IDLE)
        _state.update {
            it.copy(
                sessions = it.sessions + session,
                activeSessionId = it.activeSessionId ?: id
            )
        }
        appendLog(id, "Added session ${if (token.isNotEmpty()) token.take(6) else "blank"}")
    }

    fun selectSession(id: String) {
        val session = _state.value.sessions.find { it.id == id } ?: return
        _state.update {
            it.copy(
                activeSessionId = id,
                tokenLocked = false,
                tokenInput = session.token,
                orbs = session.orbs
            )
        }
    }

    fun toggleStartStop() {
        val s = _state.value
        var sessions = s.sessions
        var activeId = s.activeSessionId
        var token = s.tokenInput.trim()

        if (sessions.isEmpty() && token.isNotEmpty()) {
            val id = UUID.randomUUID().toString()
            val newSession = Session(id = id, token = token, status = SessionStatus.IDLE)
            sessions = listOf(newSession)
            activeId = id
            _state.update {
                it.copy(sessions = sessions, activeSessionId = id)
            }
            token = newSession.token
        }

        val currentId = activeId ?: return
        val current = sessions.find { it.id == currentId } ?: return

        val nextToken = token.ifEmpty { current.token }
        if (nextToken != current.token) {
            _state.update {
                it.copy(sessions = it.sessions.map { x ->
                    if (x.id == currentId) x.copy(token = nextToken) else x
                })
            }
        }

        if (s.runningSessionId == currentId) {
            stopRunning(currentId)
            return
        }

        if (current.status == SessionStatus.RUNNING) return
        if (nextToken.isBlank()) {
            appendLog(currentId, "Token trống")
            return
        }

        tokenStore.saveToken(nextToken)
        startSession(Session(id = currentId, token = nextToken, status = SessionStatus.IDLE))
    }

    private fun startSession(session: Session) {
        _state.update {
            it.copy(
                runningSessionId = session.id,
                tokenLocked = true,
                isLoading = true,
                sessions = it.sessions.map { x ->
                    if (x.id == session.id) x.copy(status = SessionStatus.RUNNING, token = session.token) else x
                }
            )
        }
        appendLog(session.id, "Start session ${session.id.take(6)}...")

        // Keep a foreground service so Android does not kill the process while running
        try {
            QuestForegroundService.start(appContext, session.token)
        } catch (e: Exception) {
            appendLog(session.id, "Warn: cannot start FGS: ${e.message}")
        }

        runJob?.cancel()
        runJob = viewModelScope.launch {
            val runner = Runner(session.token) { event -> handleEvent(session.id, event) }
            try {
                runner.init()
                fun mapQuest(q: com.ducknovis.discautoquests.data.Quest): QuestUiItem {
                    val status = when {
                        !q.claimedAt.isNullOrBlank() -> QuestStatus.CLAIMED
                        !q.completedAt.isNullOrBlank() -> QuestStatus.DONE
                        else -> QuestStatus.PENDING
                    }
                    val reward = when {
                        q.reward.isOrbs -> "${q.reward.orbQuantity ?: "?"} Orbs"
                        else -> q.reward.name ?: "Reward"
                    }
                    return QuestUiItem(
                        id = q.id,
                        name = q.name,
                        status = status,
                        reward = reward,
                        remaining = maxOf(0, q.target - q.progress)
                    )
                }
                // Ưu tiên hiện quest còn làm được
                val visible = (runner.runnable() + runner.pending() + runner.claimable() + runner.quests())
                    .distinctBy { it.id }
                    .sortedWith(
                        compareBy<com.ducknovis.discautoquests.data.Quest> {
                            when {
                                it.claimedAt.isNullOrBlank() && it.completedAt.isNullOrBlank() && it.taskType != null -> 0
                                it.claimedAt.isNullOrBlank() && it.completedAt.isNullOrBlank() -> 1
                                it.claimedAt.isNullOrBlank() -> 2
                                else -> 3
                            }
                        }.thenByDescending { it.reward.isOrbs }
                    )
                val qs = visible.map { mapQuest(it) }
                _state.update {
                    it.copy(questsBySession = it.questsBySession + (session.id to qs))
                }
                appendLog(
                    session.id,
                    "pending=${runner.pending().size} runnable=${runner.runnable().size} claimable=${runner.claimable().size}"
                )
                runner.run()
                // Refresh sau khi chạy
                val after = runner.quests()
                    .sortedWith(
                        compareBy<com.ducknovis.discautoquests.data.Quest> {
                            when {
                                it.claimedAt.isNullOrBlank() && it.completedAt.isNullOrBlank() -> 0
                                it.claimedAt.isNullOrBlank() -> 1
                                else -> 2
                            }
                        }
                    )
                    .map { mapQuest(it) }
                _state.update {
                    it.copy(questsBySession = it.questsBySession + (session.id to after))
                }
                val bal = runner.getBalance()
                _state.update {
                    it.copy(
                        sessions = it.sessions.map { x ->
                            if (x.id == session.id) x.copy(status = SessionStatus.DONE, orbs = bal) else x
                        },
                        orbs = bal
                    )
                }
                appendLog(session.id, "Session ${session.id.take(6)} done.")
            } catch (e: Exception) {
                val stopped = e.message == "Stopped" || e is kotlinx.coroutines.CancellationException
                appendLog(
                    session.id,
                    "Session ${session.id.take(6)} ${if (stopped) "stopped" else "error"}: ${e.message}"
                )
                _state.update {
                    it.copy(
                        sessions = it.sessions.map { x ->
                            if (x.id == session.id)
                                x.copy(status = if (stopped) SessionStatus.IDLE else SessionStatus.ERROR)
                            else x
                        }
                    )
                }
            } finally {
                try {
                    QuestForegroundService.stop(appContext)
                } catch (_: Exception) {
                }
                _state.update {
                    it.copy(runningSessionId = null, tokenLocked = false, isLoading = false)
                }
            }
        }
    }

    private fun stopRunning(sessionId: String) {
        runJob?.cancel()
        runJob = null
        try {
            QuestForegroundService.stop(appContext)
        } catch (_: Exception) {
        }
        _state.update {
            it.copy(
                runningSessionId = null,
                tokenLocked = false,
                isLoading = false,
                sessions = it.sessions.map { x ->
                    if (x.id == sessionId) x.copy(status = SessionStatus.IDLE) else x
                },
                questsBySession = it.questsBySession.mapValues { (id, list) ->
                    if (id == sessionId) list.map { q -> q.copy(status = QuestStatus.PENDING) } else list
                }
            )
        }
        appendLog(sessionId, "Stopped")
    }

    private fun handleEvent(sessionId: String, event: ProgressEvent) {
        when (event) {
            is ProgressEvent.Log -> appendLog(sessionId, "[${event.level}] ${event.message}")
            is ProgressEvent.Progress -> {
                _state.update {
                    val list = it.questsBySession[sessionId].orEmpty()
                    it.copy(
                        questsBySession = it.questsBySession + (sessionId to list.map { q ->
                            if (q.id == event.questId) q.copy(remaining = event.remaining) else q
                        })
                    )
                }
            }
            is ProgressEvent.Status -> {
                _state.update {
                    val list = it.questsBySession[sessionId].orEmpty()
                    it.copy(
                        questsBySession = it.questsBySession + (sessionId to list.map { q ->
                            if (q.id == event.questId) q.copy(status = event.status) else q
                        })
                    )
                }
            }
            is ProgressEvent.Balance -> {
                appendLog(sessionId, "Balance: ${event.orbs}")
                _state.update {
                    it.copy(
                        orbs = event.orbs,
                        sessions = it.sessions.map { x ->
                            if (x.id == sessionId) x.copy(orbs = event.orbs) else x
                        }
                    )
                }
            }
        }
    }

    private fun appendLog(sessionId: String, msg: String) {
        val line = "${timeFmt.format(Date())} $msg"
        _state.update {
            val existing = it.logsBySession[sessionId].orEmpty()
            val next = (listOf(line) + existing).take(200)
            it.copy(logsBySession = it.logsBySession + (sessionId to next))
        }
    }

    override fun onCleared() {
        runJob?.cancel()
        try {
            QuestForegroundService.stop(appContext)
        } catch (_: Exception) {
        }
        super.onCleared()
    }
}
