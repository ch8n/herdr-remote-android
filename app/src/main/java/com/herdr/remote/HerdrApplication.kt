package com.herdr.remote

import android.app.Application
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.model.ToolStatus
import com.herdr.remote.data.network.HerdrAgentSimulator
import com.herdr.remote.data.network.SimulatedStep
import com.herdr.remote.data.repository.SessionRepository
import com.herdr.remote.data.repository.SettingsRepository
import com.herdr.remote.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class HerdrApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var sessionRepository: SessionRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    val agentSimulator = HerdrAgentSimulator()

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        sessionRepository = SessionRepository(settingsRepository)
        NotificationHelper.initNotificationChannel(this)
    }

    fun sendChatMessage(sessionId: String, text: String) {
        if (text.isBlank()) return

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = MessageSender.USER,
            content = text.trim(),
            status = MessageStatus.SENT
        )
        sessionRepository.addMessage(userMessage)

        val session = sessionRepository.sessions.value.find { it.id == sessionId } ?: return

        appScope.launch {
            val pendingAgentMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                sender = MessageSender.AGENT,
                content = "",
                status = MessageStatus.STREAMING
            )
            sessionRepository.addMessage(pendingAgentMessage)

            agentSimulator.generateAgentResponse(
                sessionId = sessionId,
                prompt = userMessage.content,
                attachments = emptyList(),
                agentProfile = session.agentProfile
            ).collect { step ->
                when (step) {
                    is SimulatedStep.StatusUpdate -> {
                        sessionRepository.updateSessionStatus(session.id, step.status, step.detail)
                    }
                    is SimulatedStep.ThoughtUpdate -> {
                        sessionRepository.updateMessage(pendingAgentMessage.copy(thought = step.thought))
                    }
                    is SimulatedStep.ToolStart -> {
                        val currentList = sessionRepository.messagesMap.value[session.id] ?: emptyList()
                        val current = currentList.find { it.id == pendingAgentMessage.id } ?: pendingAgentMessage
                        val tools = current.toolExecutions + step.tool
                        sessionRepository.updateMessage(current.copy(toolExecutions = tools))

                        if (step.tool.requiresPermission || step.tool.status == ToolStatus.REQUIRES_APPROVAL) {
                            NotificationHelper.sendPermissionRequestNotification(
                                this@HerdrApplication,
                                session,
                                step.tool,
                                step.tool.permissionPrompt
                            )
                        }
                    }
                    is SimulatedStep.ToolEnd -> {
                        val currentList = sessionRepository.messagesMap.value[session.id] ?: emptyList()
                        val current = currentList.find { it.id == pendingAgentMessage.id } ?: pendingAgentMessage
                        val tools = current.toolExecutions.map { if (it.id == step.tool.id) step.tool else it }
                        sessionRepository.updateMessage(current.copy(toolExecutions = tools))
                    }
                    is SimulatedStep.StreamToken -> {
                        sessionRepository.appendStreamChunkToLastAgentMessage(session.id, step.token)
                    }
                    is SimulatedStep.Complete -> {
                        sessionRepository.updateMessage(step.message.copy(id = pendingAgentMessage.id))
                        NotificationHelper.sendTaskCompletedNotification(
                            this@HerdrApplication,
                            session,
                            step.message
                        )
                    }
                }
            }
        }
    }

    fun handlePermissionDecision(sessionId: String, toolId: String, approved: Boolean) {
        val currentList = sessionRepository.messagesMap.value[sessionId] ?: emptyList()
        val session = sessionRepository.sessions.value.find { it.id == sessionId }

        for (msg in currentList) {
            val tool = msg.toolExecutions.find { it.id == toolId }
            if (tool != null) {
                val updatedTool = tool.copy(
                    status = if (approved) ToolStatus.SUCCESS else ToolStatus.REJECTED,
                    resultJson = if (approved) "{\"decision\": \"APPROVED_BY_USER\", \"status\": \"executed\"}" else "{\"decision\": \"DENIED_BY_USER\", \"error\": \"User rejected tool execution permission\"}"
                )
                val updatedList = msg.toolExecutions.map { if (it.id == toolId) updatedTool else it }
                sessionRepository.updateMessage(msg.copy(toolExecutions = updatedList))

                // Post system confirmation in chat
                val feedbackMessage = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    sender = MessageSender.SYSTEM,
                    content = if (approved) "✅ Permission **APPROVED** for tool `${tool.toolName}`." else "🚫 Permission **DENIED** for tool `${tool.toolName}`.",
                    status = MessageStatus.SENT
                )
                sessionRepository.addMessage(feedbackMessage)

                if (session != null) {
                    NotificationHelper.sendTaskCompletedNotification(this, session, feedbackMessage)
                }
                break
            }
        }
    }

    companion object {
        lateinit var instance: HerdrApplication
            private set
    }
}
