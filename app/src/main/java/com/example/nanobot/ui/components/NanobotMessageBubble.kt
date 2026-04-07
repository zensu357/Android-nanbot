package com.example.nanobot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.feature.chat.ToolMessageKind
import com.example.nanobot.feature.chat.presentToolMessage
import com.example.nanobot.feature.chat.toolSummaryText
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NanobotMessageBubble(
    message: ChatMessage,
    expanded: Boolean,
    onToggleToolMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL
    val ext = NanobotTheme.extendedColors
    val toolPresentation = if (isTool) presentToolMessage(message) else null

    val bubbleColor = when {
        isUser -> ext.userBubble
        isTool -> ext.toolBubble
        else -> ext.assistantBubble
    }
    val bubbleShape = when {
        isUser -> NanobotShapes.UserBubble
        isTool -> NanobotShapes.ToolBubble
        else -> NanobotShapes.AssistantBubble
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.createdAt.toReadableTime(),
            style = MaterialTheme.typography.labelSmall,
            color = ext.textTertiary,
            modifier = Modifier.padding(
                start = if (isUser) 0.dp else 4.dp,
                end = if (isUser) 4.dp else 0.dp,
                bottom = 2.dp
            )
        )

        Row(
            modifier = if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier
                    .then(if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth())
                    .clickable(enabled = isTool) { onToggleToolMessage(message.id) },
                shape = bubbleShape,
                color = bubbleColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    if (!isUser && !isTool) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp, end = 12.dp)
                                .width(3.dp)
                                .background(ext.neonDim, CircleShape)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isTool) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                toolPresentation?.let { presentation ->
                                    Surface(
                                        shape = NanobotShapes.Chip,
                                        color = toolBadgeColor(presentation.kind)
                                    ) {
                                        Text(
                                            text = presentation.badgeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.textPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = (message.toolName ?: "tool_output").replace('_', ' '),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ext.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = if (expanded) "Tap to collapse" else "Tap to expand",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.textTertiary
                                )
                            }
                        }

                        Text(
                            text = when {
                                isTool && !expanded -> AnnotatedString(toolSummaryText(message))
                                !message.content.isNullOrBlank() -> parseBoldText(message.content.orEmpty())
                                isTool -> AnnotatedString("Tool result available")
                                else -> AnnotatedString("Working...")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = ext.textPrimary
                        )

                        AnimatedVisibility(
                            visible = message.attachments.isNotEmpty(),
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                message.attachments.forEach { attachment ->
                                    AttachmentSummary(attachment = attachment)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentSummary(attachment: Attachment) {
    val ext = NanobotTheme.extendedColors
    val typeLabel = when (attachment.type) {
        AttachmentType.IMAGE -> "Image"
        AttachmentType.FILE -> "File"
    }

    GlassCard(
        shape = NanobotShapes.CardSmall,
        innerPadding = 10.dp,
        backgroundColor = ext.glassOverlay.copy(alpha = 0.8f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = NanobotShapes.Chip,
                color = ext.neonDim.copy(alpha = 0.35f)
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = ext.neon,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Column {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.textPrimary
                )
                Text(
                    text = "${attachment.mimeType} • ${attachment.sizeBytes} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
            }
        }
    }
}

private fun toolBadgeColor(kind: ToolMessageKind): Color {
    return when (kind) {
        ToolMessageKind.WEB_SEARCH -> Color(0xFF275D56)
        ToolMessageKind.WEB_FETCH -> Color(0xFF2A6467)
        ToolMessageKind.WORKSPACE_WRITE -> Color(0xFF37506B)
        ToolMessageKind.WORKSPACE_READ -> Color(0xFF334246)
        ToolMessageKind.DELEGATION -> Color(0xFF58446A)
        ToolMessageKind.MCP -> Color(0xFF355A7C)
        ToolMessageKind.SKILL -> Color(0xFF46613A)
        ToolMessageKind.MEMORY -> Color(0xFF555733)
        ToolMessageKind.NOTIFY -> Color(0xFF7A5334)
        ToolMessageKind.OTHER -> Color(0xFF42505C)
    }
}

private fun parseBoldText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""\*\*(.*?)\*\*""")
        var lastIndex = 0
        pattern.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matchResult.groupValues[1])
            }
            lastIndex = matchResult.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun Long.toReadableTime(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}
