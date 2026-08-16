package com.herdr.remote.data.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.model.Session
import com.herdr.remote.data.model.ToolExecution
import com.herdr.remote.data.model.ToolStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class HerdrServerEvent {
    data class Connected(val serverInfo: String) : HerdrServerEvent()
    data class Disconnected(val reason: String) : HerdrServerEvent()
    data class ActiveSessionsReceived(val sessions: List<Session>, val sessionMessages: Map<String, List<Message>> = emptyMap()) : HerdrServerEvent()
    data class SessionHistoryReceived(val sessionId: String, val messages: List<Message>) : HerdrServerEvent()
    data class TabFocused(val tabId: String) : HerdrServerEvent()
    data class TabClosed(val tabId: String) : HerdrServerEvent()
    data class StreamChunk(val sessionId: String, val chunk: String) : HerdrServerEvent()
    data class MessageComplete(val message: Message) : HerdrServerEvent()
    data class ToolStarted(val sessionId: String, val toolExecution: ToolExecution) : HerdrServerEvent()
    data class ToolFinished(val sessionId: String, val toolExecution: ToolExecution) : HerdrServerEvent()
    data class AgentStatusChanged(val sessionId: String, val status: AgentConnectionStatus, val detail: String) : HerdrServerEvent()
    data class Error(val message: String) : HerdrServerEvent()
}

class HerdrWebSocketClient(
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var webSocket: WebSocket? = null

    private val _connectionStatus = MutableStateFlow(AgentConnectionStatus.OFFLINE)
    val connectionStatus: StateFlow<AgentConnectionStatus> = _connectionStatus.asStateFlow()

    private val _events = MutableSharedFlow<HerdrServerEvent>()
    val events: SharedFlow<HerdrServerEvent> = _events.asSharedFlow()

    private var currentServerUrl: String = ""

    fun connect(serverUrl: String) {
        if (webSocket != null && currentServerUrl == serverUrl && _connectionStatus.value == AgentConnectionStatus.ONLINE) {
            return
        }

        currentServerUrl = serverUrl
        _connectionStatus.value = AgentConnectionStatus.CONNECTING

        try {
            val request = Request.Builder()
                .url(serverUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = AgentConnectionStatus.ONLINE
                    scope.launch {
                        _events.emit(HerdrServerEvent.Connected("Connected to Herdr remote node at $serverUrl"))
                        // Handshake and query live active sessions
                        webSocket.send("""{"type":"client_hello","client":"herdr-remote-android","version":"1.0"}""")
                        webSocket.send("""{"type":"get_sessions"}""")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    _connectionStatus.value = AgentConnectionStatus.OFFLINE
                    scope.launch {
                        _events.emit(HerdrServerEvent.Disconnected("Connection closing: $reason"))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionStatus.value = AgentConnectionStatus.OFFLINE
                    scope.launch {
                        _events.emit(HerdrServerEvent.Error(t.localizedMessage ?: "WebSocket failure"))
                    }
                }
            })
        } catch (e: Exception) {
            _connectionStatus.value = AgentConnectionStatus.OFFLINE
            scope.launch {
                _events.emit(HerdrServerEvent.Error("Invalid URL or connection failed: ${e.localizedMessage}"))
            }
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = AgentConnectionStatus.OFFLINE
    }

    fun sendMessage(sessionId: String, prompt: String, attachments: List<Attachment>) {
        val payload = JsonObject().apply {
            addProperty("type", "user_message")
            addProperty("session_id", sessionId)
            addProperty("content", prompt)
            if (attachments.isNotEmpty()) {
                val attArray = gson.toJsonTree(attachments)
                add("attachments", attArray)
            }
        }

        webSocket?.send(payload.toString())
    }

    fun requestActiveSessions() {
        webSocket?.send("""{"type":"get_sessions"}""")
        webSocket?.send("""{"type":"list_sessions"}""")
        webSocket?.send("""{"type":"sync_tabs"}""")
        webSocket?.send("""{"type":"get_tabs"}""")
        webSocket?.send("""{"action":"get_sessions"}""")
    }

    fun selectTab(tabId: String) {
        if (tabId.isBlank()) return
        webSocket?.send("""{"type":"select_tab","tab_id":"$tabId"}""")
    }

    fun createTab(label: String = "") {
        val payload = JsonObject().apply {
            addProperty("type", "create_tab")
            if (label.isNotBlank()) addProperty("label", label)
        }
        webSocket?.send(payload.toString())
    }

    fun closeTab(tabId: String) {
        if (tabId.isBlank()) return
        webSocket?.send("""{"type":"close_tab","tab_id":"$tabId"}""")
    }

    private fun handleIncomingMessage(text: String) {
        scope.launch(Dispatchers.Default) {
            try {
                android.util.Log.d("HerdrWS", "Incoming WS frame: $text")

                // Handle root array
                if (text.trim().startsWith("[")) {
                    val array = gson.fromJson(text, com.google.gson.JsonArray::class.java)
                    val sessions = parseSessionArray(array)
                    if (sessions.isNotEmpty()) {
                        val sessionMessagesMap = extractSessionMessagesMap(array)
                        _events.emit(HerdrServerEvent.ActiveSessionsReceived(sessions, sessionMessagesMap))
                    }
                    return@launch
                }

                val json = gson.fromJson(text, JsonObject::class.java)
                val type = json.get("type")?.asString ?: json.get("event")?.asString ?: json.get("action")?.asString ?: json.get("op")?.asString ?: "message"
                val sessionId = json.get("session_id")?.asString ?: json.get("sessionId")?.asString ?: json.get("tab_id")?.asString ?: json.get("tabId")?.asString ?: ""

                when (type.lowercase()) {
                    "sessions_list", "active_sessions", "sessions", "list_sessions", "tabs", "tabs_list", "tab_list", "sync_tabs", "sync", "snapshot", "state", "init", "hello_ack", "get_sessions", "get_tabs" -> {
                        val array = when {
                            json.has("sessions") && json.get("sessions").isJsonArray -> json.getAsJsonArray("sessions")
                            json.has("tabs") && json.get("tabs").isJsonArray -> json.getAsJsonArray("tabs")
                            json.has("data") && json.get("data").isJsonArray -> json.getAsJsonArray("data")
                            json.has("payload") && json.get("payload").isJsonArray -> json.getAsJsonArray("payload")
                            json.has("items") && json.get("items").isJsonArray -> json.getAsJsonArray("items")
                            json.has("active_sessions") && json.get("active_sessions").isJsonArray -> json.getAsJsonArray("active_sessions")
                            json.has("activeSessions") && json.get("activeSessions").isJsonArray -> json.getAsJsonArray("activeSessions")
                            json.has("open_tabs") && json.get("open_tabs").isJsonArray -> json.getAsJsonArray("open_tabs")
                            json.has("openTabs") && json.get("openTabs").isJsonArray -> json.getAsJsonArray("openTabs")
                            else -> null
                        }

                        val sessionMessagesMap = mutableMapOf<String, List<Message>>()
                        val sessionsList = if (array != null) {
                            sessionMessagesMap.putAll(extractSessionMessagesMap(array))
                            parseSessionArray(array)
                        } else {
                            // Check if dictionary mapping of sessions { "tab-1": {...}, "tab-2": {...} }
                            val list = mutableListOf<Session>()
                            val containerObj = when {
                                json.has("sessions") && json.get("sessions").isJsonObject -> json.getAsJsonObject("sessions")
                                json.has("tabs") && json.get("tabs").isJsonObject -> json.getAsJsonObject("tabs")
                                json.has("data") && json.get("data").isJsonObject -> json.getAsJsonObject("data")
                                else -> null
                            }
                            containerObj?.keySet()?.forEach { key ->
                                val child = containerObj.get(key)
                                if (child.isJsonObject) {
                                    val childObj = child.asJsonObject
                                    parseSingleSession(childObj, defaultId = key)?.let {
                                        list.add(it)
                                        val msgs = extractMessagesFromSessionObject(childObj, it.id)
                                        if (msgs.isNotEmpty()) {
                                            sessionMessagesMap[it.id] = msgs
                                        }
                                    }
                                }
                            }
                            list
                        }

                        if (sessionsList.isNotEmpty()) {
                            _events.emit(HerdrServerEvent.ActiveSessionsReceived(sessionsList, sessionMessagesMap))
                        }
                    }

                    "history", "chat_history", "session_history", "messages", "messages_list", "get_history", "get_messages", "load_session" -> {
                        val messagesArray = when {
                            json.has("messages") && json.get("messages").isJsonArray -> json.getAsJsonArray("messages")
                            json.has("history") && json.get("history").isJsonArray -> json.getAsJsonArray("history")
                            json.has("chat_history") && json.get("chat_history").isJsonArray -> json.getAsJsonArray("chat_history")
                            json.has("data") && json.get("data").isJsonArray -> json.getAsJsonArray("data")
                            json.has("payload") && json.get("payload").isJsonArray -> json.getAsJsonArray("payload")
                            else -> null
                        }
                        if (messagesArray != null && sessionId.isNotBlank()) {
                            val parsedMessages = parseMessagesArray(messagesArray, sessionId)
                            if (parsedMessages.isNotEmpty()) {
                                _events.emit(HerdrServerEvent.SessionHistoryReceived(sessionId, parsedMessages))
                            }
                        }
                    }

                    "tab_opened", "tab_created", "session_created", "session_opened" -> {
                        val sessionObj = when {
                            json.has("session") && json.get("session").isJsonObject -> json.getAsJsonObject("session")
                            json.has("tab") && json.get("tab").isJsonObject -> json.getAsJsonObject("tab")
                            json.has("data") && json.get("data").isJsonObject -> json.getAsJsonObject("data")
                            else -> json
                        }
                        parseSingleSession(sessionObj)?.let { newSession ->
                            val msgs = extractMessagesFromSessionObject(sessionObj, newSession.id)
                            val messagesMap = if (msgs.isNotEmpty()) mapOf(newSession.id to msgs) else emptyMap()
                            _events.emit(HerdrServerEvent.ActiveSessionsReceived(listOf(newSession), messagesMap))
                        }
                    }

                    "tab_focused", "focus_tab", "tab_selected", "active_tab", "select_tab" -> {
                        val targetTab = json.get("tab_id")?.asString ?: json.get("session_id")?.asString ?: sessionId
                        if (targetTab.isNotBlank()) {
                            _events.emit(HerdrServerEvent.TabFocused(targetTab))
                        }
                    }

                    "tab_closed", "close_tab", "delete_tab" -> {
                        val targetTab = json.get("tab_id")?.asString ?: json.get("session_id")?.asString ?: sessionId
                        if (targetTab.isNotBlank()) {
                            _events.emit(HerdrServerEvent.TabClosed(targetTab))
                        }
                    }

                    "stream_chunk", "chunk", "content_chunk" -> {
                        val chunk = json.get("chunk")?.asString ?: json.get("content")?.asString ?: json.get("text")?.asString ?: ""
                        _events.emit(HerdrServerEvent.StreamChunk(sessionId, chunk))
                    }
                    "agent_status", "status" -> {
                        val statusStr = json.get("status")?.asString ?: "ONLINE"
                        val detail = json.get("detail")?.asString ?: json.get("message")?.asString ?: ""
                        val status = when (statusStr.uppercase()) {
                            "THINKING" -> AgentConnectionStatus.THINKING
                            "EXECUTING_TOOL" -> AgentConnectionStatus.EXECUTING_TOOL
                            "STREAMING" -> AgentConnectionStatus.STREAMING
                            "ONLINE" -> AgentConnectionStatus.ONLINE
                            else -> AgentConnectionStatus.ONLINE
                        }
                        _events.emit(HerdrServerEvent.AgentStatusChanged(sessionId, status, detail))
                    }
                    "tool_started", "tool_start" -> {
                        val toolName = json.get("tool_name")?.asString ?: json.get("name")?.asString ?: "tool"
                        val args = json.get("args")?.toString() ?: json.get("arguments")?.toString() ?: "{}"
                        val tool = ToolExecution(
                            toolName = toolName,
                            argumentsJson = args,
                            status = ToolStatus.RUNNING
                        )
                        _events.emit(HerdrServerEvent.ToolStarted(sessionId, tool))
                    }
                    "tool_finished", "tool_finish", "tool_complete" -> {
                        val toolName = json.get("tool_name")?.asString ?: json.get("name")?.asString ?: "tool"
                        val args = json.get("args")?.toString() ?: json.get("arguments")?.toString() ?: "{}"
                        val result = json.get("result")?.toString() ?: json.get("output")?.toString() ?: "{}"
                        val duration = json.get("duration_ms")?.asLong ?: json.get("duration")?.asLong ?: 200L
                        val tool = ToolExecution(
                            toolName = toolName,
                            argumentsJson = args,
                            resultJson = result,
                            status = ToolStatus.SUCCESS,
                            durationMs = duration
                        )
                        _events.emit(HerdrServerEvent.ToolFinished(sessionId, tool))
                    }
                    "message_complete", "message", "chat_message" -> {
                        val msgId = json.get("id")?.asString ?: json.get("message_id")?.asString ?: UUID.randomUUID().toString()
                        val content = json.get("content")?.asString ?: json.get("text")?.asString ?: json.get("response")?.asString ?: ""
                        val thought = json.get("thought")?.asString ?: json.get("reasoning")?.asString
                        val message = Message(
                            id = msgId,
                            sessionId = sessionId,
                            sender = MessageSender.AGENT,
                            content = content,
                            thought = thought,
                            status = MessageStatus.SENT
                        )
                        _events.emit(HerdrServerEvent.MessageComplete(message))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HerdrWS", "Error handling frame: ${e.localizedMessage}")
            }
        }
    }

    private fun parseSessionArray(array: com.google.gson.JsonArray): List<Session> {
        val list = mutableListOf<Session>()
        for (item in array) {
            if (item.isJsonObject) {
                parseSingleSession(item.asJsonObject)?.let { list.add(it) }
            }
        }
        return list
    }

    private fun parseSingleSession(obj: JsonObject, defaultId: String? = null): Session? {
        val id = obj.get("id")?.asString
            ?: obj.get("session_id")?.asString
            ?: obj.get("sessionId")?.asString
            ?: obj.get("tab_id")?.asString
            ?: obj.get("tabId")?.asString
            ?: defaultId
            ?: UUID.randomUUID().toString()

        val title = obj.get("title")?.asString
            ?: obj.get("name")?.asString
            ?: obj.get("label")?.asString
            ?: obj.get("tab_name")?.asString
            ?: obj.get("tabName")?.asString
            ?: obj.get("agent_name")?.asString
            ?: "Remote Agent"

        val role = obj.get("role")?.asString ?: "Autonomous Agent"
        val model = obj.get("model")?.asString
        val statusStr = obj.get("status")?.asString ?: "ONLINE"

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
            avatarEmoji = when {
                title.contains("grok", true) || role.contains("grok", true) -> "⚡"
                title.contains("claude", true) || role.contains("claude", true) -> "🧠"
                title.contains("gemini", true) || role.contains("gemini", true) -> "✨"
                title.contains("zsh", true) || title.contains("bash", true) || title.contains("sh", true) -> "💻"
                title.contains("code", true) || role.contains("engineer", true) -> "🛠️"
                title.contains("research", true) || role.contains("research", true) -> "🔬"
                title.contains("review", true) -> "🎯"
                else -> "🤖"
            },
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

    private fun extractSessionMessagesMap(array: com.google.gson.JsonArray): Map<String, List<Message>> {
        val map = mutableMapOf<String, List<Message>>()
        for (item in array) {
            if (item.isJsonObject) {
                val obj = item.asJsonObject
                val id = obj.get("id")?.asString
                    ?: obj.get("session_id")?.asString
                    ?: obj.get("sessionId")?.asString
                    ?: obj.get("tab_id")?.asString
                    ?: obj.get("tabId")?.asString
                    ?: continue
                val msgs = extractMessagesFromSessionObject(obj, id)
                if (msgs.isNotEmpty()) {
                    map[id] = msgs
                }
            }
        }
        return map
    }

    private fun extractMessagesFromSessionObject(obj: JsonObject, sessionId: String): List<Message> {
        val messagesArray = when {
            obj.has("messages") && obj.get("messages").isJsonArray -> obj.getAsJsonArray("messages")
            obj.has("history") && obj.get("history").isJsonArray -> obj.getAsJsonArray("history")
            obj.has("chat_history") && obj.get("chat_history").isJsonArray -> obj.getAsJsonArray("chat_history")
            obj.has("conversation") && obj.get("conversation").isJsonArray -> obj.getAsJsonArray("conversation")
            else -> null
        } ?: return emptyList()

        return parseMessagesArray(messagesArray, sessionId)
    }

    private fun parseMessagesArray(array: com.google.gson.JsonArray, defaultSessionId: String): List<Message> {
        val list = mutableListOf<Message>()
        for (item in array) {
            if (item.isJsonObject) {
                parseMessageObject(item.asJsonObject, defaultSessionId)?.let { list.add(it) }
            }
        }
        return list
    }

    private fun parseMessageObject(obj: JsonObject, defaultSessionId: String): Message? {
        val id = obj.get("id")?.asString ?: obj.get("message_id")?.asString ?: UUID.randomUUID().toString()
        val sId = obj.get("session_id")?.asString ?: obj.get("sessionId")?.asString ?: defaultSessionId
        val senderStr = obj.get("sender")?.asString ?: obj.get("role")?.asString ?: "agent"
        val sender = when (senderStr.lowercase()) {
            "user", "human" -> MessageSender.USER
            "system" -> MessageSender.SYSTEM
            else -> MessageSender.AGENT
        }
        val content = obj.get("content")?.asString ?: obj.get("text")?.asString ?: obj.get("response")?.asString ?: return null
        val thought = obj.get("thought")?.asString ?: obj.get("reasoning")?.asString
        val timestamp = obj.get("timestamp")?.asLong ?: obj.get("created_at")?.asLong ?: System.currentTimeMillis()

        return Message(
            id = id,
            sessionId = sId,
            sender = sender,
            content = content,
            timestamp = timestamp,
            status = MessageStatus.SENT,
            thought = thought
        )
    }
}

