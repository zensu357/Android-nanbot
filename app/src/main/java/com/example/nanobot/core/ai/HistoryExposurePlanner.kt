package com.example.nanobot.core.ai

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.MessageRole
import javax.inject.Inject

class HistoryExposurePlanner @Inject constructor() {
    fun plan(config: AgentConfig, history: List<ChatMessage>): List<ChatMessage> {
        return planWithDiagnostics(config, history).messages
    }

    fun planWithDiagnostics(config: AgentConfig, history: List<ChatMessage>): HistoryExposureResult {
        if (history.isEmpty()) {
            return HistoryExposureResult(
                messages = emptyList(),
                originalCount = 0,
                keptCount = 0,
                truncatedMessageCount = 0
            )
        }
        val totalBudget = historyBudget(config)
        val perMessageBudget = perMessageBudget(config)
        val selected = mutableListOf<ChatMessage>()
        var used = 0
        var truncatedCount = 0

        val protectedMessages = history.filter { it.protectedContext }
        protectedMessages.forEach { selected += it }
        used += protectedMessages.sumOf { estimateCost(it) }

        history.asReversed().forEach { message ->
            if (message.protectedContext) return@forEach
            val trimmed = trimMessage(message, perMessageBudget)
            if (trimmed.content != message.content) truncatedCount += 1
            val cost = estimateCost(trimmed)
            if (selected.isEmpty() || used + cost <= totalBudget) {
                selected += trimmed
                used += cost
            }
        }

        val chronological = selected.asReversed()
        val firstUserIndex = chronological.indexOfFirst { it.role == MessageRole.USER }
        val aligned = if (firstUserIndex > 0) chronological.drop(firstUserIndex) else chronological
        return HistoryExposureResult(
            messages = aligned,
            originalCount = history.size,
            keptCount = aligned.size,
            truncatedMessageCount = truncatedCount
        )
    }

    private fun trimMessage(message: ChatMessage, maxContentChars: Int): ChatMessage {
        val content = message.content ?: return message
        if (content.length <= maxContentChars) return message
        return message.copy(content = content.take(maxContentChars.coerceAtLeast(4) - 3).trimEnd() + "...")
    }

    private fun estimateCost(message: ChatMessage): Int {
        val contentLength = message.content?.length ?: 0
        val attachmentCost = message.attachments.fold(0) { total, attachment ->
            total + when (attachment.type) {
                AttachmentType.IMAGE -> 800
                AttachmentType.FILE -> 200
            }
        }
        val toolCost = if (message.toolCallsJson.isNullOrBlank()) 0 else 80
        return contentLength + attachmentCost + toolCost + 24
    }

    private fun historyBudget(config: AgentConfig): Int {
        return (config.maxTokens * 2)
            .coerceIn(1200, 6000)
    }

    private fun perMessageBudget(config: AgentConfig): Int {
        return (config.maxTokens / 3)
            .coerceIn(240, 1200)
    }
}
