package com.example.nanobot.feature.chat

import com.example.nanobot.core.model.ChatMessage

internal enum class ToolMessageKind {
    WEB_SEARCH,
    WEB_FETCH,
    WORKSPACE_WRITE,
    WORKSPACE_READ,
    DELEGATION,
    MCP,
    SKILL,
    MEMORY,
    NOTIFY,
    OTHER
}

internal data class ToolMessagePresentation(
    val kind: ToolMessageKind,
    val badgeLabel: String,
    val previewPrefix: String
)

internal fun presentToolMessage(message: ChatMessage): ToolMessagePresentation {
    val toolName = message.toolName.orEmpty()
    return when {
        toolName == "web_search" -> ToolMessagePresentation(ToolMessageKind.WEB_SEARCH, "Web Search", "Search")
        toolName == "web_fetch" -> ToolMessagePresentation(ToolMessageKind.WEB_FETCH, "Web Fetch", "Fetch")
        toolName == "write_file" || toolName == "replace_in_file" -> ToolMessagePresentation(ToolMessageKind.WORKSPACE_WRITE, "Workspace Write", "Workspace")
        toolName == "read_file" || toolName == "list_workspace" || toolName == "search_workspace" -> ToolMessagePresentation(ToolMessageKind.WORKSPACE_READ, "Workspace Read", "Workspace")
        toolName == "delegate_task" -> ToolMessagePresentation(ToolMessageKind.DELEGATION, "Delegation", "Subtask")
        toolName.startsWith("activate_skill") || toolName == "read_skill_resource" -> ToolMessagePresentation(ToolMessageKind.SKILL, "Skill", "Skill")
        toolName.startsWith("mcp.") -> ToolMessagePresentation(ToolMessageKind.MCP, "MCP", "MCP")
        toolName == "memory_lookup" -> ToolMessagePresentation(ToolMessageKind.MEMORY, "Memory", "Memory")
        toolName == "notify_user" || toolName == "schedule_reminder" -> ToolMessagePresentation(ToolMessageKind.NOTIFY, "Reminder", "Reminder")
        else -> ToolMessagePresentation(ToolMessageKind.OTHER, "Tool", toolName.ifBlank { "Tool" })
    }
}

internal fun toolSummaryText(message: ChatMessage): String {
    val presentation = presentToolMessage(message)
    val preview = message.content.orEmpty()
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(140)
        .ifBlank { "Output available" }
    return "${presentation.previewPrefix}: $preview${if (message.content.orEmpty().length > 140) "..." else ""}"
}
