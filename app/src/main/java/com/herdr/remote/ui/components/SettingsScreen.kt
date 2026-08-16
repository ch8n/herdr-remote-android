package com.herdr.remote.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.herdr.remote.HerdrApplication
import com.herdr.remote.data.network.HerdrConnectionResult
import com.herdr.remote.data.network.HerdrConnectionService
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.herdr.remote.data.model.OpenRouterModel
import com.herdr.remote.data.model.SettingsData
import com.herdr.remote.data.network.OpenRouterService
import com.herdr.remote.ui.theme.AccentAmber
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
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary
import com.herdr.remote.util.TailscaleHelper
import com.herdr.remote.util.TailscaleState
import com.herdr.remote.util.TailscaleStatus
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentSettings: SettingsData,
    onSaveSettings: (SettingsData) -> Unit,
    onBack: () -> Unit,
    onOpenModelSelector: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openRouterService = remember { OpenRouterService() }

    var apiKey by remember { mutableStateOf(currentSettings.openRouterApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(currentSettings.openRouterModel) }
    var serverUrl by remember { mutableStateOf(currentSettings.herdrServerUrl) }
    var isMockMode by remember { mutableStateOf(currentSettings.isMockMode) }
    var autoRephrase by remember { mutableStateOf(currentSettings.autoRephraseOnSpeech) }
    var rephrasePrompt by remember { mutableStateOf(currentSettings.rephraseSystemPrompt) }

    var isTestingKey by remember { mutableStateOf(false) }
    var testKeyStatus by remember { mutableStateOf<String?>(null) }

    val herdrConnectionService = remember { HerdrConnectionService() }
    var isTestingHerdr by remember { mutableStateOf(false) }
    var herdrTestResult by remember { mutableStateOf<HerdrConnectionResult?>(null) }

    fun testHerdr() {
        if (serverUrl.isBlank()) return
        isTestingHerdr = true
        herdrTestResult = null
        scope.launch {
            val result = herdrConnectionService.testConnection(serverUrl)
            isTestingHerdr = false
            herdrTestResult = result
            if (result.isSuccess && result.remoteSessions.isNotEmpty()) {
                HerdrApplication.instance.sessionRepository.syncRemoteSessions(result.remoteSessions)
                Toast.makeText(context, "Synced ${result.remoteSessions.size} active sessions!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Tailscale Status (reactive)
    val tailscaleStatus by produceState(initialValue = TailscaleHelper.getStatus(context)) {
        TailscaleHelper.observeStatus(context).collect { value = it }
    }

    // Dynamic Models List State
    var availableModels by remember { mutableStateOf<List<OpenRouterModel>>(OpenRouterModel.DEFAULT_MODELS) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }

    fun loadModels() {
        isLoadingModels = true
        scope.launch {
            val result = openRouterService.fetchModels(apiKey.ifBlank { null })
            isLoadingModels = false
            result.onSuccess { list ->
                if (list.isNotEmpty()) {
                    availableModels = list
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadModels()
    }

    val selectedModelObj = remember(availableModels, selectedModel) {
        availableModels.find { it.id == selectedModel } ?: OpenRouterModel(
            id = selectedModel,
            name = selectedModel
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Preferences & AI Config",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: OpenRouter AI Key & Searchable Model Dropdown
            SettingsCard(title = "OPENROUTER AI REPHRASE", icon = Icons.Default.AutoAwesome, iconTint = AccentViolet) {
                Text(
                    text = "OpenRouter is used to automatically remove speech fillers ('um', 'uh', 'you know') and structure voice commands into crisp agent prompts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API Key (Persisted to Device)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Quick Paste Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasted = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                        if (pasted.isNotBlank()) {
                                            apiKey = pasted
                                            onSaveSettings(currentSettings.copy(openRouterApiKey = pasted))
                                            Toast.makeText(context, "API Key pasted & saved", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Paste",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = AccentCyan
                                )
                            }
                        }

                        // Quick Copy Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                .clickable {
                                    if (apiKey.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("OpenRouter API Key", apiKey)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "API Key copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No API key to copy", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = AccentViolet,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = AccentViolet
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testKeyStatus = null
                        onSaveSettings(currentSettings.copy(openRouterApiKey = it))
                    },
                    placeholder = { Text("sk-or-v1-...", color = TextMuted, fontSize = 14.sp) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = TextMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Test Key Button & Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "Please paste an API key first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTestingKey = true
                            testKeyStatus = null
                            scope.launch {
                                val result = openRouterService.testApiKey(apiKey, selectedModel)
                                isTestingKey = false
                                if (result.isSuccess) {
                                    testKeyStatus = "Valid API Key Connected!"
                                    loadModels() // refresh live models with valid key
                                } else {
                                    testKeyStatus = "Error: ${result.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingKey) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = AccentViolet,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = "Test API Key",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    if (testKeyStatus != null) {
                        Text(
                            text = testKeyStatus!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (testKeyStatus!!.startsWith("Valid")) AccentEmerald else AccentRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model Selection Dropdown Trigger Card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI Model Selection",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { loadModels() }
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp,
                                color = AccentViolet,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "${availableModels.size} Models Available",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentViolet
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Interactive Dropdown Card for Model Picker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, AccentViolet.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable {
                            if (onOpenModelSelector != null) {
                                onOpenModelSelector(selectedModel)
                            } else {
                                showModelSelector = true
                            }
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = selectedModelObj.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1
                                )

                                if (selectedModelObj.isFree) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentEmerald.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "FREE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = AccentEmerald
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = selectedModelObj.id,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = AccentViolet,
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = AccentViolet,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Browse",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Open dropdown",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Section 2: Herdr Remote Connection & Tailscale Integration
            SettingsCard(title = "HERDR REMOTE NODE & TAILSCALE", icon = Icons.Default.CloudQueue, iconTint = AccentCyan) {
                Text(
                    text = "Connect securely to your remote Herdr server or agent daemon across your private network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Tailscale Status Card
                val isTsConnected = tailscaleStatus.state == TailscaleState.CONNECTED
                val statusCardBorder = when (tailscaleStatus.state) {
                    TailscaleState.CONNECTED -> AccentEmerald
                    TailscaleState.DISCONNECTED -> AccentAmber
                    TailscaleState.NOT_INSTALLED -> BorderSubtle
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark)
                        .border(1.dp, statusCardBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            TailscaleHelper.openTailscaleOrPlayStore(context)
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (tailscaleStatus.state) {
                                            TailscaleState.CONNECTED -> AccentEmerald.copy(alpha = 0.2f)
                                            TailscaleState.DISCONNECTED -> AccentAmber.copy(alpha = 0.2f)
                                            TailscaleState.NOT_INSTALLED -> SurfaceElevated
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTsConnected) Icons.Default.Lan else Icons.Default.VpnKey,
                                    contentDescription = "Tailscale",
                                    tint = when (tailscaleStatus.state) {
                                        TailscaleState.CONNECTED -> AccentEmerald
                                        TailscaleState.DISCONNECTED -> AccentAmber
                                        TailscaleState.NOT_INSTALLED -> TextMuted
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Tailscale VPN",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isTsConnected) AccentEmerald else AccentAmber
                                            )
                                    )
                                }
                                Text(
                                    text = tailscaleStatus.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isTsConnected) AccentEmerald else TextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (tailscaleStatus.isAppInstalled) "Open" else "Get App",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isTsConnected) AccentEmerald else AccentCyan
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = if (isTsConnected) AccentEmerald else AccentCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mock Mode Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Autonomous Mock Simulation",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Simulates agent reasoning & tool streaming without a live backend",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isMockMode,
                        onCheckedChange = {
                            isMockMode = it
                            onSaveSettings(currentSettings.copy(isMockMode = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.3f)
                        )
                    )
                }

                // WebSocket Server URL Input (unlocked / enabled)
                if (!isMockMode) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Herdr Remote URL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )

                        // Quick Paste Tailscale format button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val pasted = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                        if (pasted.isNotBlank()) {
                                            serverUrl = pasted
                                            onSaveSettings(currentSettings.copy(herdrServerUrl = pasted, isMockMode = false))
                                            Toast.makeText(context, "URL pasted & saved", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Paste URL",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = AccentCyan
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            onSaveSettings(currentSettings.copy(herdrServerUrl = it))
                        },
                        placeholder = { Text("ws://100.x.y.z:8080/herdr/ws", color = TextMuted, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tip: With Tailscale active, you can use ws://100.x.y.z:8080/herdr/ws or ws://machinename.ts.net:8080/herdr/ws directly.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Connection Button & Sync Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { testHerdr() },
                            enabled = !isTestingHerdr && serverUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (herdrTestResult?.isSuccess == true) AccentEmerald else AccentCyan,
                                disabledContainerColor = SurfaceElevated
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingHerdr) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Testing...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            } else {
                                Icon(
                                    imageVector = if (herdrTestResult?.isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Bolt,
                                    contentDescription = "Test Connection",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (herdrTestResult?.isSuccess == true) "Re-test Connection" else "Test Connection",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                        }

                        if (herdrTestResult?.isSuccess == true && herdrTestResult?.remoteSessions?.isNotEmpty() == true) {
                            Button(
                                onClick = {
                                    herdrTestResult?.remoteSessions?.let { list ->
                                        HerdrApplication.instance.sessionRepository.syncRemoteSessions(list)
                                        Toast.makeText(context, "Synced ${list.size} active sessions!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceElevated
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Sync",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sync Tabs (${herdrTestResult?.remoteSessions?.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = AccentCyan)
                                )
                            }
                        }
                    }

                    // Connection Result Status Card
                    herdrTestResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (res.isSuccess) AccentEmerald.copy(alpha = 0.12f) else AccentRose.copy(alpha = 0.12f))
                                .border(
                                    1.dp,
                                    if (res.isSuccess) AccentEmerald.copy(alpha = 0.4f) else AccentRose.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (res.isSuccess) AccentEmerald else AccentRose,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = if (res.isSuccess) AccentEmerald else AccentRose
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Voice & Speech Preferences
            SettingsCard(title = "SPEECH & REPHRASE BEHAVIOR", icon = Icons.Default.Mic, iconTint = AccentEmerald) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Rephrase Spoken Voice",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Automatically clean speech and strip verbal fillers on mic stop",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = autoRephrase,
                        onCheckedChange = {
                            autoRephrase = it
                            onSaveSettings(currentSettings.copy(autoRephraseOnSpeech = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentEmerald,
                            checkedTrackColor = AccentEmerald.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Save Preferences Button
            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        openRouterApiKey = apiKey.trim(),
                        openRouterModel = selectedModel.trim(),
                        herdrServerUrl = serverUrl.trim(),
                        isMockMode = isMockMode,
                        autoRephraseOnSpeech = autoRephrase,
                        rephraseSystemPrompt = rephrasePrompt.trim()
                    )
                    onSaveSettings(updated)
                    Toast.makeText(context, "Settings saved & synced successfully", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Preferences",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Searchable Model Selector Dialog Modal
    if (showModelSelector) {
        ModelSelectorDialog(
            models = availableModels,
            selectedModelId = selectedModel,
            isLoading = isLoadingModels,
            onRefresh = { loadModels() },
            onSelectModel = { modelId ->
                selectedModel = modelId
                onSaveSettings(currentSettings.copy(openRouterModel = modelId))
            },
            onDismiss = { showModelSelector = false }
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = iconTint
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
