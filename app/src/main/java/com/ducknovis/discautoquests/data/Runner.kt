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
                message = "Loaded ${all.size} | pending=${store.pending().size} runnable=${store.runnable().size} chờ claim=${store.claimable().size}"
            )
        )
        // Không auto-claim — chỉ báo nếu có quest đã complete
        store.claimAll()
    }

    fun quests(): List<Quest> = store.all()
    fun pending(): List<Quest> = store.pending()
    fun runnable(): List<Quest> = store.runnable()
    fun claimable(): List<Quest> = store.claimable()

    suspend fun run() {
        store.runPending()
        try {
            onEvent(ProgressEvent.Balance(client.getBalance()))
        } catch (_: Exception) {
        }
    }

    suspend fun getBalance(): Int? = try {
        client.getBalance()
    } catch (_: Exception) {
        null
    }
}
