package com.example.nanobot.feature.settings

import com.example.nanobot.core.mcp.McpAuthConfig
import com.example.nanobot.core.mcp.McpAuthType
import com.example.nanobot.core.mcp.McpHealthStatus
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpServerHealth
import com.example.nanobot.core.skills.SkillDiagnosticKind
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.skills.PendingPhoneControlUnlockConsent
import com.example.nanobot.core.model.ProviderType
import com.example.nanobot.core.skills.SkillDiscoveryIssue
import com.example.nanobot.core.skills.SkillDefinition

data class SkillOptionUiState(
    val id: String,
    val title: String,
    val description: String,
    val checked: Boolean,
    val tags: List<String> = emptyList(),
    val isImported: Boolean = false,
    val originLabel: String? = null,
    val scopeLabel: String? = null,
    val trusted: Boolean = true
)

data class SkillRootUiState(
    val uri: String,
    val label: String,
    val trusted: Boolean = true
)

data class SkillDiagnosticItemUiState(
    val kind: SkillDiagnosticKind,
    val scopeLabel: String,
    val message: String,
    val levelLabel: String
)

data class SkillDiagnosticSectionUiState(
    val title: String,
    val items: List<SkillDiagnosticItemUiState>
)

data class PendingPhoneControlUnlockConsentUiState(
    val packageId: String,
    val skillId: String,
    val skillTitle: String,
    val unlockProfiles: List<String>,
    val consentTitle: String,
    val consentVersion: String,
    val consentText: String,
    val signerKeyId: String,
    val signerAlgorithm: String,
    val createdAtEpochMs: Long
)

data class McpServerUiState(
    val id: String,
    val label: String,
    val endpoint: String,
    val enabled: Boolean,
    val discoveredToolCount: Int = 0,
    val authType: McpAuthType = McpAuthType.NONE,
    val authToken: String = "",
    val authHeaderName: String = "X-API-Key",
    val authHeaderValue: String = "",
    val connectTimeoutSeconds: String = "30",
    val readTimeoutSeconds: String = "60",
    val writeTimeoutSeconds: String = "30",
    val callTimeoutSeconds: String = "90",
    val maxRetries: String = "2",
    val backoffBaseMs: String = "500",
    val healthStatus: McpHealthStatus = McpHealthStatus.UNKNOWN,
    val healthError: String? = null,
    val consecutiveFailures: Int = 0
)

data class SettingsDraftState(
    val providerType: String = ProviderType.OPENAI_COMPATIBLE.wireValue,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val maxTokens: String = "4096",
    val maxToolIterations: String = "8",
    val memoryWindow: String = "100",
    val reasoningEffort: String = "",
    val enableTools: Boolean = true,
    val enableMemory: Boolean = true,
    val enableBackgroundWork: Boolean = true,
    val heartbeatEnabled: Boolean = true,
    val heartbeatInstructions: String = "",
    val webSearchApiKey: String = "",
    val webProxy: String = "",
    val restrictToWorkspace: Boolean = false,
    val presetId: String = "assistant_default",
    val skillOptions: List<SkillOptionUiState> = emptyList(),
    val skillsDirectoryUri: String? = null,
    val skillRoots: List<SkillRootUiState> = emptyList(),
    val trustProjectSkills: Boolean = false,
    val skillImportStatus: String? = null,
    val skillDiagnostics: List<SkillDiagnosticSectionUiState> = emptyList(),
    val pendingPhoneControlUnlockConsents: List<PendingPhoneControlUnlockConsentUiState> = emptyList(),
    val mcpServers: List<McpServerUiState> = emptyList(),
    val draftMcpLabel: String = "",
    val draftMcpEndpoint: String = "",
    val draftMcpAuthType: McpAuthType = McpAuthType.NONE,
    val draftMcpAuthToken: String = "",
    val draftMcpAuthHeaderName: String = "X-API-Key",
    val draftMcpAuthHeaderValue: String = "",
    val draftMcpConnectTimeoutSeconds: String = "30",
    val draftMcpReadTimeoutSeconds: String = "60",
    val draftMcpWriteTimeoutSeconds: String = "30",
    val draftMcpCallTimeoutSeconds: String = "90",
    val draftMcpMaxRetries: String = "2",
    val draftMcpBackoffBaseMs: String = "500",
    val systemPrompt: String = ""
)

data class SettingsBaselineState(
    val config: AgentConfig,
    val heartbeatEnabled: Boolean,
    val heartbeatInstructions: String,
    val skills: List<SkillDefinition>,
    val skillsDirectoryUri: String?,
    val skillRoots: List<String>,
    val trustProjectSkills: Boolean,
    val skillDiscoveryIssues: List<SkillDiscoveryIssue>,
    val pendingUnlockConsents: List<PendingPhoneControlUnlockConsent> = emptyList(),
    val mcpServers: List<McpServerDefinition>,
    val mcpToolCounts: Map<String, Int>
)

data class PersistedSettingsSnapshot(
    val agentConfig: AgentConfig,
    val heartbeatEnabled: Boolean,
    val heartbeatInstructions: String,
    val mcpServers: List<McpServerDefinition>
)

data class SettingsUiState(
    val draft: SettingsDraftState = SettingsDraftState(),
    val baseline: SettingsBaselineState? = null,
    val availablePresets: List<String> = emptyList(),
    val mcpStatus: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false
) {
    val providerType: String get() = draft.providerType
    val apiKey: String get() = draft.apiKey
    val baseUrl: String get() = draft.baseUrl
    val model: String get() = draft.model
    val maxTokens: String get() = draft.maxTokens
    val maxToolIterations: String get() = draft.maxToolIterations
    val memoryWindow: String get() = draft.memoryWindow
    val reasoningEffort: String get() = draft.reasoningEffort
    val enableTools: Boolean get() = draft.enableTools
    val enableMemory: Boolean get() = draft.enableMemory
    val enableBackgroundWork: Boolean get() = draft.enableBackgroundWork
    val heartbeatEnabled: Boolean get() = draft.heartbeatEnabled
    val heartbeatInstructions: String get() = draft.heartbeatInstructions
    val webSearchApiKey: String get() = draft.webSearchApiKey
    val webProxy: String get() = draft.webProxy
    val restrictToWorkspace: Boolean get() = draft.restrictToWorkspace
    val presetId: String get() = draft.presetId
    val skillOptions: List<SkillOptionUiState> get() = draft.skillOptions
    val skillsDirectoryUri: String? get() = draft.skillsDirectoryUri
    val skillRoots: List<SkillRootUiState> get() = draft.skillRoots
    val trustProjectSkills: Boolean get() = draft.trustProjectSkills
    val skillImportStatus: String? get() = draft.skillImportStatus
    val skillDiagnostics: List<SkillDiagnosticSectionUiState> get() = draft.skillDiagnostics
    val pendingPhoneControlUnlockConsents: List<PendingPhoneControlUnlockConsentUiState> get() = draft.pendingPhoneControlUnlockConsents
    val mcpServers: List<McpServerUiState> get() = draft.mcpServers
    val draftMcpLabel: String get() = draft.draftMcpLabel
    val draftMcpEndpoint: String get() = draft.draftMcpEndpoint
    val draftMcpAuthType: McpAuthType get() = draft.draftMcpAuthType
    val draftMcpAuthToken: String get() = draft.draftMcpAuthToken
    val draftMcpAuthHeaderName: String get() = draft.draftMcpAuthHeaderName
    val draftMcpAuthHeaderValue: String get() = draft.draftMcpAuthHeaderValue
    val draftMcpConnectTimeoutSeconds: String get() = draft.draftMcpConnectTimeoutSeconds
    val draftMcpReadTimeoutSeconds: String get() = draft.draftMcpReadTimeoutSeconds
    val draftMcpWriteTimeoutSeconds: String get() = draft.draftMcpWriteTimeoutSeconds
    val draftMcpCallTimeoutSeconds: String get() = draft.draftMcpCallTimeoutSeconds
    val draftMcpMaxRetries: String get() = draft.draftMcpMaxRetries
    val draftMcpBackoffBaseMs: String get() = draft.draftMcpBackoffBaseMs
    val systemPrompt: String get() = draft.systemPrompt
}

fun SettingsBaselineState.toDraftState(): SettingsDraftState {
    return SettingsDraftState(
        providerType = config.providerType.wireValue,
        apiKey = config.apiKey,
        baseUrl = config.baseUrl,
        model = config.model,
        maxTokens = config.maxTokens.toString(),
        maxToolIterations = config.maxToolIterations.toString(),
        memoryWindow = config.memoryWindow.toString(),
        reasoningEffort = config.reasoningEffort.orEmpty(),
        enableTools = config.enableTools,
        enableMemory = config.enableMemory,
        enableBackgroundWork = config.enableBackgroundWork,
        heartbeatEnabled = heartbeatEnabled,
        heartbeatInstructions = heartbeatInstructions,
        webSearchApiKey = config.webSearchApiKey,
        webProxy = config.webProxy,
        restrictToWorkspace = config.restrictToWorkspace,
        presetId = config.presetId,
        skillOptions = skills.map { skill ->
            SkillOptionUiState(
                id = skill.id,
                title = skill.title,
                description = skill.description,
                checked = skill.id in config.enabledSkillIds,
                tags = skill.tags,
                isImported = skill.isImported,
                originLabel = skill.originLabel,
                scopeLabel = skill.scope.name.lowercase(),
                trusted = skill.isTrusted
            )
        },
        skillsDirectoryUri = skillsDirectoryUri,
        skillRoots = skillRoots.map { uri -> SkillRootUiState(uri = uri, label = uri, trusted = true) },
        trustProjectSkills = trustProjectSkills,
        skillImportStatus = null,
        skillDiagnostics = skillDiscoveryIssues
            .groupBy { it.kind }
            .toSortedMap(compareBy { diagnosticKindOrder(it) })
            .map { (kind, issues) ->
                SkillDiagnosticSectionUiState(
                    title = diagnosticKindTitle(kind),
                    items = issues.map { issue ->
                        SkillDiagnosticItemUiState(
                            kind = issue.kind,
                            scopeLabel = issue.scope.name.lowercase(),
                            message = issue.message,
                            levelLabel = issue.level.name.lowercase()
                        )
                    }.sortedWith(compareBy({ it.scopeLabel }, { it.message.lowercase() }))
                )
            },
        pendingPhoneControlUnlockConsents = pendingUnlockConsents.map { it.toUiState() },
        mcpServers = mcpServers.map { server ->
            McpServerUiState(
                id = server.id,
                label = server.label,
                endpoint = server.endpoint,
                enabled = server.enabled,
                discoveredToolCount = mcpToolCounts[server.id] ?: 0,
                authType = server.auth.type,
                authToken = server.auth.token,
                authHeaderName = server.auth.headerName,
                authHeaderValue = server.auth.headerValue,
                connectTimeoutSeconds = server.connectTimeoutSeconds.toString(),
                readTimeoutSeconds = server.readTimeoutSeconds.toString(),
                writeTimeoutSeconds = server.writeTimeoutSeconds.toString(),
                callTimeoutSeconds = server.callTimeoutSeconds.toString(),
                maxRetries = server.maxRetries.toString(),
                backoffBaseMs = server.backoffBaseMs.toString(),
                healthStatus = server.health.status,
                healthError = server.health.lastError,
                consecutiveFailures = server.health.consecutiveFailures
            )
        },
        draftMcpLabel = "",
        draftMcpEndpoint = "",
        draftMcpAuthType = McpAuthType.NONE,
        draftMcpAuthToken = "",
        draftMcpAuthHeaderName = "X-API-Key",
        draftMcpAuthHeaderValue = "",
        draftMcpConnectTimeoutSeconds = "30",
        draftMcpReadTimeoutSeconds = "60",
        draftMcpWriteTimeoutSeconds = "30",
        draftMcpCallTimeoutSeconds = "90",
        draftMcpMaxRetries = "2",
        draftMcpBackoffBaseMs = "500",
        systemPrompt = config.systemPrompt
    )
}

fun PendingPhoneControlUnlockConsent.toUiState(): PendingPhoneControlUnlockConsentUiState {
    return PendingPhoneControlUnlockConsentUiState(
        packageId = packageId,
        skillId = skillId,
        skillTitle = skillTitle,
        unlockProfiles = unlockProfiles,
        consentTitle = consentTitle,
        consentVersion = consentVersion,
        consentText = consentText,
        signerKeyId = signerKeyId,
        signerAlgorithm = signerAlgorithm,
        createdAtEpochMs = createdAtEpochMs
    )
}

private fun diagnosticKindTitle(kind: SkillDiagnosticKind): String {
    return when (kind) {
        SkillDiagnosticKind.LOADED -> "Loaded"
        SkillDiagnosticKind.OVERRIDDEN -> "Overridden"
        SkillDiagnosticKind.SKIPPED -> "Skipped"
        SkillDiagnosticKind.BLOCKED -> "Blocked"
        SkillDiagnosticKind.WARNING -> "Warnings"
        SkillDiagnosticKind.ERROR -> "Errors"
    }
}

private fun diagnosticKindOrder(kind: SkillDiagnosticKind): Int {
    return when (kind) {
        SkillDiagnosticKind.ERROR -> 0
        SkillDiagnosticKind.BLOCKED -> 1
        SkillDiagnosticKind.WARNING -> 2
        SkillDiagnosticKind.OVERRIDDEN -> 3
        SkillDiagnosticKind.SKIPPED -> 4
        SkillDiagnosticKind.LOADED -> 5
    }
}

fun SettingsBaselineState.withPendingUnlockConsents(
    pendingUnlockConsents: List<PendingPhoneControlUnlockConsent>
): SettingsBaselineState {
    return copy(pendingUnlockConsents = pendingUnlockConsents)
}

fun SettingsDraftState.toAgentConfig(): AgentConfig {
    return AgentConfig(
        providerType = ProviderType.from(providerType),
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        maxTokens = maxTokens.toIntOrNull() ?: 4096,
        maxToolIterations = maxToolIterations.toIntOrNull() ?: 8,
        memoryWindow = memoryWindow.toIntOrNull() ?: 100,
        reasoningEffort = reasoningEffort.ifBlank { null },
        enableTools = enableTools,
        enableMemory = enableMemory,
        enableBackgroundWork = enableBackgroundWork,
        webSearchApiKey = webSearchApiKey,
        webProxy = webProxy,
        restrictToWorkspace = restrictToWorkspace,
        presetId = presetId,
        enabledSkillIds = skillOptions.filter { it.checked }.map { it.id },
        systemPrompt = systemPrompt
    )
}

fun SettingsDraftState.toMcpServerDefinitions(): List<McpServerDefinition> {
    return mcpServers.map { server ->
        McpServerDefinition(
            id = server.id,
            label = server.label,
            endpoint = server.endpoint,
            enabled = server.enabled,
            auth = McpAuthConfig(
                type = server.authType,
                token = server.authToken,
                headerName = server.authHeaderName,
                headerValue = server.authHeaderValue
            ),
            connectTimeoutSeconds = server.connectTimeoutSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 30,
            readTimeoutSeconds = server.readTimeoutSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 60,
            writeTimeoutSeconds = server.writeTimeoutSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 30,
            callTimeoutSeconds = server.callTimeoutSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 90,
            maxRetries = server.maxRetries.toIntOrNull()?.coerceAtLeast(0) ?: 2,
            backoffBaseMs = server.backoffBaseMs.toLongOrNull()?.coerceAtLeast(0L) ?: 500L,
            health = McpServerHealth(
                status = server.healthStatus,
                consecutiveFailures = server.consecutiveFailures,
                lastError = server.healthError
            )
        )
    }
}

fun SettingsBaselineState.toPersistedSnapshot(): PersistedSettingsSnapshot {
    return PersistedSettingsSnapshot(
        agentConfig = config,
        heartbeatEnabled = heartbeatEnabled,
        heartbeatInstructions = heartbeatInstructions,
        mcpServers = mcpServers
    )
}

fun SettingsDraftState.toPersistedSnapshot(): PersistedSettingsSnapshot {
    return PersistedSettingsSnapshot(
        agentConfig = toAgentConfig(),
        heartbeatEnabled = heartbeatEnabled,
        heartbeatInstructions = heartbeatInstructions,
        mcpServers = toMcpServerDefinitions()
    )
}
