package com.herdr.remote.data.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

data class HerdrConnectionResult(
    val isSuccess: Boolean,
    val latencyMs: Long = 0,
    val message: String = "",
    val activeSessionsCount: Int = 0,
    val remoteSessions: List<Session> = emptyList(),
    val serverVersion: String? = null
)

class HerdrConnectionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Test connection to Herdr Node via HTTP probe or WebSocket health endpoint.
     */
    suspend fun testConnection(serverUrl: String): HerdrConnectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val httpBaseUrl = toHttpBaseUrl(serverUrl)

        if (httpBaseUrl.isBlank()) {
            return@withContext HerdrConnectionResult(
                isSuccess = false,
                message = "Invalid URL format. Example: ws://100.x.y.z:8080/herdr/ws or http://100.x.y.z:8080"
            )
        }

        // Try /api/sessions first, then /api/health, then root
        val endpointsToTry = listOf(
            "$httpBaseUrl/api/sessions",
            "$httpBaseUrl/sessions",
            "$httpBaseUrl/api/health",
            "$httpBaseUrl/health",
            httpBaseUrl
        )

        var lastError = ""
        for (endpoint in endpointsToTry) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HerdrRemoteAndroid/1.0")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: "{}"
                        val sessions = parseSessionsFromBody(bodyString)
                        val version = parseVersionFromBody(bodyString)

                        val msg = if (sessions.isNotEmpty()) {
                            "Connected • ${latency}ms latency • ${sessions.size} active session(s) on cluster"
                        } else {
                            "Connected • ${latency}ms latency • Herdr node ready"
                        }

                        return@withContext HerdrConnectionResult(
                            isSuccess = true,
                            latencyMs = latency,
                            message = msg,
                            activeSessionsCount = sessions.size,
                            remoteSessions = sessions,
                            serverVersion = version
                        )
                    } else if (response.code in 400..499) {
                        // Node is reachable but endpoint not recognized or requires auth
                        val latency = System.currentTimeMillis() - startTime
                        return@withContext HerdrConnectionResult(
                            isSuccess = true,
                            latencyMs = latency,
                            message = "Node reachable (HTTP ${response.code}) • ${latency}ms latency",
                            activeSessionsCount = 0
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: e.javaClass.simpleName
            }
        }

        val latency = System.currentTimeMillis() - startTime
        return@withContext HerdrConnectionResult(
            isSuccess = false,
            latencyMs = latency,
            message = "Connection failed: $lastError. Ensure Herdr node is running and Tailscale VPN is connected."
        )
    }

    /**
     * Fetch active sessions directly from Herdr REST endpoint.
     */
    suspend fun fetchActiveSessions(serverUrl: String): List<Session> = withContext(Dispatchers.IO) {
        val httpBaseUrl = toHttpBaseUrl(serverUrl)
        if (httpBaseUrl.isBlank()) return@withContext emptyList()

        val endpointsToTry = listOf(
            "$httpBaseUrl/api/sessions",
            "$httpBaseUrl/sessions"
        )

        for (endpoint in endpointsToTry) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "HerdrRemoteAndroid/1.0")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: "{}"
                        val list = parseSessionsFromBody(bodyString)
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
        return@withContext emptyList()
    }

    private fun parseSessionsFromBody(bodyString: String): List<Session> {
        val result = mutableListOf<Session>()
        try {
            if (bodyString.startsWith("[")) {
                val array = gson.fromJson(bodyString, JsonArray::class.java)
                for (item in array) {
                    if (item.isJsonObject) {
                        parseSessionObject(item.asJsonObject)?.let { result.add(it) }
                    }
                }
            } else if (bodyString.startsWith("{")) {
                val obj = gson.fromJson(bodyString, JsonObject::class.java)
                if (obj.has("sessions") && obj.get("sessions").isJsonArray) {
                    val array = obj.getAsJsonArray("sessions")
                    for (item in array) {
                        if (item.isJsonObject) {
                            parseSessionObject(item.asJsonObject)?.let { result.add(it) }
                        }
                    }
                } else if (obj.has("active_sessions") && obj.get("active_sessions").isJsonArray) {
                    val array = obj.getAsJsonArray("active_sessions")
                    for (item in array) {
                        if (item.isJsonObject) {
                            parseSessionObject(item.asJsonObject)?.let { result.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse failures
        }
        return result
    }

    private fun parseSessionObject(json: JsonObject): Session? {
        val id = json.get("id")?.asString ?: json.get("session_id")?.asString ?: UUID.randomUUID().toString()
        val title = json.get("title")?.asString ?: json.get("name")?.asString ?: "Remote Agent"
        val role = json.get("role")?.asString ?: "Autonomous Agent"
        val model = json.get("model")?.asString

        val statusStr = json.get("status")?.asString ?: "ONLINE"
        val status = when (statusStr.uppercase()) {
            "THINKING" -> AgentConnectionStatus.THINKING
            "EXECUTING_TOOL" -> AgentConnectionStatus.EXECUTING_TOOL
            "STREAMING" -> AgentConnectionStatus.STREAMING
            "OFFLINE" -> AgentConnectionStatus.OFFLINE
            else -> AgentConnectionStatus.ONLINE
        }

        val profile = AgentProfile(
            id = "remote_${id.take(8)}",
            name = title,
            role = role,
            avatarEmoji = if (title.contains("code", true)) "⚡" else if (title.contains("research", true)) "🔬" else "🤖",
            systemPrompt = "Remote Herdr agent session",
            defaultModel = model ?: "openrouter/auto"
        )

        return Session(
            id = id,
            title = title,
            agentProfile = profile,
            status = status,
            statusDetail = "Remote Herdr Session • $statusStr",
            modelOverride = model
        )
    }

    private fun parseVersionFromBody(bodyString: String): String? {
        return try {
            val obj = gson.fromJson(bodyString, JsonObject::class.java)
            obj.get("version")?.asString ?: obj.get("server_version")?.asString
        } catch (e: Exception) {
            null
        }
    }

    private fun toHttpBaseUrl(wsOrHttpUrl: String): String {
        val trimmed = wsOrHttpUrl.trim()
        if (trimmed.isBlank()) return ""

        return when {
            trimmed.startsWith("ws://") -> "http://" + trimmed.removePrefix("ws://").substringBefore("/herdr/ws").substringBefore("/ws")
            trimmed.startsWith("wss://") -> "https://" + trimmed.removePrefix("wss://").substringBefore("/herdr/ws").substringBefore("/ws")
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed.substringBefore("/herdr/ws").substringBefore("/ws")
            else -> "http://$trimmed".substringBefore("/herdr/ws").substringBefore("/ws")
        }.trimEnd('/')
    }
}
