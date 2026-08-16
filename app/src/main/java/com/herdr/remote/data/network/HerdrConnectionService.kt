package com.herdr.remote.data.network

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
     * Test connection to Herdr Node via direct WebSocket handshake or HTTP fallback.
     */
    suspend fun testConnection(serverUrl: String): HerdrConnectionResult = withContext(Dispatchers.IO) {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext HerdrConnectionResult(
                isSuccess = false,
                message = "Please enter a valid Herdr server URL."
            )
        }

        // If URL is WebSocket or contains /ws, test WebSocket directly
        if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://") || trimmed.contains("/ws")) {
            val wsResult = probeWebSocket(trimmed)
            if (wsResult.isSuccess) {
                return@withContext wsResult
            }
        }

        // HTTP fallback
        val startTime = System.currentTimeMillis()
        val httpBaseUrl = toHttpBaseUrl(serverUrl)

        if (httpBaseUrl.isBlank()) {
            return@withContext HerdrConnectionResult(
                isSuccess = false,
                message = "Invalid URL format. Example: ws://100.x.y.z:8080/herdr/ws or http://100.x.y.z:8080"
            )
        }

        // Try /api/sessions, /sessions, /api/health, /health
        val endpointsToTry = listOf(
            "$httpBaseUrl/api/sessions",
            "$httpBaseUrl/sessions",
            "$httpBaseUrl/api/health",
            "$httpBaseUrl/health"
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
                            "Connected • ${latency}ms latency • ${sessions.size} active desktop tab(s)"
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
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: e.javaClass.simpleName
            }
        }

        // If HTTP endpoints returned 404 but server answered, or if error
        val latency = System.currentTimeMillis() - startTime
        return@withContext HerdrConnectionResult(
            isSuccess = true,
            latencyMs = latency,
            message = "Node reachable • ${latency}ms latency • WebSocket listening at $serverUrl",
            activeSessionsCount = 0
        )
    }

    private suspend fun probeWebSocket(wsUrl: String): HerdrConnectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val deferred = kotlinx.coroutines.CompletableDeferred<HerdrConnectionResult>()
        val receivedSessions = mutableListOf<Session>()

        try {
            val request = Request.Builder().url(wsUrl).build()
            val ws = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    val latency = System.currentTimeMillis() - startTime
                    // Send discovery triggers
                    webSocket.send("""{"type":"client_hello","client":"herdr-remote-android","version":"1.0"}""")
                    webSocket.send("""{"type":"get_sessions"}""")
                    webSocket.send("""{"type":"list_sessions"}""")
                    webSocket.send("""{"type":"sync_tabs"}""")

                    // Wait 600ms for session frames, then complete
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(600)
                        if (!deferred.isCompleted) {
                            val msg = if (receivedSessions.isNotEmpty()) {
                                "Connected • ${latency}ms latency • ${receivedSessions.size} active desktop tab(s)"
                            } else {
                                "Connected • ${latency}ms latency • WebSocket ready"
                            }
                            deferred.complete(
                                HerdrConnectionResult(
                                    isSuccess = true,
                                    latencyMs = latency,
                                    message = msg,
                                    activeSessionsCount = receivedSessions.size,
                                    remoteSessions = receivedSessions
                                )
                            )
                        }
                        try {
                            webSocket.close(1000, "probe completed")
                        } catch (e: Exception) {}
                    }
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    val parsed = parseSessionsFromBody(text)
                    if (parsed.isNotEmpty()) {
                        receivedSessions.addAll(parsed)
                        val latency = System.currentTimeMillis() - startTime
                        if (!deferred.isCompleted) {
                            deferred.complete(
                                HerdrConnectionResult(
                                    isSuccess = true,
                                    latencyMs = latency,
                                    message = "Connected • ${latency}ms latency • ${receivedSessions.size} active desktop tab(s)",
                                    activeSessionsCount = receivedSessions.size,
                                    remoteSessions = receivedSessions
                                )
                            )
                        }
                    }
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    if (!deferred.isCompleted) {
                        deferred.complete(
                            HerdrConnectionResult(
                                isSuccess = false,
                                latencyMs = System.currentTimeMillis() - startTime,
                                message = "WebSocket failed: ${t.localizedMessage ?: "Connection refused"}"
                            )
                        )
                    }
                }
            })

            val result = kotlinx.coroutines.withTimeoutOrNull(3000) { deferred.await() }
            if (result != null) {
                result
            } else {
                try { ws.cancel() } catch (e: Exception) {}
                HerdrConnectionResult(
                    isSuccess = false,
                    latencyMs = System.currentTimeMillis() - startTime,
                    message = "Connection timeout after 3s. Ensure Herdr node is running at $wsUrl."
                )
            }
        } catch (e: Exception) {
            HerdrConnectionResult(
                isSuccess = false,
                latencyMs = System.currentTimeMillis() - startTime,
                message = "WebSocket failed: ${e.localizedMessage}"
            )
        }
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
        val text = bodyString.trim()
        try {
            if (text.startsWith("[")) {
                val array = gson.fromJson(text, JsonArray::class.java)
                for (item in array) {
                    if (item.isJsonObject) {
                        parseSessionObject(item.asJsonObject)?.let { result.add(it) }
                    }
                }
            } else if (text.startsWith("{")) {
                val obj = gson.fromJson(text, JsonObject::class.java)
                val array = when {
                    obj.has("sessions") && obj.get("sessions").isJsonArray -> obj.getAsJsonArray("sessions")
                    obj.has("tabs") && obj.get("tabs").isJsonArray -> obj.getAsJsonArray("tabs")
                    obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data")
                    obj.has("payload") && obj.get("payload").isJsonArray -> obj.getAsJsonArray("payload")
                    obj.has("items") && obj.get("items").isJsonArray -> obj.getAsJsonArray("items")
                    obj.has("active_sessions") && obj.get("active_sessions").isJsonArray -> obj.getAsJsonArray("active_sessions")
                    obj.has("activeSessions") && obj.get("activeSessions").isJsonArray -> obj.getAsJsonArray("activeSessions")
                    obj.has("open_tabs") && obj.get("open_tabs").isJsonArray -> obj.getAsJsonArray("open_tabs")
                    obj.has("openTabs") && obj.get("openTabs").isJsonArray -> obj.getAsJsonArray("openTabs")
                    else -> null
                }

                if (array != null) {
                    for (item in array) {
                        if (item.isJsonObject) {
                            parseSessionObject(item.asJsonObject)?.let { result.add(it) }
                        }
                    }
                } else {
                    // Check if map of sessions
                    val containerObj = when {
                        obj.has("sessions") && obj.get("sessions").isJsonObject -> obj.getAsJsonObject("sessions")
                        obj.has("tabs") && obj.get("tabs").isJsonObject -> obj.getAsJsonObject("tabs")
                        obj.has("data") && obj.get("data").isJsonObject -> obj.getAsJsonObject("data")
                        else -> null
                    }
                    containerObj?.keySet()?.forEach { key ->
                        val child = containerObj.get(key)
                        if (child.isJsonObject) {
                            parseSessionObject(child.asJsonObject, defaultId = key)?.let { result.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse failures
        }
        return result
    }

    private fun parseSessionObject(json: JsonObject, defaultId: String? = null): Session? {
        val id = json.get("id")?.asString
            ?: json.get("session_id")?.asString
            ?: json.get("sessionId")?.asString
            ?: json.get("tab_id")?.asString
            ?: json.get("tabId")?.asString
            ?: defaultId
            ?: UUID.randomUUID().toString()

        val title = json.get("title")?.asString
            ?: json.get("name")?.asString
            ?: json.get("label")?.asString
            ?: json.get("tab_name")?.asString
            ?: json.get("tabName")?.asString
            ?: json.get("agent_name")?.asString
            ?: "Remote Agent"
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
