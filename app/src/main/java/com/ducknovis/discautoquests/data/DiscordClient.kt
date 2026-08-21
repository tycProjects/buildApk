package com.ducknovis.discautoquests.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class DiscordClient(
    private val token: String,
    private val onRateLimit: ((Long) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) {
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) discord/1.0.9215 Chrome/138.0.7204.251 Electron/37.6.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun clientPropsJson(): String {
        val props = JSONObject().apply {
            put("os", "Windows")
            put("browser", "Discord Client")
            put("release_channel", "stable")
            put("client_version", "1.0.9215")
            put("os_version", "10.0.19045")
            put("os_arch", "x64")
            put("app_arch", "x64")
            put("system_locale", "en-US")
            put("has_client_mods", false)
            put("client_launch_id", UUID.randomUUID().toString())
            put("browser_user_agent", userAgent)
            put("browser_version", "37.6.0")
            put("os_sdk_version", "19045")
            put("client_build_number", 471091)
            put("native_build_number", 72186)
            put("client_event_source", JSONObject.NULL)
            put("launch_signature", UUID.randomUUID().toString())
            put("client_heartbeat_session_id", UUID.randomUUID().toString())
            put("client_app_state", "focused")
        }
        return Base64.encodeToString(props.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun headers(): Map<String, String> = mapOf(
        "Authorization" to token,
        "User-Agent" to userAgent,
        "X-Discord-Locale" to "en-US",
        "Accept-Language" to "en-US",
        "X-Super-Properties" to clientPropsJson(),
        "X-Discord-Timezone" to (TimeZone.getDefault().id.ifBlank { "Etc/UTC" }),
        "Origin" to "https://discord.com",
        "Referer" to "https://discord.com/channels/@me"
    )

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        retries: Int = 3
    ): String? = withContext(Dispatchers.IO) {
        val url = "https://discord.com/api/v10$path"
        val builder = Request.Builder().url(url)
        headers().forEach { (k, v) -> builder.header(k, v) }

        when (method) {
            "POST" -> builder.post((body?.toString() ?: "{}").toRequestBody(jsonMedia))
            "PATCH" -> builder.patch((body?.toString() ?: "{}").toRequestBody(jsonMedia))
            else -> builder.get()
        }

        val response = client.newCall(builder.build()).execute()
        val code = response.code
        val text = response.body?.string().orEmpty()
        response.close()

        when {
            code == 429 -> {
                val retryAfterMs = try {
                    ((JSONObject(text).optDouble("retry_after", 1.0)) * 1000).toLong()
                } catch (_: Exception) {
                    1000L
                }
                onRateLimit?.invoke(retryAfterMs)
                if (retries > 0) {
                    delay(retryAfterMs)
                    return@withContext request(path, method, body, retries - 1)
                }
                throw Exception("Rate limited")
            }
            code == 204 -> null
            code in 200..299 -> text
            else -> {
                val msg = "HTTP $code: $text"
                onError?.invoke(msg)
                throw Exception(msg)
            }
        }
    }

    suspend fun getQuests(): List<Quest> {
        val raw = request("/quests/@me") ?: return emptyList()
        val root = JSONObject(raw)
        val arr = root.optJSONArray("quests") ?: JSONArray()
        val result = mutableListOf<Quest>()

        for (i in 0 until arr.length()) {
            val q = arr.getJSONObject(i)
            val config = q.optJSONObject("config")
            // task_config or task_config_v2
            val taskConfig = config?.optJSONObject("task_config_v2")?.optJSONObject("tasks")
                ?: config?.optJSONObject("task_config")?.optJSONObject("tasks")
            val userStatus = q.optJSONObject("user_status")

            val preferred = listOf(
                TaskType.WATCH_VIDEO,
                TaskType.WATCH_VIDEO_ON_MOBILE,
                TaskType.PLAY_ON_DESKTOP,
                TaskType.STREAM_ON_DESKTOP,
                TaskType.PLAY_ACTIVITY
            )
            val taskType = preferred.firstOrNull { taskConfig?.has(it.apiValue) == true }

            val taskObj = taskType?.let { taskConfig?.optJSONObject(it.apiValue) }
            val target = taskObj?.optInt("target", 900) ?: 900

            val progress = taskType?.let {
                userStatus?.optJSONObject("progress")
                    ?.optJSONObject(it.apiValue)
                    ?.optInt("value", 0) ?: 0
            } ?: 0

            // application id: config.application.id OR tasks.*.applications[0].id
            var applicationId = config?.optJSONObject("application")?.optString("id")
                ?.takeIf { it.isNotBlank() }
            if (applicationId.isNullOrBlank() && taskObj != null) {
                val apps = taskObj.optJSONArray("applications")
                if (apps != null && apps.length() > 0) {
                    applicationId = apps.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
                }
            }

            val rewards = config?.optJSONObject("rewards_config")?.optJSONArray("rewards")
            val firstReward = rewards?.optJSONObject(0)
            val messages = firstReward?.optJSONObject("messages")
            val questMessages = config?.optJSONObject("messages")
            val rewardType = firstReward?.optInt("type", -1) ?: -1
            val orbQty = firstReward?.optInt("orb_quantity")?.takeIf { it > 0 }

            result += Quest(
                id = q.getString("id"),
                name = questMessages?.optString("quest_name")?.trim()?.ifBlank { null }
                    ?: q.getString("id"),
                expiresAt = config?.optString("expires_at")?.takeIf { it.isNotBlank() },
                taskType = taskType,
                target = target,
                progress = progress,
                applicationId = applicationId,
                enrolledAt = userStatus?.optString("enrolled_at")?.takeIf { it.isNotBlank() },
                claimedAt = userStatus?.optString("claimed_at")?.takeIf { it.isNotBlank() },
                completedAt = userStatus?.optString("completed_at")?.takeIf { it.isNotBlank() },
                reward = QuestReward(
                    type = rewardType,
                    orbQuantity = orbQty,
                    name = messages?.optString("name")
                )
            )
        }
        return result
    }

    /** Proper claim body — Discord requires platform + location */
    suspend fun claimReward(questId: String): String? {
        val body = JSONObject()
            .put("platform", 0)   // CROSS_PLATFORM
            .put("location", 11)  // QUEST_HOME_DESKTOP
        val res = request("/quests/$questId/claim-reward", "POST", body) ?: return null
        val json = JSONObject(res)
        return json.optString("claimed_at").takeIf { it.isNotBlank() }
            ?: java.time.Instant.now().toString()
    }

    suspend fun postVideoProgress(questId: String, timestamp: Double): Boolean {
        val res = request(
            "/quests/$questId/video-progress",
            "POST",
            JSONObject().put("timestamp", timestamp)
        ) ?: return false
        return JSONObject(res).has("completed_at")
    }

    suspend fun enroll(questId: String): String? {
        val res = request(
            "/quests/$questId/enroll",
            "POST",
            JSONObject()
                .put("location", 11)
                .put("is_targeted", false)
                .put("metadata_raw", JSONObject.NULL)
        ) ?: return null
        return JSONObject(res).optString("enrolled_at").takeIf { it.isNotBlank() }
    }

    suspend fun postHeartbeat(
        questId: String,
        applicationId: String?,
        terminal: Boolean = false
    ): Boolean {
        val body = JSONObject()
            .put("application_id", applicationId ?: JSONObject.NULL)
            .put("terminal", terminal)
        val res = request("/quests/$questId/heartbeat", "POST", body) ?: return false
        return JSONObject(res).has("completed_at")
    }

    suspend fun getBalance(): Int {
        val res = request("/users/@me/virtual-currency/balance") ?: return 0
        return JSONObject(res).optInt("balance", 0)
    }
}
