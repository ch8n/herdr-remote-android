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
        val defaultProfile = AgentProfile.PRESET_PROFILES[0]
        val initialSession = Session(
            id = "default_cluster",
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

✨ **Capabilities & Controls**:
- **Multi-Session Tabs**: Tap `+ New Tab` above to spawn and switch specialized sub-agents.
- **Sync Desktop Tabs**: Tap `🔄` in the tab bar or in the agent header to pull live desktop workspace tabs.
- **Voice & AI Rephrase**: Use the mic button to speak naturally. Verbal fillers (*um*, *uh*) are cleaned via OpenRouter AI.
- **Tools & Approvals**: Approve or reject elevated bash/file executions with 1 tap.

How can I assist your workflow today?
            """.trimIndent(),
            status = MessageStatus.SENT,
            thought = "Autonomous agent system initialized. Subsystems online: Multi-session tabs, WebSocket sync, Speech Synthesizer."
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
            ensureSessionMessages(sessionId)
        }
    }

    fun ensureSessionMessages(sessionId: String) {
        val currentMap = _messagesMap.value.toMutableMap()
        val currentMsgs = currentMap[sessionId]
        if (currentMsgs.isNullOrEmpty()) {
            val session = _sessions.value.find { it.id == sessionId }
            if (session != null) {
                val profile = session.agentProfile
                val intro = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    sender = MessageSender.AGENT,
                    content = "⚡ **${profile.name}** (${session.title}) is online.\nRole: *${profile.role}*\nReady for commands.",
                    status = MessageStatus.SENT
                )
                currentMap[sessionId] = listOf(intro)
                _messagesMap.value = currentMap
            }
        }
    }

    fun restoreAllSessionsChat() {
        val currentMap = _messagesMap.value.toMutableMap()
        _sessions.value.forEach { session ->
            if (currentMap[session.id].isNullOrEmpty()) {
                val profile = session.agentProfile
                val intro = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = session.id,
                    sender = MessageSender.AGENT,
                    content = "⚡ **${profile.name}** (${session.title}) is online.\nRole: *${profile.role}*\nReady for commands.",
                    status = MessageStatus.SENT
                )
                currentMap[session.id] = listOf(intro)
            }
        }
        _messagesMap.value = currentMap
    }

    fun syncSessionHistory(sessionId: String, messages: List<Message>) {
        if (messages.isEmpty()) return
        val currentMap = _messagesMap.value.toMutableMap()
        currentMap[sessionId] = messages
        _messagesMap.value = currentMap
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
            val nextId = updatedList.last().id
            _activeSessionId.value = nextId
            ensureSessionMessages(nextId)
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

    fun updateAllSessionsStatus(status: AgentConnectionStatus, detail: String) {
        _sessions.value = _sessions.value.map { session ->
            session.copy(status = status, statusDetail = detail)
        }
    }

    fun addMessage(message: Message) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = currentMap[message.sessionId] ?: emptyList()

        if (message.sender == MessageSender.USER) {
            // User message -> always spawn a new user chat bubble
            currentMap[message.sessionId] = list + message
        } else {
            // Agent response / Terminal streaming:
            val lastMsg = list.lastOrNull()
            if (lastMsg != null && lastMsg.sender == MessageSender.AGENT) {
                // If current active bubble in turn is already AGENT -> update in place
                val updated = list.toMutableList()
                updated[updated.size - 1] = message.copy(id = lastMsg.id)
                currentMap[message.sessionId] = updated
            } else {
                // If last message was USER (or thread is empty) -> spawn a NEW agent bubble for this turn
                val newAgentMsg = message.copy(id = UUID.randomUUID().toString())
                currentMap[message.sessionId] = list + newAgentMsg
            }
        }
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
        ensureSessionMessages(sessionId)
    }

    /**
     * Merge or sync active sessions received from remote Herdr cluster.
     */
    fun syncRemoteSessions(
        remoteSessions: List<Session>,
        sessionMessagesMap: Map<String, List<Message>> = emptyMap()
    ) {
        if (remoteSessions.isEmpty()) return

        val currentList = _sessions.value
        val newSessionsList = mutableListOf<Session>()
        val currentMessages = _messagesMap.value.toMutableMap()

        // Update existing sessions or add new remote ones
        remoteSessions.forEach { remote ->
            val existing = currentList.find { it.id == remote.id }
            if (existing != null) {
                newSessionsList.add(
                    existing.copy(
                        title = remote.title.ifBlank { existing.title },
                        status = remote.status,
                        statusDetail = remote.statusDetail,
                        modelOverride = remote.modelOverride ?: existing.modelOverride
                    )
                )
            } else {
                newSessionsList.add(remote)
            }

            // Sync or populate messages
            val remoteMsgs = sessionMessagesMap[remote.id]
            if (!remoteMsgs.isNullOrEmpty()) {
                currentMessages[remote.id] = remoteMsgs
            } else if (currentMessages[remote.id].isNullOrEmpty()) {
                currentMessages[remote.id] = listOf(
                    Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = remote.id,
                        sender = MessageSender.AGENT,
                        content = "⚡ Synced active desktop session **${remote.title}** from Herdr node.\nAgent role: *${remote.agentProfile.role}*\nReady to receive your commands.",
                        status = MessageStatus.SENT
                    )
                )
            }
        }

        // Keep active local sessions that were not in remote list
        val localPreserved = currentList.filter { local ->
            remoteSessions.none { it.id == local.id }
        }
        val mergedSessions = (newSessionsList + localPreserved).distinctBy { it.id }

        _sessions.value = mergedSessions
        _messagesMap.value = currentMessages

        // Switch to the first synced remote tab if active session is missing
        if (mergedSessions.none { it.id == _activeSessionId.value }) {
            val firstId = mergedSessions.first().id
            _activeSessionId.value = firstId
            ensureSessionMessages(firstId)
        }
    }
}

