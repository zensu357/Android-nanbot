package com.example.nanobot.core.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemPromptBuildResult(
    val prompt: String,
    val sectionTitles: List<String>,
    val catalogSkillIds: List<String>,
    val expandedSkillIds: List<String>
)

data class MemoryExposureResult(
    val context: String?,
    val summaryIncluded: Boolean,
    val sessionFactCount: Int,
    val longTermFactCount: Int
)

data class RuntimeContextResult(
    val context: String,
    val diagnosticsEnabled: Boolean
)

data class HistoryExposureResult(
    val messages: List<com.example.nanobot.core.model.ChatMessage>,
    val originalCount: Int,
    val keptCount: Int,
    val truncatedMessageCount: Int
)

data class PromptDiagnosticsSnapshot(
    val systemPromptChars: Int,
    val systemPromptSections: List<String>,
    val catalogSkillIds: List<String>,
    val expandedSkillIds: List<String>,
    val memorySummaryIncluded: Boolean,
    val memorySessionFactCount: Int,
    val memoryLongTermFactCount: Int,
    val runtimeDiagnosticsEnabled: Boolean,
    val runtimeContextChars: Int,
    val historyOriginalCount: Int,
    val historyKeptCount: Int,
    val historyTruncatedMessageCount: Int
)

@Singleton
class PromptDiagnosticsStore @Inject constructor() {
    private val snapshotState = MutableStateFlow<PromptDiagnosticsSnapshot?>(null)
    val snapshot: StateFlow<PromptDiagnosticsSnapshot?> = snapshotState.asStateFlow()

    fun publish(snapshot: PromptDiagnosticsSnapshot) {
        snapshotState.value = snapshot
    }
}
