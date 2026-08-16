package com.herdr.remote.data.model

import java.util.UUID

enum class AgentConnectionStatus {
    ONLINE,
    CONNECTING,
    THINKING,
    EXECUTING_TOOL,
    STREAMING,
    OFFLINE
}

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val agentProfile: AgentProfile,
    val createdAt: Long = System.currentTimeMillis(),
    val status: AgentConnectionStatus = AgentConnectionStatus.ONLINE,
    val statusDetail: String = "Ready for instructions",
    val unreadCount: Int = 0,
    val modelOverride: String? = null
)
