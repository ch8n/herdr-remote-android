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

class SessionRepository(
    private val settingsRepository: SettingsRepository? = null
) {

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String>("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _messagesMap = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<Message>>> = _messagesMap.asStateFlow()

    init {
        _sessions.value = emptyList()
        _activeSessionId.value = settingsRepository?.getLastActiveSessionId() ?: ""
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
        settingsRepository?.saveLastActiveSession(newSession.id, newSession.title)

        val currentMessages = _messagesMap.value.toMutableMap()
        currentMessages[newSession.id] = listOf(introMessage)
        _messagesMap.value = currentMessages

        return newSession
    }

    fun switchSession(sessionId: String) {
        val session = _sessions.value.find { it.id == sessionId }
        if (session != null) {
            _activeSessionId.value = sessionId
            settingsRepository?.saveLastActiveSession(session.id, session.title)
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
            settingsRepository?.saveLastActiveSessionId(nextId)
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
            // User message -> always append new user bubble
            currentMap[message.sessionId] = list + message
        } else {
            // Agent response:
            val existingIndex = list.indexOfFirst { it.id == message.id }
            if (existingIndex >= 0) {
                // If message with same ID exists, update it in place
                val updated = list.toMutableList()
                updated[existingIndex] = message
                currentMap[message.sessionId] = updated
            } else {
                val lastMsg = list.lastOrNull()
                if (lastMsg != null && lastMsg.sender == MessageSender.AGENT) {
                    // Update the current turn's agent bubble in place
                    val updated = list.toMutableList()
                    updated[updated.size - 1] = message.copy(id = lastMsg.id)
                    currentMap[message.sessionId] = updated
                } else {
                    // Last message was USER -> spawn new Agent bubble for this turn
                    currentMap[message.sessionId] = list + message
                }
            }
        }
        _messagesMap.value = currentMap
    }

    fun updateMessage(message: Message) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = currentMap[message.sessionId] ?: emptyList()
        val existingIndex = list.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            val updatedList = list.toMutableList()
            updatedList[existingIndex] = message
            currentMap[message.sessionId] = updatedList
            _messagesMap.value = currentMap
        } else {
            addMessage(message)
        }
    }

    fun updateLiveStreamTurn(sessionId: String, content: String, isComplete: Boolean = false) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = currentMap[sessionId] ?: emptyList()
        val lastMsg = list.lastOrNull()
        val targetStatus = if (isComplete) MessageStatus.SENT else MessageStatus.STREAMING

        if (lastMsg != null && lastMsg.sender == MessageSender.AGENT) {
            // Update current turn's agent bubble in place with live streaming text
            val updatedMsg = lastMsg.copy(
                content = content,
                status = targetStatus
            )
            val updatedList = list.toMutableList()
            updatedList[updatedList.size - 1] = updatedMsg
            currentMap[sessionId] = updatedList
            _messagesMap.value = currentMap
        } else {
            // User message was the last one -> create new agent bubble for this turn
            val newAgentMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                sender = MessageSender.AGENT,
                content = content,
                status = targetStatus
            )
            currentMap[sessionId] = list + newAgentMsg
            _messagesMap.value = currentMap
        }
    }

    fun appendStreamChunkToLastAgentMessage(sessionId: String, chunk: String) {
        val currentMap = _messagesMap.value.toMutableMap()
        val list = currentMap[sessionId] ?: emptyList()
        val lastMsg = list.lastOrNull()

        if (lastMsg != null && lastMsg.sender == MessageSender.AGENT && lastMsg.status == MessageStatus.STREAMING) {
            val updatedMsg = lastMsg.copy(content = lastMsg.content + chunk)
            val updatedList = list.toMutableList()
            updatedList[updatedList.size - 1] = updatedMsg
            currentMap[sessionId] = updatedList
            _messagesMap.value = currentMap
        } else {
            updateLiveStreamTurn(sessionId, chunk, isComplete = false)
        }
    }

    fun addOrCompleteAgentMessage(message: Message) {
        updateLiveStreamTurn(message.sessionId, message.content, isComplete = true)
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

        // Use the live remote sessions as the source of truth
        _sessions.value = newSessionsList
        _messagesMap.value = currentMessages

        // If current active session was closed, switch to first available session
        val currentActive = _activeSessionId.value
        val stillExists = newSessionsList.any { it.id == currentActive }

        if (!stillExists || currentActive.isBlank()) {
            val savedSessionId = settingsRepository?.getLastActiveSessionId() ?: ""
            val matched = newSessionsList.find { it.id == savedSessionId } ?: newSessionsList.firstOrNull()
            if (matched != null) {
                _activeSessionId.value = matched.id
                settingsRepository?.saveLastActiveSession(matched.id, matched.title)
                ensureSessionMessages(matched.id)
            }
        }
    }
}

