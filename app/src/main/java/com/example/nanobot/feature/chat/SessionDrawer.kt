package com.example.nanobot.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.GlowButton
import com.example.nanobot.ui.theme.NanobotTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SessionDrawer(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTools: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val ext = NanobotTheme.extendedColors
    var pendingDeleteSession by remember { mutableStateOf<ChatSession?>(null) }
    val groupedSessions = remember(sessions) {
        sessions
            .sortedByDescending { it.updatedAt }
            .groupBy { categorizeDate(it.updatedAt) }
            .entries
            .toList()
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Sessions",
            style = MaterialTheme.typography.titleLarge,
            color = ext.textPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Jump between conversations or start a fresh chat.",
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        GlowButton(
            text = "New Chat",
            onClick = onCreateSession,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                innerPadding = 12.dp,
                borderColor = ext.errorRed.copy(alpha = 0.35f)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.errorRed
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (groupedSessions.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.example.nanobot.ui.theme.NanobotShapes.CardSmall,
                        innerPadding = 14.dp
                    ) {
                        Text(
                            text = "No sessions yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ext.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Create a new chat to start building a conversation history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textSecondary
                        )
                    }
                }
            } else {
                groupedSessions.forEach { (dateCategory, sessionsInGroup) ->
                    item(dateCategory) {
                        Text(
                            text = dateCategory,
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.textTertiary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                    items(sessionsInGroup, key = { it.id }) { session ->
                        SessionDrawerItem(
                            session = session,
                            isCurrent = session.id == currentSessionId,
                            onClick = { onSelectSession(session.id) },
                            onDeleteClick = { pendingDeleteSession = session }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = ext.glassBorder,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        DrawerBottomLink(label = "Settings", onClick = onOpenSettings)
        DrawerBottomLink(label = "Memory", onClick = onOpenMemory)
        DrawerBottomLink(label = "Tools", onClick = onOpenTools)
    }

    pendingDeleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = { Text("Delete session?") },
            text = { Text("This removes the session and its related conversation history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session.id)
                        pendingDeleteSession = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ext.errorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionDrawerItem(
    session: ChatSession,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val ext = NanobotTheme.extendedColors
    val selectedBackground by animateColorAsState(
        targetValue = if (isCurrent) ext.neonDim.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(220),
        label = "sessionDrawerItemBg"
    )
    val selectedAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.72f,
        animationSpec = tween(220),
        label = "sessionDrawerItemAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                selectedBackground,
                shape = com.example.nanobot.ui.theme.NanobotShapes.CardSmall
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(width = 3.dp, height = 36.dp)
                    .background(ext.neon, shape = androidx.compose.foundation.shape.CircleShape)
            )
        } else {
            Box(modifier = Modifier.size(width = 11.dp, height = 1.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) ext.neon else ext.textPrimary.copy(alpha = selectedAlpha),
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                text = session.updatedAt.toDrawerTime(),
                style = MaterialTheme.typography.labelSmall,
                color = ext.textTertiary
            )
        }

        IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = ext.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DrawerBottomLink(label: String, onClick: () -> Unit) {
    val ext = NanobotTheme.extendedColors

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = ext.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

private fun categorizeDate(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val now = Calendar.getInstance()
    cal.timeInMillis = timestamp

    return when {
        isSameDay(cal, now) -> "Today"
        isYesterday(cal, now) -> "Yesterday"
        isWithinDays(cal, now, 7) -> "Previous 7 Days"
        else -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(a: Calendar, b: Calendar): Boolean {
    val yesterday = b.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(a, yesterday)
}

private fun isWithinDays(a: Calendar, b: Calendar, days: Int): Boolean {
    val threshold = b.clone() as Calendar
    threshold.add(Calendar.DAY_OF_YEAR, -days)
    return a.after(threshold)
}

private fun Long.toDrawerTime(): String {
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(this))
}
