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
        // Initial clean state with default cluster session (empty messages)
        val defaultProfile = AgentProfile.PRESET_PROFILES[0]
        val initialSession = Session(
            id = "default_cluster",
            title = "Herdr Node",
            agentProfile = defaultProfile,
            status = AgentConnectionStatus.OFFLINE,
            statusDetail = "Disconnected • Configure in Settings"
        )
        _sessions.value = listOf(initialSession)
        _activeSessionId.value = initialSession.id
        _messagesMap.value = emptyMap()
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

    fun updateAllSessionsStatus(status: AgentConnectionStatus, detail: String) {
        _sessions.value = _sessions.value.map { session ->
            session.copy(status = status, statusDetail = detail)
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

    /**
     * Merge or sync active sessions received from remote Herdr cluster.
     */
    fun syncRemoteSessions(remoteSessions: List<Session>) {
        if (remoteSessions.isEmpty()) return

        val currentList = _sessions.value
        val newSessionsList = mutableListOf<Session>()

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

                // Add initial session greeting for newly discovered remote tab
                val currentMessages = _messagesMap.value.toMutableMap()
                if (!currentMessages.containsKey(remote.id)) {
                    currentMessages[remote.id] = listOf(
                        Message(
                            id = UUID.randomUUID().toString(),
                            sessionId = remote.id,
                            sender = MessageSender.AGENT,
                            content = "⚡ Synced active desktop session **${remote.title}** from Herdr node.\nAgent role: *${remote.agentProfile.role}*",
                            status = MessageStatus.SENT
                        )
                    )
                    _messagesMap.value = currentMessages
                }
            }
        }

        // Keep active local sessions that were not in remote list, excluding initial placeholder default_cluster
        val localPreserved = currentList.filter { local ->
            local.id != "default_cluster" && remoteSessions.none { it.id == local.id }
        }
        val mergedSessions = (newSessionsList + localPreserved).distinctBy { it.id }

        _sessions.value = mergedSessions

        // Switch to the first synced remote tab if on default_cluster or if active session is missing
        if (_activeSessionId.value == "default_cluster" || mergedSessions.none { it.id == _activeSessionId.value }) {
            _activeSessionId.value = mergedSessions.first().id
        }
    }
}
