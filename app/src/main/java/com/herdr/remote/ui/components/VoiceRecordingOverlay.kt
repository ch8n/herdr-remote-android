package com.herdr.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.herdr.remote.speech.SpeechState
import com.herdr.remote.ui.theme.AccentEmerald
import com.herdr.remote.ui.theme.AccentPrimary
import com.herdr.remote.ui.theme.AccentRose
import com.herdr.remote.ui.theme.AccentViolet
import com.herdr.remote.ui.theme.BorderHighlight
import com.herdr.remote.ui.theme.BorderSubtle
import com.herdr.remote.ui.theme.SurfaceDark
import com.herdr.remote.ui.theme.SurfaceElevated
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary

@Composable
fun VoiceRecordingOverlay(
    speechState: SpeechState,
    isRephrasing: Boolean,
    onCancel: () -> Unit,
    onStopAndRephrase: () -> Unit,
    onDirectSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rmsDb = if (speechState is SpeechState.Listening) speechState.rmsDb else 0f
    val partialText = if (speechState is SpeechState.Listening) speechState.partialText else ""

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(SurfaceElevated, SurfaceDark)
                )
            )
            .border(1.dp, BorderHighlight, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isRephrasing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = AccentViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OpenRouter AI Cleaning Prompt & Fillers...",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentViolet
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentRose)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Listening to your voice...",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentRose
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Waveform Bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(48.dp)
            ) {
                val barCount = 18
                for (i in 0 until barCount) {
                    val factor = (Math.sin((i.toDouble() / barCount) * Math.PI) * (rmsDb + 2f) * 4f).coerceIn(4.0, 44.0).toFloat()
                    val height = if (isRephrasing) 8.dp else (factor * pulse1).dp.coerceAtLeast(6.dp)

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(height)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isRephrasing) listOf(AccentViolet, AccentPrimary)
                                    else listOf(AccentRose, AccentViolet)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time transcribed text preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (partialText.isNotBlank()) partialText else "Speak naturally (e.g., 'Um, so basically check the docker logs and fix the error')...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (partialText.isNotBlank()) TextPrimary else TextMuted,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Cancel", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }

                // AI Polish & Finish (Hero Action)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentViolet, AccentPrimary)
                                )
                            )
                            .clickable { onStopAndRephrase() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Clean & Polish",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stop & Polish",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentViolet
                    )
                }

                // Send As Is Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald.copy(alpha = 0.18f))
                            .border(1.dp, AccentEmerald.copy(alpha = 0.5f), CircleShape)
                            .clickable { onDirectSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send As Is",
                            tint = AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Send As Is", style = MaterialTheme.typography.labelSmall, color = AccentEmerald)
                }
            }
        }
    }
}
