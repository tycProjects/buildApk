package com.ducknovis.discautoquests.data

class Runner(
    private val token: String,
    private val maxParallel: Int = 2,
    private val onEvent: (ProgressEvent) -> Unit
) {
    private val client = DiscordClient(
        token = token,
        onRateLimit = { ms ->
            onEvent(ProgressEvent.Log(level = ProgressEvent.Level.WARN, message = "Rate limit, wait ${ms}ms"))
        },
        onError = { msg ->
            onEvent(ProgressEvent.Log(level = ProgressEvent.Level.ERROR, message = msg))
        }
    )

    private val store = QuestStore(client, maxParallel, onEvent)

    suspend fun init() {
        store.load()
        val all = store.all()
        onEvent(
            ProgressEvent.Log(
                level = ProgressEvent.Level.INFO,
                message = "Loaded ${all.size} quests (pending=${store.pending().size}, claimable=${store.claimable().size})"
            )
        )
        // Claim completed-but-unclaimed first (including Orbs)
        store.claimAll()
    }

    fun quests(): List<Quest> = store.all()

    fun pending(): List<Quest> = store.pending()

    fun claimable(): List<Quest> = store.claimable()

    suspend fun run() {
        store.runPending()
        try {
            val balance = client.getBalance()
            onEvent(ProgressEvent.Balance(balance))
        } catch (_: Exception) {
        }
    }

    suspend fun getBalance(): Int? = try {
        client.getBalance()
    } catch (_: Exception) {
        null
    }
}
