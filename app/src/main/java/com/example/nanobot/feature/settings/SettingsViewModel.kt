package com.example.nanobot.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanobot.core.ai.PromptPresetCatalog
import com.example.nanobot.core.mcp.McpAuthType
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.worker.WorkerSchedulingController
import com.example.nanobot.domain.repository.HeartbeatRepository
import com.example.nanobot.domain.repository.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsConfigStore,
    private val promptPresetCatalog: PromptPresetCatalog,
    private val skillRepository: SkillRepository,
    private val mcpRegistry: McpRegistry,
    private val heartbeatRepository: HeartbeatRepository,
    private val nanobotWorkerScheduler: WorkerSchedulingController
) : ViewModel() {
    private val uiStateInternal = MutableStateFlow(
        SettingsUiState(availablePresets = promptPresetCatalog.presets.map { it.id })
    )
    private val pendingUnlockConsentsState = MutableStateFlow(emptyList<com.example.nanobot.core.skills.PendingPhoneControlUnlockConsent>())

    val uiState: StateFlow<SettingsUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            refreshPendingUnlockConsents()
        }
        viewModelScope.launch {
            combine(
                settingsDataStore.configFlow,
                settingsDataStore.skillsDirectoryUriFlow,
                settingsDataStore.skillRootsFlow,
                settingsDataStore.trustProjectSkillsFlow,
                skillRepository.observeSkills(),
                skillRepository.observeDiscoveryIssues(),
                pendingUnlockConsentsState,
                mcpRegistry.observeServers(),
                mcpRegistry.observeCachedTools(),
                heartbeatRepository.observeHeartbeatEnabled(),
                heartbeatRepository.observeHeartbeatInstructions()
            ) { values ->
                val config = values[0] as com.example.nanobot.core.model.AgentConfig
                val skillsDirectoryUri = values[1] as String?
                val skillRoots = values[2] as List<String>
                val trustProjectSkills = values[3] as Boolean
                val skills = values[4] as List<com.example.nanobot.core.skills.SkillDefinition>
                val skillDiscoveryIssues = values[5] as List<com.example.nanobot.core.skills.SkillDiscoveryIssue>
                val pendingUnlockConsents = values[6] as List<com.example.nanobot.core.skills.PendingPhoneControlUnlockConsent>
                val mcpServers = values[7] as List<com.example.nanobot.core.mcp.McpServerDefinition>
                val mcpTools = values[8] as List<com.example.nanobot.core.mcp.McpToolDescriptor>
                val heartbeatEnabled = values[9] as Boolean
                val heartbeatInstructions = values[10] as String
                SettingsBaselineState(
                    config = config,
                    heartbeatEnabled = heartbeatEnabled,
                    heartbeatInstructions = heartbeatInstructions,
                    skills = skills,
                    skillsDirectoryUri = skillsDirectoryUri,
                    skillRoots = skillRoots,
                    trustProjectSkills = trustProjectSkills,
                    skillDiscoveryIssues = skillDiscoveryIssues,
                    pendingUnlockConsents = pendingUnlockConsents,
                    mcpServers = mcpServers,
                    mcpToolCounts = mcpTools.groupingBy { it.serverId }.eachCount()
                )
            }.collect { baseline ->
                applyBaseline(baseline)
            }
        }
    }

    fun onApiKeyChanged(value: String) = updateDraft { copy(apiKey = value) }

    fun onProviderChanged(value: String) = updateDraft { copy(providerType = value) }

    fun onBaseUrlChanged(value: String) = updateDraft { copy(baseUrl = value) }

    fun onModelChanged(value: String) = updateDraft { copy(model = value) }

    fun onMaxTokensChanged(value: String) = updateDraft { copy(maxTokens = value) }

    fun onMaxToolIterationsChanged(value: String) = updateDraft { copy(maxToolIterations = value) }

    fun onMemoryWindowChanged(value: String) = updateDraft { copy(memoryWindow = value) }

    fun onReasoningEffortChanged(value: String) = updateDraft { copy(reasoningEffort = value) }

    fun onEnableToolsChanged(value: Boolean) = updateDraft { copy(enableTools = value) }

    fun onEnableMemoryChanged(value: Boolean) = updateDraft { copy(enableMemory = value) }

    fun onEnableBackgroundWorkChanged(value: Boolean) = updateDraft { copy(enableBackgroundWork = value) }

    fun onHeartbeatEnabledChanged(value: Boolean) = updateDraft { copy(heartbeatEnabled = value) }

    fun onHeartbeatInstructionsChanged(value: String) = updateDraft { copy(heartbeatInstructions = value) }

    fun onWebSearchApiKeyChanged(value: String) = updateDraft { copy(webSearchApiKey = value) }

    fun onWebProxyChanged(value: String) = updateDraft { copy(webProxy = value) }

    fun onRestrictToWorkspaceChanged(value: Boolean) = updateDraft { copy(restrictToWorkspace = value) }

    fun onPresetChanged(value: String) = updateDraft { copy(presetId = value) }

    fun onSkillToggled(skillId: String, enabled: Boolean) = updateDraft {
        copy(
            skillOptions = skillOptions.map { option ->
                if (option.id == skillId) option.copy(checked = enabled) else option
            }
        )
    }

    fun onSkillDirectorySelected(uri: Uri) {
        viewModelScope.launch {
            settingsDataStore.addSkillRootUri(uri.toString())
            val result = skillRepository.importSkillsFromDirectory(uri)
            refreshPendingUnlockConsents()
            val status = buildSkillImportStatus("Import", result)
            updateUiState { current -> current.copy(draft = current.draft.copy(skillImportStatus = status)) }
        }
    }

    fun onSkillZipSelected(uri: Uri) {
        viewModelScope.launch {
            val result = skillRepository.importSkillsFromZip(uri)
            refreshPendingUnlockConsents()
            val status = buildSkillImportStatus("Zip import", result)
            updateUiState { current -> current.copy(draft = current.draft.copy(skillImportStatus = status)) }
        }
    }

    fun acceptPendingPhoneControlUnlockConsent(packageId: String) {
        viewModelScope.launch {
            val receipt = skillRepository.acceptPendingPhoneControlUnlockConsent(packageId)
            val status = if (receipt == null) {
                "Unlock consent could not be accepted because the pending request no longer exists."
            } else {
                "Accepted unlock consent for '${receipt.skillId}'. Hidden phone-control tools can now be unlocked by this skill."
            }
            refreshPendingUnlockConsents()
            val pendingConsents = pendingUnlockConsentsState.value
            updateUiState { current ->
                val updatedBaseline = current.baseline?.withPendingUnlockConsents(pendingConsents)
                current.copy(
                    baseline = updatedBaseline,
                    draft = (updatedBaseline?.toDraftState() ?: current.draft).copy(skillImportStatus = status),
                    isDirty = current.isDirty
                )
            }
        }
    }

    fun rejectPendingPhoneControlUnlockConsent(packageId: String) {
        viewModelScope.launch {
            skillRepository.rejectPendingPhoneControlUnlockConsent(packageId)
            refreshPendingUnlockConsents()
            val pendingConsents = pendingUnlockConsentsState.value
            updateUiState { current ->
                val updatedBaseline = current.baseline?.withPendingUnlockConsents(pendingConsents)
                current.copy(
                    baseline = updatedBaseline,
                    draft = (updatedBaseline?.toDraftState() ?: current.draft).copy(
                        skillImportStatus = "Rejected hidden phone-control unlock request."
                    ),
                    isDirty = current.isDirty
                )
            }
        }
    }

    fun onTrustProjectSkillsChanged(value: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setTrustProjectSkills(value)
            updateDraft { copy(trustProjectSkills = value) }
        }
    }

    fun onRemoveSkillRoot(uri: String) {
        viewModelScope.launch {
            settingsDataStore.removeSkillRootUri(uri)
            updateUiState { current ->
                current.copy(
                    draft = current.draft.copy(skillImportStatus = "Removed imported root."),
                    mcpStatus = current.mcpStatus
                )
            }
        }
    }

    fun onRemoveImportedSkill(skillId: String) {
        viewModelScope.launch {
            skillRepository.removeImportedSkill(skillId)
            updateUiState { current -> current.copy(draft = current.draft.copy(skillImportStatus = "Removed imported skill.")) }
        }
    }

    fun rescanImportedSkills() {
        viewModelScope.launch {
            val result = skillRepository.rescanImportedSkills()
            refreshPendingUnlockConsents()
            val status = if (result == null) {
                "No imported skills directory selected yet."
            } else {
                buildSkillImportStatus("Rescan", result)
            }
            updateUiState { current -> current.copy(draft = current.draft.copy(skillImportStatus = status)) }
        }
    }

    fun onDraftMcpLabelChanged(value: String) = updateDraft { copy(draftMcpLabel = value) }

    fun onDraftMcpEndpointChanged(value: String) = updateDraft { copy(draftMcpEndpoint = value) }

    fun onDraftMcpAuthTypeChanged(value: McpAuthType) = updateDraft { copy(draftMcpAuthType = value) }

    fun onDraftMcpAuthTokenChanged(value: String) = updateDraft { copy(draftMcpAuthToken = value) }

    fun onDraftMcpAuthHeaderNameChanged(value: String) = updateDraft { copy(draftMcpAuthHeaderName = value) }

    fun onDraftMcpAuthHeaderValueChanged(value: String) = updateDraft { copy(draftMcpAuthHeaderValue = value) }

    fun onDraftMcpConnectTimeoutChanged(value: String) = updateDraft { copy(draftMcpConnectTimeoutSeconds = value) }

    fun onDraftMcpReadTimeoutChanged(value: String) = updateDraft { copy(draftMcpReadTimeoutSeconds = value) }

    fun onDraftMcpWriteTimeoutChanged(value: String) = updateDraft { copy(draftMcpWriteTimeoutSeconds = value) }

    fun onDraftMcpCallTimeoutChanged(value: String) = updateDraft { copy(draftMcpCallTimeoutSeconds = value) }

    fun onDraftMcpMaxRetriesChanged(value: String) = updateDraft { copy(draftMcpMaxRetries = value) }

    fun onDraftMcpBackoffBaseMsChanged(value: String) = updateDraft { copy(draftMcpBackoffBaseMs = value) }

    fun onMcpServerToggled(serverId: String, enabled: Boolean) = updateDraft {
        copy(
            mcpServers = mcpServers.map { server ->
                if (server.id == serverId) server.copy(enabled = enabled) else server
            }
        )
    }

    fun addMcpServer() {
        val draft = uiStateInternal.value.draft
        val label = draft.draftMcpLabel.trim()
        val endpoint = draft.draftMcpEndpoint.trim()
        if (label.isBlank() || endpoint.isBlank()) {
            updateUiState { current -> current.copy(mcpStatus = "MCP server label and endpoint are required.") }
            return
        }

        updateDraft(
            status = null
        ) {
            copy(
                mcpServers = mcpServers + McpServerUiState(
                    id = UUID.randomUUID().toString(),
                    label = label,
                    endpoint = endpoint,
                    enabled = true,
                    discoveredToolCount = 0,
                    authType = draft.draftMcpAuthType,
                    authToken = draft.draftMcpAuthToken,
                    authHeaderName = draft.draftMcpAuthHeaderName,
                    authHeaderValue = draft.draftMcpAuthHeaderValue,
                    connectTimeoutSeconds = draft.draftMcpConnectTimeoutSeconds,
                    readTimeoutSeconds = draft.draftMcpReadTimeoutSeconds,
                    writeTimeoutSeconds = draft.draftMcpWriteTimeoutSeconds,
                    callTimeoutSeconds = draft.draftMcpCallTimeoutSeconds,
                    maxRetries = draft.draftMcpMaxRetries,
                    backoffBaseMs = draft.draftMcpBackoffBaseMs
                ),
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
                draftMcpBackoffBaseMs = "500"
            )
        }
    }

    fun removeMcpServer(serverId: String) = updateDraft(status = null) {
        copy(mcpServers = mcpServers.filterNot { it.id == serverId })
    }

    fun refreshMcpTools() {
        viewModelScope.launch {
            val draft = uiStateInternal.value.draft
            mcpRegistry.saveServers(draft.toMcpServerDefinitions())
            val result = mcpRegistry.refreshTools()
            val status = buildString {
                append("MCP refresh: ${result.discoveredToolCount} newly discovered tool(s), ${result.retainedToolCount} tool(s) available across ${result.enabledServerCount} enabled server(s)")
                append(". Health: ${result.healthyServerCount} healthy, ${result.degradedServerCount} degraded, ${result.unhealthyServerCount} unhealthy")
                if (result.errors.isNotEmpty()) {
                    append(". Errors: ")
                    append(result.errors.joinToString("; "))
                }
            }
            updateUiState { current -> current.copy(mcpStatus = status) }
        }
    }

    fun onSystemPromptChanged(value: String) = updateDraft { copy(systemPrompt = value) }

    fun saveSettings() {
        viewModelScope.launch {
            updateUiState { current -> current.copy(isSaving = true) }
            val draft = uiStateInternal.value.draft
            settingsDataStore.save(draft.toAgentConfig())
            mcpRegistry.saveServers(draft.toMcpServerDefinitions())
            heartbeatRepository.setHeartbeatEnabled(draft.heartbeatEnabled)
            heartbeatRepository.setHeartbeatInstructions(draft.heartbeatInstructions)
            nanobotWorkerScheduler.refreshScheduling()
            updateUiState { current -> current.copy(isSaving = false) }
        }
    }

    fun resetDraft() {
        val baseline = uiStateInternal.value.baseline ?: return
        updateUiState { current ->
            current.copy(
                draft = baseline.toDraftState(),
                isDirty = false,
                mcpStatus = null
            )
        }
    }

    private fun applyBaseline(baseline: SettingsBaselineState) {
        updateUiState { current ->
            if (current.isDirty) {
                current.copy(
                    baseline = baseline,
                    draft = current.draft.mergeSkillStateFrom(baseline.toDraftState())
                )
            } else {
                current.copy(
                    baseline = baseline,
                    draft = baseline.toDraftState().copy(skillImportStatus = current.draft.skillImportStatus),
                    isDirty = false,
                    mcpStatus = current.mcpStatus?.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    private fun updateDraft(
        status: String? = uiStateInternal.value.mcpStatus,
        transform: SettingsDraftState.() -> SettingsDraftState
    ) {
        updateUiState { current ->
            val updatedDraft = current.draft.transform()
            val baselineSnapshot = current.baseline?.toPersistedSnapshot()
            val draftSnapshot = updatedDraft.toPersistedSnapshot()
            current.copy(
                draft = updatedDraft,
                isDirty = baselineSnapshot == null || draftSnapshot != baselineSnapshot,
                mcpStatus = status
            )
        }
    }

    private fun updateUiState(transform: (SettingsUiState) -> SettingsUiState) {
        uiStateInternal.value = transform(uiStateInternal.value)
    }

    private fun buildSkillImportStatus(action: String, result: com.example.nanobot.core.skills.SkillImportResult): String {
        return buildString {
            append("$action complete: ${result.importedCount} imported, ${result.updatedCount} updated, ${result.skippedCount} skipped")
            if (result.duplicateCount > 0) {
                append(", ${result.duplicateCount} duplicate")
            }
            if (result.pendingConsentCount > 0) {
                append(", ${result.pendingConsentCount} waiting for unlock consent")
            }
            if (result.errors.isNotEmpty()) {
                append(". Errors: ")
                append(result.errors.joinToString("; "))
            }
        }
    }

    private suspend fun refreshPendingUnlockConsents() {
        pendingUnlockConsentsState.value = skillRepository.listPendingPhoneControlUnlockConsents()
    }
}

private fun SettingsDraftState.mergeSkillStateFrom(incoming: SettingsDraftState): SettingsDraftState {
    val currentChecked = skillOptions.associate { it.id to it.checked }
    return copy(
        skillOptions = incoming.skillOptions.map { skill ->
            skill.copy(checked = currentChecked[skill.id] ?: skill.checked)
        },
        skillsDirectoryUri = incoming.skillsDirectoryUri,
        skillRoots = incoming.skillRoots,
        trustProjectSkills = incoming.trustProjectSkills,
        skillDiagnostics = incoming.skillDiagnostics,
        pendingPhoneControlUnlockConsents = incoming.pendingPhoneControlUnlockConsents,
        skillImportStatus = skillImportStatus
    )
}
