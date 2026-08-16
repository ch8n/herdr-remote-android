package com.herdr.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.herdr.remote.data.model.Session
import com.herdr.remote.ui.theme.AccentPrimary
import com.herdr.remote.ui.theme.BorderHighlight
import com.herdr.remote.ui.theme.BorderSubtle
import com.herdr.remote.ui.theme.SurfaceDark
import com.herdr.remote.ui.theme.SurfaceElevated
import com.herdr.remote.ui.theme.TextMuted
import com.herdr.remote.ui.theme.TextPrimary
import com.herdr.remote.ui.theme.TextSecondary

@Composable
fun SessionTabsBar(
    sessions: List<Session>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onNewSessionClick: () -> Unit,
    onSyncTabs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(sessions, activeSessionId) {
        sessions.indexOfFirst { it.id == activeSessionId }
    }

    LaunchedEffect(activeSessionId, sessions.size) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = 0
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            val isActive = session.id == activeSessionId

            val bgCol by animateColorAsState(
                targetValue = if (isActive) AccentPrimary.copy(alpha = 0.22f) else SurfaceElevated,
                label = "tabBg"
            )
            val borderCol by animateColorAsState(
                targetValue = if (isActive) AccentPrimary else BorderSubtle,
                label = "tabBorder"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgCol)
                    .border(1.dp, borderCol, RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Selection Area (Emoji + Title)
                Row(
                    modifier = Modifier
                        .clickable { onSelectSession(session.id) }
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = if (sessions.size > 1) 4.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.agentProfile.avatarEmoji,
                        fontSize = 14.sp
                    )

                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isActive) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dedicated Close Button
                if (sessions.size > 1) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onCloseSession(session.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            tint = if (isActive) TextPrimary.copy(alpha = 0.8f) else TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // "+ New Tab" Button
        item(key = "btn_new_tab") {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp))
                    .clickable { onNewSessionClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = AccentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "New Tab",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                }
            }
        }

        // "Sync Tabs" Button (if callback provided)
        if (onSyncTabs != null) {
            item(key = "btn_sync_tabs") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { onSyncTabs() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Desktop Tabs",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
