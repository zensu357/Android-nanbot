package com.example.nanobot.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.skills.ActivatedSkillSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onMessageChange: (String) -> Unit,
    onAttachImage: (Uri) -> Unit,
    onAttachFile: (Uri) -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    onActivateSkill: (String) -> Unit,
    onDeactivateSkill: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleToolMessage: (String) -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onAttachImage)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onAttachFile)
    }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showSkillMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nanobot Chat")
                        Text(
                            text = state.sessionTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSessions) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "Sessions")
                    }
                    Box {
                        IconButton(onClick = { showSkillMenu = true }) {
                            Icon(imageVector = Icons.Default.Build, contentDescription = "Activate Skill")
                        }
                        DropdownMenu(
                            expanded = showSkillMenu,
                            onDismissRequest = { showSkillMenu = false }
                        ) {
                            if (state.availableSkills.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No enabled skills") },
                                    onClick = { showSkillMenu = false }
                                )
                            } else {
                                state.availableSkills.forEach { skill ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(skill.title)
                                                Text(
                                                    text = skill.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            showSkillMenu = false
                                            onActivateSkill(skill.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start with a prompt to build the first session.")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            expanded = message.id in state.expandedToolMessageIds,
                            onToggleToolMessage = onToggleToolMessage
                        )
                    }
                }
            }

            state.errorMessage?.let { errorText ->
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.statusText?.let { status ->
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.activeSkills.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.activeSkills.forEach { skill ->
                            val sourceColor = when (skill.source) {
                                ActivatedSkillSource.USER -> MaterialTheme.colorScheme.tertiaryContainer
                                ActivatedSkillSource.MODEL -> MaterialTheme.colorScheme.primaryContainer
                            }
                            val sourceContentColor = when (skill.source) {
                                ActivatedSkillSource.USER -> MaterialTheme.colorScheme.onTertiaryContainer
                                ActivatedSkillSource.MODEL -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = skill.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = sourceColor
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (skill.source == ActivatedSkillSource.USER) Icons.Default.Person else Icons.Default.Build,
                                                contentDescription = null,
                                                tint = sourceContentColor
                                            )
                                            Text(
                                                text = if (skill.source == ActivatedSkillSource.USER) "You" else "Auto",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = sourceContentColor
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { onDeactivateSkill(skill.name) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "x",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.pendingAttachments.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pending Attachments",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        state.pendingAttachments.forEach { attachment ->
                            AttachmentChip(
                                attachment = attachment,
                                onRemove = { onRemovePendingAttachment(attachment.id) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = !state.isRunning && !state.isSending,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Attach")
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Image") },
                            onClick = {
                                showAttachMenu = false
                                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File") },
                            onClick = {
                                showAttachMenu = false
                                fileLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = state.input,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Nanobot anything...") },
                    maxLines = 6
                )

                IconButton(
                    onClick = if (state.isRunning) onCancelClick else onSendClick,
                    enabled = !state.isCancelling,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    if (state.isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    } else if (state.isRunning) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Stop")
                    } else if (state.isSending) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    expanded: Boolean,
    onToggleToolMessage: (String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL
    val toolPresentation = if (isTool) presentToolMessage(message) else null
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (isTool) {
        MaterialTheme.colorScheme.toolContainerColor(toolPresentation?.kind ?: ToolMessageKind.OTHER)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clickable(enabled = isTool) { onToggleToolMessage(message.id) }
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                if (isTool && !expanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        toolPresentation?.let { presentation ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.toolBadgeColor(presentation.kind),
                                        CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = presentation.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Text(
                            text = (message.toolName ?: "tool_output").replace('_', ' '),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = message.createdAt.toReadableTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    return@Column
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    toolPresentation?.let { presentation ->
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.toolBadgeColor(presentation.kind),
                                    CircleShape
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = presentation.badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Text(
                        text = when {
                            isUser -> "You"
                            isTool -> "Tool"
                            else -> "Nanobot"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = message.createdAt.toReadableTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isTool) {
                    Text(
                        text = buildString {
                            append(message.toolName ?: "tool_output")
                            append(if (expanded) " • tap to collapse" else " • tap to expand")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = when {
                        isTool && !expanded -> AnnotatedString(toolSummaryText(message))
                        !message.content.isNullOrBlank() -> parseBoldText(message.content.orEmpty())
                        message.role == MessageRole.TOOL -> AnnotatedString("Tool result available")
                        else -> AnnotatedString("Working...")
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (message.attachments.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.attachments.forEach { attachment ->
                            AttachmentSummary(attachment = attachment)
                        }
                    }
                }
            }
        }
    }
}

private fun ColorScheme.toolContainerColor(kind: ToolMessageKind) = when (kind) {
    ToolMessageKind.WEB_SEARCH -> tertiaryContainer
    ToolMessageKind.WEB_FETCH -> tertiaryContainer.copy(alpha = 0.92f)
    ToolMessageKind.WORKSPACE_WRITE -> primaryContainer.copy(alpha = 0.88f)
    ToolMessageKind.WORKSPACE_READ -> secondaryContainer.copy(alpha = 0.92f)
    ToolMessageKind.DELEGATION -> tertiaryContainer.copy(alpha = 0.85f)
    ToolMessageKind.MCP -> primaryContainer.copy(alpha = 0.82f)
    ToolMessageKind.SKILL -> secondaryContainer.copy(alpha = 0.82f)
    ToolMessageKind.MEMORY -> secondaryContainer.copy(alpha = 0.88f)
    ToolMessageKind.NOTIFY -> tertiaryContainer.copy(alpha = 0.8f)
    ToolMessageKind.OTHER -> tertiaryContainer
}

private fun ColorScheme.toolBadgeColor(kind: ToolMessageKind) = when (kind) {
    ToolMessageKind.WEB_SEARCH -> tertiary
    ToolMessageKind.WEB_FETCH -> tertiary
    ToolMessageKind.WORKSPACE_WRITE -> primary
    ToolMessageKind.WORKSPACE_READ -> secondary
    ToolMessageKind.DELEGATION -> tertiary
    ToolMessageKind.MCP -> primary
    ToolMessageKind.SKILL -> secondary
    ToolMessageKind.MEMORY -> secondary
    ToolMessageKind.NOTIFY -> tertiary
    ToolMessageKind.OTHER -> primary
}

@Composable
private fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachmentSummary(attachment)
        TextButton(onClick = onRemove) {
            Text("Remove")
        }
    }
}

@Composable
private fun AttachmentSummary(attachment: Attachment) {
    val typeLabel = when (attachment.type) {
        AttachmentType.IMAGE -> "Image"
        AttachmentType.FILE -> "File"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(typeLabel, style = MaterialTheme.typography.labelMedium)
            }
            Column {
                Text(attachment.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${attachment.mimeType} • ${attachment.sizeBytes} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun parseBoldText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""\*\*(.*?)\*\*""")
        var lastIndex = 0
        pattern.findAll(text).forEach { matchResult ->
            // 添加匹配前的普通文本
            append(text.substring(lastIndex, matchResult.range.first))
            // 添加加粗文本（不含星号）
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matchResult.groupValues[1])
            }
            lastIndex = matchResult.range.last + 1
        }
        // 添加剩余文本
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun Long.toReadableTime(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}
