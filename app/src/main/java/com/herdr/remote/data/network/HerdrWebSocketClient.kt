package com.herdr.remote.data.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
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
import java.util.concurrent.TimeUnit

sealed class HerdrServerEvent {
    data class Connected(val serverInfo: String) : HerdrServerEvent()
    data class Disconnected(val reason: String) : HerdrServerEvent()
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

    private fun handleIncomingMessage(text: String) {
        scope.launch(Dispatchers.Default) {
            try {
                val json = gson.fromJson(text, JsonObject::class.java)
                val type = json.get("type")?.asString ?: "message"
                val sessionId = json.get("session_id")?.asString ?: ""

                when (type) {
                    "stream_chunk" -> {
                        val chunk = json.get("chunk")?.asString ?: ""
                        _events.emit(HerdrServerEvent.StreamChunk(sessionId, chunk))
                    }
                    "agent_status" -> {
                        val statusStr = json.get("status")?.asString ?: "ONLINE"
                        val detail = json.get("detail")?.asString ?: ""
                        val status = when (statusStr.uppercase()) {
                            "THINKING" -> AgentConnectionStatus.THINKING
                            "EXECUTING_TOOL" -> AgentConnectionStatus.EXECUTING_TOOL
                            "STREAMING" -> AgentConnectionStatus.STREAMING
                            "ONLINE" -> AgentConnectionStatus.ONLINE
                            else -> AgentConnectionStatus.ONLINE
                        }
                        _events.emit(HerdrServerEvent.AgentStatusChanged(sessionId, status, detail))
                    }
                    "tool_started" -> {
                        val toolName = json.get("tool_name")?.asString ?: "tool"
                        val args = json.get("args")?.toString() ?: "{}"
                        val tool = ToolExecution(
                            toolName = toolName,
                            argumentsJson = args,
                            status = ToolStatus.RUNNING
                        )
                        _events.emit(HerdrServerEvent.ToolStarted(sessionId, tool))
                    }
                    "tool_finished" -> {
                        val toolName = json.get("tool_name")?.asString ?: "tool"
                        val args = json.get("args")?.toString() ?: "{}"
                        val result = json.get("result")?.toString() ?: "{}"
                        val duration = json.get("duration_ms")?.asLong ?: 200L
                        val tool = ToolExecution(
                            toolName = toolName,
                            argumentsJson = args,
                            resultJson = result,
                            status = ToolStatus.SUCCESS,
                            durationMs = duration
                        )
                        _events.emit(HerdrServerEvent.ToolFinished(sessionId, tool))
                    }
                    "message_complete" -> {
                        val content = json.get("content")?.asString ?: ""
                        val thought = json.get("thought")?.asString
                        val message = Message(
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
                // Ignore malformed raw frames
            }
        }
    }
}
