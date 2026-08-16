package com.herdr.remote.data.model

import java.util.UUID

enum class MessageSender {
    USER,
    AGENT,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT,
    STREAMING,
    ERROR
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val sender: MessageSender,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val attachments: List<Attachment> = emptyList(),
    val toolExecutions: List<ToolExecution> = emptyList(),
    val thought: String? = null,
    val originalSpokenText: String? = null,
    val errorMessage: String? = null,
    val fallbackModel: String? = null
)
