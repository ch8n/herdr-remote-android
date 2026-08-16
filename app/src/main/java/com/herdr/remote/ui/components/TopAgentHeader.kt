package com.herdr.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.Session
import com.herdr.remote.ui.theme.AccentAmber
import com.herdr.remote.ui.theme.AccentCyan
import com.herdr.remote.ui.theme.AccentEmerald
import com.herdr.remote.ui.theme.AccentPrimary
import com.herdr.remote.ui.theme.AccentRose
import com.herdr.remote.ui.theme.AccentViolet
import com.herdr.remote.ui.theme.BorderSubtle
import com.herdr.remote.ui.theme.SurfaceDark
import com.herdr.remote.ui.theme.SurfaceElevated
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary

@Composable
fun TopAgentHeader(
    session: Session,
    autoRephraseEnabled: Boolean,
    onToggleAutoRephrase: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearChat: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when (session.status) {
            AgentConnectionStatus.ONLINE -> AccentEmerald
            AgentConnectionStatus.THINKING -> AccentAmber
            AgentConnectionStatus.EXECUTING_TOOL -> AccentCyan
            AgentConnectionStatus.STREAMING -> AccentViolet
            AgentConnectionStatus.CONNECTING -> AccentPrimary
            AgentConnectionStatus.OFFLINE -> AccentRose
        },
        label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (session.status != AgentConnectionStatus.ONLINE && session.status != AgentConnectionStatus.OFFLINE) 1.35f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Agent Avatar and Title block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Agent Avatar with status ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SurfaceElevated, Color(0xFF1E293B))
                            )
                        )
                        .border(1.5.dp, BorderSubtle, CircleShape)
                ) {
                    Text(
                        text = session.agentProfile.avatarEmoji,
                        fontSize = 20.sp
                    )

                    // Live Status Dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(11.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(statusColor)
                            .border(1.5.dp, SurfaceDark, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // AGENT NAME and live status detail
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.agentProfile.name.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = when (session.status) {
                                AgentConnectionStatus.ONLINE -> "Online • ${session.title}"
                                AgentConnectionStatus.THINKING -> "Thinking..."
                                AgentConnectionStatus.EXECUTING_TOOL -> session.statusDetail
                                AgentConnectionStatus.STREAMING -> "Streaming response..."
                                AgentConnectionStatus.CONNECTING -> "Connecting to node..."
                                AgentConnectionStatus.OFFLINE -> "Offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // AI Rephrase Quick Indicator/Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (autoRephraseEnabled) AccentViolet.copy(alpha = 0.18f) else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (autoRephraseEnabled) AccentViolet.copy(alpha = 0.5f) else BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onToggleAutoRephrase() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto Rephrase",
                            tint = if (autoRephraseEnabled) AccentViolet else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (autoRephraseEnabled) "AI Prompt" else "Raw",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (autoRephraseEnabled) AccentViolet else TextMuted
                        )
                    }
                }

                // Settings Button
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // More Options Menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Session Tab", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onNewSession()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat Messages", color = AccentRose) },
                            onClick = {
                                showMenu = false
                                onClearChat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("App Settings", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
        }
    }
}
