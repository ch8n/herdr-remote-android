package com.herdr.remote.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.AttachmentType
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.model.ToolExecution
import com.herdr.remote.data.model.ToolStatus
import com.herdr.remote.ui.theme.AccentCyan
import com.herdr.remote.ui.theme.AccentEmerald
import com.herdr.remote.ui.theme.AccentPrimary
import com.herdr.remote.ui.theme.AccentRose
import com.herdr.remote.ui.theme.AccentViolet
import com.herdr.remote.ui.theme.BorderHighlight
import com.herdr.remote.ui.theme.BorderSubtle
import com.herdr.remote.ui.theme.BubbleAgent
import com.herdr.remote.ui.theme.BubbleAgentBorder
import com.herdr.remote.ui.theme.BubbleUser
import com.herdr.remote.ui.theme.BubbleUserBorder
import com.herdr.remote.ui.theme.CodeBackground
import com.herdr.remote.ui.theme.CodeBorder
import com.herdr.remote.ui.theme.SurfaceCard
import com.herdr.remote.ui.theme.SurfaceElevated
import com.herdr.remote.ui.theme.TextHighlight
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    message: Message,
    agentProfile: AgentProfile,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val isSystem = message.sender == MessageSender.SYSTEM
    val context = LocalContext.current

    val timeString = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    if (isSystem) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Agent Avatar next to bubble
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = agentProfile.avatarEmoji, fontSize = 16.sp)
            }
        }

        var isRawMode by remember { mutableStateOf(false) }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Main Bubble Container
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) {
                            Brush.linearGradient(
                                colors = listOf(BubbleUser, Color(0xFF3730A3))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(BubbleAgent, Color(0xFF131B2E))
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (isUser) BubbleUserBorder else BubbleAgentBorder,
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Header Bar with Agent Name (or User label), Preview/Raw Toggle, and Copy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isUser) "You" else agentProfile.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isUser) AccentViolet else AccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Markdown Toggle Pill (Preview vs Raw)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isRawMode) AccentViolet.copy(alpha = 0.25f) else SurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isRawMode) AccentViolet else BorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { isRawMode = !isRawMode }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRawMode) Icons.Default.Code else Icons.Default.Visibility,
                                        contentDescription = if (isRawMode) "Raw Markdown" else "Rendered Preview",
                                        tint = if (isRawMode) AccentViolet else AccentCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isRawMode) "RAW" else "PREVIEW",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isRawMode) AccentViolet else TextSecondary
                                    )
                                }
                            }

                            // Contextual Copy Button (Copies Rendered text or Raw Markdown based on toggle)
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val textToCopy = if (isRawMode) message.content else stripMarkdownForPlainCopy(message.content)
                                    val clip = ClipData.newPlainText("Herdr Message", textToCopy)
                                    clipboard.setPrimaryClip(clip)
                                    val toastMsg = if (isRawMode) "Copied raw markdown" else "Copied rendered preview"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // Attachments Rendering (if any)
                    if (message.attachments.isNotEmpty()) {
                        message.attachments.forEach { attachment ->
                            AttachmentBubbleItem(
                                attachment = attachment,
                                onImageClick = onImageClick
                            )
                        }
                    }

                    // Spoken voice transcript badge if original text was rephrased
                    if (isUser && !message.originalSpokenText.isNullOrBlank() && message.originalSpokenText != message.content) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF312E81))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Rephrased",
                                    tint = AccentViolet,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AI Refined Prompt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFDDD6FE)
                                )
                            }
                        }
                    }

                    // Agent Monologue / Thought Section (if present)
                    if (!message.thought.isNullOrBlank()) {
                        AgentThoughtCard(thought = message.thought)
                    }

                    // Agent Tool Executions (if present)
                    if (message.toolExecutions.isNotEmpty()) {
                        message.toolExecutions.forEach { tool ->
                            ToolExecutionCard(tool = tool)
                        }
                    }

                    // Main Text Content with Markdown formatting
                    if (message.content.isNotBlank()) {
                        MarkdownMessageText(
                            text = message.content,
                            isRawMode = isRawMode,
                            isStreaming = message.status == MessageStatus.STREAMING
                        )
                    }

                    // Timestamp & Status Ticks
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) Color(0xFFC7D2FE) else TextMuted
                        )

                        if (isUser) {
                            when (message.status) {
                                MessageStatus.SENDING -> {
                                    CircularProgressIndicator(
                                        strokeWidth = 1.5.dp,
                                        color = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                MessageStatus.SENT -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                MessageStatus.ERROR -> {
                                    Text(
                                        text = "!",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentRose,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            // User Avatar next to user bubble
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BubbleUser, Color(0xFF3730A3))
                        )
                    )
                    .border(1.dp, BubbleUserBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "👤", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun AttachmentBubbleItem(
    attachment: Attachment,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current

    when (attachment.type) {
        AttachmentType.IMAGE -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onImageClick(attachment.uriString) }
            ) {
                AsyncImage(
                    model = attachment.uriString,
                    contentDescription = attachment.name,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        AttachmentType.PDF -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(attachment.uriString), "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = AccentRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "PDF Document • Tap to open",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "File",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun AgentThoughtCard(thought: String) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Reasoning",
                        tint = AccentViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reasoning Process",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentViolet
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Thought",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thought,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ToolExecutionCard(tool: ToolExecution) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
            .border(1.dp, CodeBorder, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(8.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Tool",
                        tint = AccentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tool.toolName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AccentCyan
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tool.status == ToolStatus.RUNNING) {
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp,
                            color = AccentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    } else if (tool.status == ToolStatus.REQUIRES_APPROVAL) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentRose.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "APPROVAL NEEDED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = AccentRose
                            )
                        }
                    } else if (tool.status == ToolStatus.REJECTED) {
                        Text(
                            text = "Denied",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AccentRose
                        )
                    } else {
                        Text(
                            text = "${tool.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentEmerald
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Details",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Inline Approval Buttons if pending permission
            if (tool.status == ToolStatus.REQUIRES_APPROVAL) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            com.herdr.remote.HerdrApplication.instance.handlePermissionDecision(
                                sessionId = tool.id,
                                toolId = tool.id,
                                approved = true
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text(
                            text = "✅ Approve",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Button(
                        onClick = {
                            com.herdr.remote.HerdrApplication.instance.handlePermissionDecision(
                                sessionId = tool.id,
                                toolId = tool.id,
                                approved = false
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text(
                            text = "❌ Deny",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded || tool.status == ToolStatus.REQUIRES_APPROVAL) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        text = "Args: ${tool.argumentsJson}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary
                    )
                    if (!tool.resultJson.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Result: ${tool.resultJson}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (tool.status == ToolStatus.REJECTED) AccentRose else AccentEmerald
                        )
                    }
                }
            }
        }
    }
}

sealed class ContentBlock {
    data class TextBlock(val content: String) : ContentBlock()
    data class CodeBlock(val language: String, val code: String) : ContentBlock()
}

fun cleanDividerLines(text: String): String {
    val lines = text.lines()
    val cleaned = mutableListOf<String>()
    var prevWasDivider = false

    for (line in lines) {
        val trimmed = line.trim()
        val isDivider = trimmed.length >= 3 && trimmed.all { it == '─' || it == '━' || it == '═' || it == '-' || it == '_' || it == '=' || it == '~' || it == ' ' }
        if (isDivider) {
            if (!prevWasDivider) {
                cleaned.add("\n---\n")
                prevWasDivider = true
            }
        } else {
            prevWasDivider = false
            cleaned.add(line)
        }
    }
    val result = cleaned.joinToString("\n")
    return result
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

fun parseContentBlocks(rawText: String): List<ContentBlock> {
    val sanitized = cleanDividerLines(rawText)
    val blocks = mutableListOf<ContentBlock>()
    val codeFenceRegex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)
    var lastIndex = 0

    val matches = codeFenceRegex.findAll(sanitized).toList()
    if (matches.isEmpty()) {
        return listOf(ContentBlock.TextBlock(sanitized))
    }

    for (match in matches) {
        val matchStart = match.range.first
        val matchEnd = match.range.last + 1

        if (matchStart > lastIndex) {
            val textPart = sanitized.substring(lastIndex, matchStart).trim()
            if (textPart.isNotEmpty()) {
                blocks.add(ContentBlock.TextBlock(textPart))
            }
        }

        val lang = match.groupValues[1].ifBlank { "terminal" }
        val codeContent = match.groupValues[2].trim()
        blocks.add(ContentBlock.CodeBlock(language = lang, code = codeContent))
        lastIndex = matchEnd
    }

    if (lastIndex < sanitized.length) {
        val remaining = sanitized.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(ContentBlock.TextBlock(remaining))
        }
    }

    return blocks
}

@Composable
fun CodeOrTerminalCard(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    val isTerminal = language.equals("terminal", ignoreCase = true) || language.equals("bash", ignoreCase = true) || language.equals("sh", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isTerminal) Color(0xFF090D16) else CodeBackground)
            .border(1.dp, if (isTerminal) AccentCyan.copy(alpha = 0.3f) else CodeBorder, RoundedCornerShape(10.dp))
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isTerminal) Color(0xFF0E1726) else SurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        imageVector = if (isTerminal) Icons.Default.Terminal else Icons.Default.Code,
                        contentDescription = "Code type",
                        tint = if (isTerminal) AccentCyan else AccentViolet,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isTerminal) "💻 TERMINAL" else "⚡ ${language.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        color = if (isTerminal) AccentCyan else AccentViolet
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                            copied = true
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (copied) AccentEmerald else TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (copied) "COPIED" else "COPY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                        color = if (copied) AccentEmerald else TextMuted
                    )
                }
            }

            // Monospace code content
            Box(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    ),
                    color = if (isTerminal) Color(0xFF93C5FD) else TextHighlight
                )
            }
        }
    }
}

@Composable
fun MarkdownMessageText(
    text: String,
    isRawMode: Boolean,
    isStreaming: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    if (isRawMode) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CodeBackground)
                .border(1.dp, CodeBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = TextHighlight
            )
        }
        return
    }

    val blocks = remember(text) { parseContentBlocks(text) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.CodeBlock -> {
                    CodeOrTerminalCard(
                        language = block.language,
                        code = block.code
                    )
                }
                is ContentBlock.TextBlock -> {
                    if (block.content.isNotBlank()) {
                        MarkdownText(
                            markdown = block.content,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = TextPrimary
                            ),
                            linkColor = AccentCyan,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (isStreaming) {
            Box(
                modifier = Modifier
                    .size(8.dp, 16.dp)
                    .background(AccentViolet.copy(alpha = cursorAlpha))
            )
        }
    }
}

/**
 * Strips markdown syntax (headers, code fences, asterisks, links) to provide clean text when copying rendered preview.
 */
fun stripMarkdownForPlainCopy(raw: String): String {
    return raw
        .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), "")
        .replace("```", "")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        .trim()
}
