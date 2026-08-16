package com.herdr.remote.data.model

import java.util.UUID

enum class ToolStatus {
    RUNNING,
    REQUIRES_APPROVAL,
    SUCCESS,
    FAILED,
    REJECTED
}

data class ToolExecution(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String? = null,
    val status: ToolStatus = ToolStatus.RUNNING,
    val durationMs: Long = 0,
    val requiresPermission: Boolean = false,
    val permissionPrompt: String? = null
)
