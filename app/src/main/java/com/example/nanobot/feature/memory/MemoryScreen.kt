package com.example.nanobot.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.toConfidencePercent
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Back")
                    }
                }
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
                Text(
                    text = "Session Summaries",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (state.summaries.isEmpty()) {
                item {
                    Text(
                        text = "No summaries yet. Send a few messages and Nanobot will start building memory.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.summaries, key = { it.sessionId }) { summary ->
                    val isRebuilding = summary.sessionId in state.rebuildingSessionIds
                    MemoryCard(
                        title = "Session ${summary.sessionId.take(8)}",
                        body = summary.summary,
                        updatedAt = summary.updatedAt,
                        metadata = if (summary.sessionId == state.currentSessionId) {
                            buildSummaryMetadata(summary, currentSession = true)
                        } else {
                            buildSummaryMetadata(summary, currentSession = false)
                        },
                        actions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onRebuildSummary(summary.sessionId) }) {
                                    Text(if (isRebuilding) "Rebuilding..." else "Rebuild")
                                }
                                TextButton(onClick = { onDeleteSummary(summary.sessionId) }) {
                                    Text("Delete")
                                }
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (state.facts.isEmpty()) {
                item {
                    Text(
                        text = "No user facts captured yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        updatedAt = fact.updatedAt,
                        metadata = buildFactMetadata(fact, state.currentSessionId),
                        actions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onEditFact(fact.id) }) {
                                    Text("Edit")
                                }
                                TextButton(onClick = { onDeleteFact(fact.id) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    val editor = state.editor
    if (editor != null) {
        AlertDialog(
            onDismissRequest = onCancelFactEdit,
            title = { Text("Edit Memory Fact") },
            text = {
                OutlinedTextField(
                    value = editor.draftText,
                    onValueChange = onFactDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = onSaveFactEdit) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelFactEdit) {
                    Text("Cancel")
                }
            }
        )
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
    updatedAt: Long,
    metadata: String? = null,
    actions: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!metadata.isNullOrBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Updated ${updatedAt.toReadableTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actions?.invoke()
        }
    }
}

private fun Long.toReadableTime(): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}
