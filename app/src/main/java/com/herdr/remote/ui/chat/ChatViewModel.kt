package com.herdr.remote.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.AttachmentType
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.model.Session
import com.herdr.remote.data.model.SettingsData
import com.herdr.remote.data.model.ToolExecution
import com.herdr.remote.data.network.HerdrAgentSimulator
import com.herdr.remote.data.network.HerdrServerEvent
import com.herdr.remote.data.network.HerdrWebSocketClient
import com.herdr.remote.data.network.OpenRouterService
import com.herdr.remote.data.network.SimulatedStep
import com.herdr.remote.data.repository.SessionRepository
import com.herdr.remote.data.repository.SettingsRepository
import com.herdr.remote.speech.SpeechRecognizerHelper
import com.herdr.remote.speech.SpeechState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RephraseComparison(
    val originalText: String,
    val rephrasedText: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = com.herdr.remote.HerdrApplication.instance.settingsRepository
    private val sessionRepository = com.herdr.remote.HerdrApplication.instance.sessionRepository
    private val speechHelper = SpeechRecognizerHelper(application)
    private val openRouterService = OpenRouterService()
    private val agentSimulator = HerdrAgentSimulator()
    private val wsClient = HerdrWebSocketClient(viewModelScope)
    private val herdrConnectionService = com.herdr.remote.data.network.HerdrConnectionService()

    val settings: StateFlow<SettingsData> = settingsRepository.settings
    val sessions: StateFlow<List<Session>> = sessionRepository.sessions
    val activeSessionId: StateFlow<String> = sessionRepository.activeSessionId
    val wsConnectionStatus: StateFlow<AgentConnectionStatus> = wsClient.connectionStatus

    val activeSession: StateFlow<Session?> = combine(sessions, activeSessionId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeMessages: StateFlow<List<Message>> = combine(sessionRepository.messagesMap, activeSessionId) { map, id ->
        map[id] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val speechState: StateFlow<SpeechState> = speechHelper.speechState

    // UI state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    private val _isRephrasing = MutableStateFlow(false)
    val isRephrasing: StateFlow<Boolean> = _isRephrasing.asStateFlow()

    private val _comparisonDialog = MutableStateFlow<RephraseComparison?>(null)
    val comparisonDialog: StateFlow<RephraseComparison?> = _comparisonDialog.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isNewSessionDialogOpen = MutableStateFlow(false)
    val isNewSessionDialogOpen: StateFlow<Boolean> = _isNewSessionDialogOpen.asStateFlow()

    private val _isAttachmentSheetOpen = MutableStateFlow(false)
    val isAttachmentSheetOpen: StateFlow<Boolean> = _isAttachmentSheetOpen.asStateFlow()

    private val _previewImageUrl = MutableStateFlow<String?>(null)
    val previewImageUrl: StateFlow<String?> = _previewImageUrl.asStateFlow()

    private var activeSimulationJob: Job? = null

    init {
        observeSpeechState()
        observeWebSocketEvents()
        observeWebSocketStatus()
        observeSettings()
    }

    private fun observeWebSocketStatus() {
        viewModelScope.launch {
            wsClient.connectionStatus.collect { status ->
                val detail = when (status) {
                    AgentConnectionStatus.ONLINE -> "Online • Herdr Node Ready"
                    AgentConnectionStatus.CONNECTING -> "Connecting to node..."
                    AgentConnectionStatus.OFFLINE -> "Offline • Disconnected"
                    else -> "Online"
                }
                sessionRepository.updateAllSessionsStatus(status, detail)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.collect { cfg ->
                if (cfg.herdrServerUrl.isNotBlank()) {
                    wsClient.connect(cfg.herdrServerUrl)
                } else {
                    wsClient.disconnect()
                }
            }
        }
    }

    private fun observeSpeechState() {
        viewModelScope.launch {
            speechState.collect { state ->
                if (state is SpeechState.Success && state.finalText.isNotBlank()) {
                    handleSpeechFinalText(state.finalText)
                }
            }
        }
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            wsClient.events.collect { event ->
                when (event) {
                    is HerdrServerEvent.Connected -> {
                        sessionRepository.updateAllSessionsStatus(AgentConnectionStatus.ONLINE, "Online • Herdr Node Ready")
                        wsClient.requestActiveSessions()
                    }
                    is HerdrServerEvent.Disconnected -> {
                        sessionRepository.updateAllSessionsStatus(AgentConnectionStatus.OFFLINE, "Offline • Disconnected")
                    }
                    is HerdrServerEvent.Error -> {
                        sessionRepository.updateAllSessionsStatus(AgentConnectionStatus.OFFLINE, "Error • ${event.message}")
                    }
                    is HerdrServerEvent.ActiveSessionsReceived -> {
                        sessionRepository.syncRemoteSessions(event.sessions)
                    }
                    is HerdrServerEvent.StreamChunk -> {
                        sessionRepository.appendStreamChunkToLastAgentMessage(event.sessionId, event.chunk)
                    }
                    is HerdrServerEvent.AgentStatusChanged -> {
                        sessionRepository.updateSessionStatus(event.sessionId, event.status, event.detail)
                    }
                    is HerdrServerEvent.MessageComplete -> {
                        sessionRepository.addMessage(event.message)
                        val targetSession = sessions.value.find { it.id == event.message.sessionId }
                        if (targetSession != null) {
                            com.herdr.remote.util.NotificationHelper.sendTaskCompletedNotification(
                                getApplication(),
                                targetSession,
                                event.message
                            )
                        }
                    }
                    is HerdrServerEvent.ToolStarted -> {
                        // Tool started
                    }
                    is HerdrServerEvent.ToolFinished -> {
                        // Tool finished
                    }
                }
            }
        }
    }

    fun testHerdrConnection(serverUrl: String, onResult: (com.herdr.remote.data.network.HerdrConnectionResult) -> Unit) {
        viewModelScope.launch {
            val result = herdrConnectionService.testConnection(serverUrl)
            if (result.isSuccess && result.remoteSessions.isNotEmpty()) {
                sessionRepository.syncRemoteSessions(result.remoteSessions)
            }
            onResult(result)
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun selectSession(sessionId: String) {
        sessionRepository.switchSession(sessionId)
    }

    fun createSession(title: String, profile: AgentProfile) {
        sessionRepository.createSession(title, profile)
    }

    fun createNewHerdrSession() {
        val currentCount = sessions.value.size
        val defaultProfile = AgentProfile.PRESET_PROFILES[0]
        val title = "Herdr Session ${currentCount + 1}"
        sessionRepository.createSession(title, defaultProfile)
    }

    fun syncTabsWithDesktop(onComplete: ((Int) -> Unit)? = null) {
        val currentUrl = settings.value.herdrServerUrl
        if (currentUrl.isNotBlank()) {
            wsClient.requestActiveSessions()
            viewModelScope.launch {
                val result = herdrConnectionService.testConnection(currentUrl)
                if (result.isSuccess && result.remoteSessions.isNotEmpty()) {
                    sessionRepository.syncRemoteSessions(result.remoteSessions)
                    onComplete?.invoke(result.remoteSessions.size)
                } else {
                    onComplete?.invoke(0)
                }
            }
        } else {
            onComplete?.invoke(0)
        }
    }

    fun closeSession(sessionId: String) {
        sessionRepository.closeSession(sessionId)
    }

    fun clearChat() {
        val currentId = activeSessionId.value
        sessionRepository.clearSessionMessages(currentId)
    }

    fun toggleAutoRephrase() {
        val current = settings.value.autoRephraseOnSpeech
        settingsRepository.toggleAutoRephrase(!current)
    }

    fun openSettings() { _isSettingsOpen.value = true }
    fun closeSettings() { _isSettingsOpen.value = false }
    fun saveSettings(newSettings: SettingsData) {
        settingsRepository.updateSettings(newSettings)
    }

    fun openNewSessionDialog() { _isNewSessionDialogOpen.value = true }
    fun closeNewSessionDialog() { _isNewSessionDialogOpen.value = false }

    fun openAttachmentSheet() { _isAttachmentSheetOpen.value = true }
    fun closeAttachmentSheet() { _isAttachmentSheetOpen.value = false }

    fun setPreviewImage(url: String?) { _previewImageUrl.value = url }

    fun addAttachment(attachment: Attachment) {
        _pendingAttachments.value = _pendingAttachments.value + attachment
    }

    fun removeAttachment(attachmentId: String) {
        _pendingAttachments.value = _pendingAttachments.value.filter { it.id != attachmentId }
    }

    fun addSampleImage() {
        val sample = Attachment(
            type = AttachmentType.IMAGE,
            uriString = "https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=800&q=80",
            name = "system_architecture_diagram.png",
            sizeBytes = 245000,
            mimeType = "image/png"
        )
        addAttachment(sample)
    }

    fun addSamplePdf() {
        val sample = Attachment(
            type = AttachmentType.PDF,
            uriString = "content://com.herdr.remote.provider/sample_agent_spec.pdf",
            name = "Herdr_Remote_Protocol_v2.pdf",
            sizeBytes = 1420000,
            mimeType = "application/pdf"
        )
        addAttachment(sample)
    }

    // Voice Recording & AI Rephrase Flow
    fun startVoiceRecording() {
        val lang = settings.value.speechLanguage
        speechHelper.startListening(lang)
    }

    fun stopVoiceRecording(triggerRephrase: Boolean) {
        val currentSpeech = speechState.value
        val rawText = if (currentSpeech is SpeechState.Listening) currentSpeech.partialText else ""
        speechHelper.stopListening()

        if (rawText.isNotBlank()) {
            handleSpeechFinalText(rawText)
        }
    }

    fun cancelVoiceRecording() {
        speechHelper.reset()
        _isRephrasing.value = false
    }

    private fun handleSpeechFinalText(spokenText: String) {
        val cfg = settings.value
        if (cfg.autoRephraseOnSpeech) {
            _isRephrasing.value = true
            viewModelScope.launch {
                val result = openRouterService.rephrasePrompt(
                    spokenText = spokenText,
                    apiKey = cfg.openRouterApiKey,
                    model = cfg.openRouterModel,
                    systemPrompt = cfg.rephraseSystemPrompt
                )
                _isRephrasing.value = false
                val refined = result.getOrDefault(openRouterService.cleanTextLocally(spokenText))
                _comparisonDialog.value = RephraseComparison(originalText = spokenText, rephrasedText = refined)
            }
        } else {
            // Append directly to input
            _inputText.value = if (_inputText.value.isBlank()) spokenText else "${_inputText.value} $spokenText"
            speechHelper.reset()
        }
    }

    fun triggerManualRephrase() {
        val currentText = _inputText.value.trim()
        if (currentText.isBlank()) return

        _isRephrasing.value = true
        val cfg = settings.value
        viewModelScope.launch {
            val result = openRouterService.rephrasePrompt(
                spokenText = currentText,
                apiKey = cfg.openRouterApiKey,
                model = cfg.openRouterModel,
                systemPrompt = cfg.rephraseSystemPrompt
            )
            _isRephrasing.value = false
            val refined = result.getOrDefault(openRouterService.cleanTextLocally(currentText))
            _comparisonDialog.value = RephraseComparison(originalText = currentText, rephrasedText = refined)
        }
    }

    fun acceptRephraseAndSend(rephrased: String, original: String) {
        _comparisonDialog.value = null
        speechHelper.reset()
        sendUserMessage(content = rephrased, originalSpoken = original)
    }

    fun acceptRephraseToInput(rephrased: String) {
        _comparisonDialog.value = null
        _inputText.value = rephrased
        speechHelper.reset()
    }

    fun useOriginalSpoken(original: String) {
        _comparisonDialog.value = null
        _inputText.value = original
        speechHelper.reset()
    }

    fun dismissComparison() {
        _comparisonDialog.value = null
        speechHelper.reset()
    }

    // Message Sending & Agent Dispatch
    fun sendUserMessage(content: String = _inputText.value, originalSpoken: String? = null) {
        val trimmed = content.trim()
        val attachmentsToSend = _pendingAttachments.value.toList()

        if (trimmed.isEmpty() && attachmentsToSend.isEmpty()) return

        val currentSession = activeSession.value ?: return

        val userMessage = Message(
            sessionId = currentSession.id,
            sender = MessageSender.USER,
            content = trimmed,
            attachments = attachmentsToSend,
            originalSpokenText = originalSpoken,
            status = MessageStatus.SENT
        )

        sessionRepository.addMessage(userMessage)

        // Clear input and attachments
        _inputText.value = ""
        _pendingAttachments.value = emptyList()

        // Dispatch directly to connected Herdr cluster
        wsClient.sendMessage(currentSession.id, trimmed, attachmentsToSend)
    }

    private fun dispatchToSimulator(session: Session, userMessage: Message) {
        activeSimulationJob?.cancel()
        activeSimulationJob = viewModelScope.launch {
            val pendingAgentMessage = Message(
                sessionId = session.id,
                sender = MessageSender.AGENT,
                content = "",
                status = MessageStatus.STREAMING
            )
            sessionRepository.addMessage(pendingAgentMessage)

            agentSimulator.generateAgentResponse(
                sessionId = session.id,
                prompt = userMessage.content,
                attachments = userMessage.attachments,
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

                        if (step.tool.requiresPermission || step.tool.status == com.herdr.remote.data.model.ToolStatus.REQUIRES_APPROVAL) {
                            val currentSession = sessions.value.find { it.id == session.id } ?: session
                            com.herdr.remote.util.NotificationHelper.sendPermissionRequestNotification(
                                getApplication(),
                                currentSession,
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
                        val currentSession = sessions.value.find { it.id == session.id } ?: session
                        com.herdr.remote.util.NotificationHelper.sendTaskCompletedNotification(
                            getApplication(),
                            currentSession,
                            step.message
                        )
                    }
                }
            }
        }
    }
}
