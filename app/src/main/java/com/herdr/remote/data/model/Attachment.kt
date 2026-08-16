package com.herdr.remote.data.model

import java.util.UUID

enum class AttachmentType {
    IMAGE,
    PDF,
    DOCUMENT,
    AUDIO
}

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val uriString: String,
    val name: String,
    val sizeBytes: Long = 0,
    val mimeType: String = "",
    val base64Data: String? = null
)
