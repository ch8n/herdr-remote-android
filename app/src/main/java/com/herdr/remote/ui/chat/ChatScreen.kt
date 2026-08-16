package com.herdr.remote.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.herdr.remote.data.model.AgentConnectionStatus
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.AttachmentType
import com.herdr.remote.speech.SpeechState
import com.herdr.remote.ui.components.AttachmentPickerSheet
import com.herdr.remote.ui.components.ChatBubble
import com.herdr.remote.ui.components.ImagePreviewDialog
import com.herdr.remote.ui.components.NewSessionDialog
import com.herdr.remote.ui.components.RephraseComparisonDialog
import com.herdr.remote.ui.components.SessionTabsBar
import com.herdr.remote.ui.components.SettingsScreen
import com.herdr.remote.ui.components.TopAgentHeader
import com.herdr.remote.ui.components.VoiceRecordingOverlay
import com.herdr.remote.ui.theme.AccentCyan
import com.herdr.remote.ui.theme.AccentEmerald
import com.herdr.remote.ui.theme.AccentPrimary
import com.herdr.remote.ui.theme.AccentRose
import com.herdr.remote.ui.theme.AccentViolet
import com.herdr.remote.ui.theme.BackgroundDark
import com.herdr.remote.ui.theme.BorderHighlight
import com.herdr.remote.ui.theme.BorderSubtle
import com.herdr.remote.ui.theme.SurfaceDark
import com.herdr.remote.ui.theme.SurfaceElevated
import com.herdr.remote.ui.theme.SurfaceInput
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val wsConnectionStatus by viewModel.wsConnectionStatus.collectAsState()

    val inputText by viewModel.inputText.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val speechState by viewModel.speechState.collectAsState()
    val isRephrasing by viewModel.isRephrasing.collectAsState()
    val comparisonDialog by viewModel.comparisonDialog.collectAsState()

    val isNewSessionDialogOpen by viewModel.isNewSessionDialogOpen.collectAsState()
    val isAttachmentSheetOpen by viewModel.isAttachmentSheetOpen.collectAsState()
    val previewImageUrl by viewModel.previewImageUrl.collectAsState()

    val isRecording = speechState is SpeechState.Listening || speechState is SpeechState.Initializing || isRephrasing

    val listState = rememberLazyListState()
    val showScrollToBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Activity Result Launchers for attachments
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            viewModel.addAttachment(
                Attachment(
                    type = AttachmentType.IMAGE,
                    uriString = it.toString(),
                    name = fileName,
                    mimeType = "image/*"
                )
            )
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = "document_${System.currentTimeMillis()}.pdf"
            viewModel.addAttachment(
                Attachment(
                    type = AttachmentType.PDF,
                    uriString = it.toString(),
                    name = fileName,
                    mimeType = "application/pdf"
                )
            )
        }
    }

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification Permission Launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Agent Header
                activeSession?.let { currentSession ->
                    TopAgentHeader(
                        session = currentSession,
                        autoRephraseEnabled = settings.autoRephraseOnSpeech,
                        onToggleAutoRephrase = { viewModel.toggleAutoRephrase() },
                        onOpenSettings = onOpenSettings,
                        onClearChat = { viewModel.clearChat() },
                        onNewSession = { viewModel.openNewSessionDialog() }
                    )
                }

                // Multi-session Tabs Bar
                SessionTabsBar(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    onSelectSession = { viewModel.selectSession(it) },
                    onCloseSession = { viewModel.closeSession(it) },
                    onNewSessionClick = { viewModel.openNewSessionDialog() }
                )

                // Message Thread
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                                    .padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (wsConnectionStatus == AgentConnectionStatus.ONLINE) AccentEmerald.copy(alpha = 0.15f)
                                            else AccentCyan.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (wsConnectionStatus == AgentConnectionStatus.ONLINE) Icons.Default.CheckCircle else Icons.Default.Lan,
                                        contentDescription = null,
                                        tint = if (wsConnectionStatus == AgentConnectionStatus.ONLINE) AccentEmerald else AccentCyan,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = if (wsConnectionStatus == AgentConnectionStatus.ONLINE) "Connected to Herdr Node" else "Herdr Node Disconnected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                                    color = TextPrimary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (wsConnectionStatus == AgentConnectionStatus.ONLINE)
                                        "Active sessions synced from Herdr daemon. Send a prompt below to interact with your agents."
                                    else
                                        "Configure your Herdr remote URL and test connectivity via Tailscale or your local network.",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp, fontSize = 13.sp),
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = onOpenSettings,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (wsConnectionStatus == AgentConnectionStatus.ONLINE) SurfaceElevated else AccentCyan
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = if (wsConnectionStatus == AgentConnectionStatus.ONLINE) TextPrimary else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Go to Settings & Connect",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (wsConnectionStatus == AgentConnectionStatus.ONLINE) TextPrimary else Color.White
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val currentProfile = activeSession?.agentProfile ?: com.herdr.remote.data.model.AgentProfile.PRESET_PROFILES[0]
                                ChatBubble(
                                    message = msg,
                                    agentProfile = currentProfile,
                                    onImageClick = { viewModel.setPreviewImage(it) }
                                )
                            }
                        }
                    }

                    // Jump to Bottom FAB
                    if (showScrollToBottom) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            },
                            containerColor = SurfaceElevated,
                            contentColor = AccentPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Input Bar Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Pending Attachments Preview Chips
                    if (pendingAttachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pendingAttachments.forEach { attachment ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceElevated)
                                        .border(1.dp, BorderHighlight, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (attachment.type == AttachmentType.IMAGE) Icons.Default.Image else Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = if (attachment.type == AttachmentType.IMAGE) AccentCyan else AccentRose,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = attachment.name.take(16),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { viewModel.removeAttachment(attachment.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Main Input Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Attachment Plus Button
                        IconButton(
                            onClick = { viewModel.openAttachmentSheet() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Add Attachment",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { viewModel.setInputText(it) },
                            placeholder = {
                                Text(
                                    text = "Instruct ${activeSession?.agentProfile?.name ?: "Agent"}...",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = SurfaceInput,
                                unfocusedContainerColor = SurfaceInput
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        )

                        // Magic Wand AI Rephrase Button (if input has text)
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = { viewModel.triggerManualRephrase() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AccentViolet.copy(alpha = 0.2f))
                                    .border(1.dp, AccentViolet.copy(alpha = 0.5f), CircleShape)
                            ) {
                                if (isRephrasing) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = AccentViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Polish Prompt",
                                        tint = AccentViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Speech Mic Button
                        IconButton(
                            onClick = {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasAudioPermission) {
                                    viewModel.startVoiceRecording()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Send Button
                        val canSend = inputText.isNotBlank() || pendingAttachments.isNotEmpty()
                        IconButton(
                            onClick = { viewModel.sendUserMessage() },
                            enabled = canSend,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) {
                                        Brush.linearGradient(
                                            colors = listOf(AccentPrimary, AccentCyan)
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(SurfaceElevated, SurfaceElevated)
                                        )
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Voice Recording Waveform Overlay
            AnimatedVisibility(
                visible = isRecording,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                VoiceRecordingOverlay(
                    speechState = speechState,
                    isRephrasing = isRephrasing,
                    onCancel = { viewModel.cancelVoiceRecording() },
                    onStopAndRephrase = { viewModel.stopVoiceRecording(triggerRephrase = true) },
                    onDirectSend = {
                        val currentSpeech = speechState
                        val raw = if (currentSpeech is SpeechState.Listening) currentSpeech.partialText else ""
                        viewModel.cancelVoiceRecording()
                        if (raw.isNotBlank()) {
                            viewModel.sendUserMessage(content = raw)
                        }
                    }
                )
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (isAttachmentSheetOpen) {
        AttachmentPickerSheet(
            onDismiss = { viewModel.closeAttachmentSheet() },
            onPickImage = { imagePickerLauncher.launch("image/*") },
            onPickPdf = { pdfPickerLauncher.launch("application/pdf") },
            onAddSampleImage = { viewModel.addSampleImage() },
            onAddSamplePdf = { viewModel.addSamplePdf() }
        )
    }

    // New Session Tab Dialog
    if (isNewSessionDialogOpen) {
        NewSessionDialog(
            onDismiss = { viewModel.closeNewSessionDialog() },
            onCreateSession = { title, profile ->
                viewModel.createSession(title, profile)
            }
        )
    }

    // AI Rephrase Comparison Dialog
    comparisonDialog?.let { comparison ->
        RephraseComparisonDialog(
            originalText = comparison.originalText,
            rephrasedText = comparison.rephrasedText,
            onAcceptAndSend = {
                viewModel.acceptRephraseAndSend(comparison.rephrasedText, comparison.originalText)
            },
            onInsertToInput = {
                viewModel.acceptRephraseToInput(comparison.rephrasedText)
            },
            onUseOriginal = {
                viewModel.useOriginalSpoken(comparison.originalText)
            },
            onDismiss = {
                viewModel.dismissComparison()
            }
        )
    }

    // Fullscreen Image Preview
    previewImageUrl?.let { url ->
        ImagePreviewDialog(
            imageUrl = url,
            onDismiss = { viewModel.setPreviewImage(null) }
        )
    }
}
