package com.herdr.remote.data.repository

import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.model.Session
import com.herdr.remote.data.model.ToolExecution
import com.herdr.remote.data.model.ToolStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SessionRepository {

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String>("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _messagesMap = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<Message>>> = _messagesMap.asStateFlow()

    init {
        seedInitialSession()
    }

    private fun seedInitialSession() {
        val defaultProfile = AgentProfile.PRESET_PROFILES[0] // Herdr Orchestrator
        val initialSession = Session(
            id = UUID.randomUUID().toString(),
            title = "Herdr Main Cluster",
            agentProfile = defaultProfile,
            status = AgentConnectionStatus.ONLINE,
            statusDetail = "Online • Ready for commands"
        )

        val welcomeMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = initialSession.id,
            sender = MessageSender.AGENT,
            content = """
👋 **Welcome to Herdr Remote Android!**

I am **${defaultProfile.name}**, your autonomous system coordinator.

✨ **Key Capabilities**:
- **Multi-Session Tabs**: Tap `+` above to spawn specialized sub-agents.
- **Voice & AI Rephrase**: Speak naturally via the mic button—verbal fillers like *'um'* and *'uh'* are automatically cleaned via OpenRouter AI.
- **Rich Media**: Send images, PDFs, and code snippets.
- **Agentmon State**: Live tool invocation cards and thought traces.

How can I assist your workflow today?
            """.trimIndent(),
            status = MessageStatus.SENT,
            thought = "Autonomous agent system initialized. Subsystems online: OpenRouter AI, Speech Synthesizer, WebSockets.",
            toolExecutions = listOf(
                ToolExecution(
                    toolName = "herdr_init_environment",
                    argumentsJson = "{\"cluster\": \"main\", \"protocol\": \"v2\"}",
                    resultJson = "{\"status\": \"initialized\", \"agents_ready\": 4}",
                    status = ToolStatus.SUCCESS,
                    durationMs = 340
                )
            )
        )

        _sessions.value = listOf(initialSession)
        _activeSessionId.value = initialSession.id
        _messagesMap.value = mapOf(initialSession.id to listOf(welcomeMessage))
    }

    fun createSession(
        title: String,
        profile: AgentProfile,
        modelOverride: String? = null
    ): Session {
        val newSession = Session(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "${profile.name} Session" },
            agentProfile = profile,
            status = AgentConnectionStatus.ONLINE,
            statusDetail = "Online • Ready",
            modelOverride = modelOverride
        )

        val introMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = newSession.id,
            sender = MessageSender.AGENT,
            content = "⚡ **${profile.name}** is online.\nRole: *${profile.role}*\nReady to execute your instructions.",
            status = MessageStatus.SENT
        )

        _sessions.value = _sessions.value + newSession
        _activeSessionId.value = newSession.id

        val currentMessages = _messagesMap.value.toMutableMap()
        currentMessages[newSession.id] = listOf(introMessage)
        _messagesMap.value = currentMessages

        return newSession
    }

    fun switchSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        }
    }

    fun closeSession(sessionId: String) {
        val currentList = _sessions.value
        if (currentList.size <= 1) {
            // Don't close last session, instead reset it
            return
        }

        val updatedList = currentList.filter { it.id != sessionId }
        _sessions.value = updatedList

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap.remove(sessionId)
        _messagesMap.value = updatedMap

        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = updatedList.last().id
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) session.copy(title = newTitle) else session
        }
    }

    fun updateSessionStatus(sessionId: String, status: AgentConnectionStatus, detail: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) session.copy(status = status, statusDetail = detail) else session
        }
    }

    fun addMessage(message: Message) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = (currentMap[message.sessionId] ?: emptyList()) + message
        currentMap[message.sessionId] = list
        _messagesMap.value = currentMap
    }

    fun updateMessage(message: Message) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = currentMap[message.sessionId] ?: emptyList()
        val updatedList = list.map { if (it.id == message.id) message else it }
        currentMap[message.sessionId] = updatedList
        _messagesMap.value = currentMap
    }

    fun appendStreamChunkToLastAgentMessage(sessionId: String, chunk: String) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = currentMap[sessionId] ?: return
        val lastMsg = list.lastOrNull { it.sender == MessageSender.AGENT && it.status == MessageStatus.STREAMING }

        if (lastMsg != null) {
            val updatedMsg = lastMsg.copy(content = lastMsg.content + chunk)
            val updatedList = list.map { if (it.id == lastMsg.id) updatedMsg else it }
            currentMap[sessionId] = updatedList
            _messagesMap.value = currentMap
        }
    }

    fun clearSessionMessages(sessionId: String) {
        val currentMap = _messagesMap.value.toMutableMap()
        currentMap[sessionId] = emptyList()
        _messagesMap.value = currentMap
    }
}
