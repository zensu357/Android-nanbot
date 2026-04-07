package com.example.nanobot.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.toConfidencePercent
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.GlowButton
import com.example.nanobot.ui.components.NanobotTopBar
import com.example.nanobot.ui.components.nanobotTextFieldColors
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onEditFact: (String) -> Unit,
    onDeleteFact: (String) -> Unit,
    onDeleteSummary: (String) -> Unit,
    onRebuildSummary: (String) -> Unit,
    onFactDraftChange: (String) -> Unit,
    onSaveFactEdit: () -> Unit,
    onCancelFactEdit: () -> Unit,
    onBackClick: () -> Unit
) {
    val ext = NanobotTheme.extendedColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NanobotTopBar(
                title = "Memory",
                subtitle = "Summaries, facts, and confidence signals",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "Back",
                onNavigationClick = onBackClick
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 18.dp, highlighted = true) {
                    Text(
                        text = "Nanobot builds structured memory from conversation history to keep long-running sessions coherent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.textPrimary
                    )
                    Text(
                        text = "Review summaries, edit user facts, or rebuild memory after major conversation changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                }
            }
            item {
                Text(
                    text = "Session Summaries",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ext.textPrimary
                )
            }

            if (state.summaries.isEmpty()) {
                item {
                    Text(
                        text = "No summaries yet. Send a few messages and Nanobot will start building memory.",
                        color = ext.textSecondary
                    )
                }
            } else {
                items(state.summaries, key = { it.sessionId }) { summary ->
                    val isRebuilding = summary.sessionId in state.rebuildingSessionIds
                    MemoryCard(
                        title = "Session ${summary.sessionId.take(8)}",
                        body = summary.summary,
                        confidencePercent = summary.confidence.toConfidencePercent(),
                        accentColor = ext.neonDim,
                        updatedAt = summary.updatedAt,
                        metadata = if (summary.sessionId == state.currentSessionId) {
                            buildSummaryMetadata(summary, currentSession = true)
                        } else {
                            buildSummaryMetadata(summary, currentSession = false)
                        },
                        actions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlowButton(
                                    text = if (isRebuilding) "Rebuilding..." else "Rebuild",
                                    onClick = { onRebuildSummary(summary.sessionId) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isRebuilding
                                )
                                GlowButton(
                                    text = "Delete",
                                    onClick = { onDeleteSummary(summary.sessionId) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "User Facts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ext.textPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.facts.isEmpty()) {
                item {
                    Text(
                        text = "No user facts captured yet.",
                        color = ext.textSecondary
                    )
                }
            } else {
                items(state.facts, key = { it.id }) { fact ->
                    MemoryCard(
                        title = when {
                            fact.sourceSessionId == state.currentSessionId -> "Current Session Fact"
                            fact.sourceSessionId != null -> "Fact from ${fact.sourceSessionId.take(8)}"
                            else -> "User Fact"
                        },
                        body = fact.fact,
                        confidencePercent = fact.confidence.toConfidencePercent(),
                        accentColor = ext.neon,
                        updatedAt = fact.updatedAt,
                        metadata = buildFactMetadata(fact, state.currentSessionId),
                        actions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlowButton(
                                    text = "Edit",
                                    onClick = { onEditFact(fact.id) },
                                    modifier = Modifier.weight(1f)
                                )
                                GlowButton(
                                    text = "Delete",
                                    onClick = { onDeleteFact(fact.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = onCancelFactEdit,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NanobotShapes.BottomSheet
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Memory Fact",
                    style = MaterialTheme.typography.titleLarge,
                    color = ext.textPrimary
                )
                OutlinedTextField(
                    value = editor.draftText,
                    onValueChange = onFactDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = nanobotTextFieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlowButton(
                        text = "Cancel",
                        onClick = onCancelFactEdit,
                        modifier = Modifier.weight(1f)
                    )
                    GlowButton(
                        text = "Save",
                        onClick = onSaveFactEdit,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun buildSummaryMetadata(summary: MemorySummary, currentSession: Boolean): String {
    val parts = mutableListOf<String>()
    if (currentSession) {
        parts += "Current session"
    }
    parts += "${summary.sourceMessageCount} messages"
    parts += "Confidence ${summary.confidence.toConfidencePercent()}%"
    summary.provenance.messageIds.takeIf { it.isNotEmpty() }?.let { parts += "Evidence msgs ${it.joinToString(limit = 2)}" }
    summary.provenance.evidenceExcerpt?.takeIf { it.isNotBlank() }?.let { parts += it.take(100) }
    return parts.joinToString(" • ")
}

private fun buildFactMetadata(fact: MemoryFact, currentSessionId: String?): String {
    val parts = mutableListOf<String>()
    fact.sourceSessionId?.let { sessionId ->
        parts += if (sessionId == currentSessionId) {
            "Current session"
        } else {
            "Session ${sessionId.take(8)}"
        }
    }
    parts += "Confidence ${fact.confidence.toConfidencePercent()}%"
    fact.provenance.messageIds.takeIf { it.isNotEmpty() }?.let { parts += "Evidence msgs ${it.joinToString(limit = 2)}" }
    fact.provenance.evidenceExcerpt?.takeIf { it.isNotBlank() }?.let { parts += it.take(100) }
    return parts.joinToString(" • ")
}

@Composable
private fun MemoryCard(
    title: String,
    body: String,
    confidencePercent: Int?,
    accentColor: Color,
    updatedAt: Long,
    metadata: String? = null,
    actions: @Composable (() -> Unit)? = null
) {
    val ext = NanobotTheme.extendedColors

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(3.dp)
                    .height(48.dp)
                    .background(accentColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.textPrimary
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.textPrimary
                )
                confidencePercent?.let { percent ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Confidence",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textTertiary
                        )
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                            color = ext.neon,
                            trackColor = ext.glassBorder
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.neon
                        )
                    }
                }
                if (!metadata.isNullOrBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                }
                Text(
                    text = "Updated ${updatedAt.toReadableTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
                actions?.invoke()
            }
        }
    }
}

private fun Long.toReadableTime(): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}
