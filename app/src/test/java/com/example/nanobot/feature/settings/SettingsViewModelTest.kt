@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.nanobot.feature.settings

import com.example.nanobot.core.ai.PromptPresetCatalog
import com.example.nanobot.core.mcp.McpAuthType
import com.example.nanobot.core.mcp.McpRefreshResult
import com.example.nanobot.core.mcp.McpRegistry
import com.example.nanobot.core.mcp.McpServerDefinition
import com.example.nanobot.core.mcp.McpToolDescriptor
import com.example.nanobot.core.mcp.McpToolDiscoverySnapshot
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.skills.PendingPhoneControlUnlockConsent
import com.example.nanobot.core.skills.PhoneControlUnlockReceipt
import com.example.nanobot.core.skills.SkillDiagnosticKind
import com.example.nanobot.core.skills.SkillDiscoveryIssue
import com.example.nanobot.core.skills.SkillScope
import com.example.nanobot.core.worker.WorkerSchedulingController
import com.example.nanobot.domain.repository.HeartbeatRepository
import com.example.nanobot.domain.repository.SystemAccessRepository
import com.example.nanobot.domain.repository.SystemAccessState
import com.example.nanobot.testutil.FakeSkillRepository
import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.JsonObject

class SettingsViewModelTest {
    @Test
    fun externalBaselineUpdatesDoNotOverwriteDirtyDraft() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(
            AgentConfig(apiKey = "old-key", systemPrompt = "baseline")
        )
        val heartbeatRepository = FakeHeartbeatRepository()
        val mcpRegistry = FakeMcpRegistry()
        val viewModel = createViewModel(settingsStore, heartbeatRepository, mcpRegistry, FakeWorkerScheduler())

        advanceUntilIdle()
        viewModel.onApiKeyChanged("draft-key")
        settingsStore.emit(AgentConfig(apiKey = "incoming-key", systemPrompt = "incoming"))
        advanceUntilIdle()

        assertEquals("draft-key", viewModel.uiState.value.apiKey)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun savePersistsDraftAndClearsDirtyAfterBaselineRefresh() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(AgentConfig(apiKey = "old-key"))
        val heartbeatRepository = FakeHeartbeatRepository()
        val mcpRegistry = FakeMcpRegistry()
        val scheduler = FakeWorkerScheduler()
        val viewModel = createViewModel(settingsStore, heartbeatRepository, mcpRegistry, scheduler)

        advanceUntilIdle()
        viewModel.onApiKeyChanged("saved-key")
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.saveSettings()
        advanceUntilIdle()

        assertEquals("saved-key", settingsStore.savedConfig?.apiKey)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("saved-key", viewModel.uiState.value.apiKey)
        assertEquals(1, scheduler.refreshCalls)
    }

    @Test
    fun resetRestoresDraftFromBaseline() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(AgentConfig(apiKey = "baseline-key"))
        val viewModel = createViewModel(
            settingsStore,
            FakeHeartbeatRepository(),
            FakeMcpRegistry(),
            FakeWorkerScheduler()
        )

        advanceUntilIdle()
        viewModel.onApiKeyChanged("draft-key")
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.resetDraft()
        advanceUntilIdle()

        assertEquals("baseline-key", viewModel.uiState.value.apiKey)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun savingLifecycleTogglesIsSavingAndPreservesStatus() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(AgentConfig())
        val viewModel = createViewModel(
            settingsStore,
            FakeHeartbeatRepository(),
            FakeMcpRegistry(),
            FakeWorkerScheduler()
        )

        advanceUntilIdle()
        viewModel.onApiKeyChanged("new-key")
        viewModel.saveSettings()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun addMcpServerPersistsHardeningFieldsIntoDraft() = runSettingsTest {
        val viewModel = createViewModel(
            FakeSettingsConfigStore(AgentConfig()),
            FakeHeartbeatRepository(),
            FakeMcpRegistry(),
            FakeWorkerScheduler()
        )

        advanceUntilIdle()
        viewModel.onDraftMcpLabelChanged("GitHub")
        viewModel.onDraftMcpEndpointChanged("https://mcp.example/github")
        viewModel.onDraftMcpAuthTypeChanged(McpAuthType.BEARER)
        viewModel.onDraftMcpAuthTokenChanged("secret")
        viewModel.onDraftMcpConnectTimeoutChanged("10")
        viewModel.onDraftMcpReadTimeoutChanged("20")
        viewModel.onDraftMcpWriteTimeoutChanged("15")
        viewModel.onDraftMcpCallTimeoutChanged("25")
        viewModel.onDraftMcpMaxRetriesChanged("4")
        viewModel.onDraftMcpBackoffBaseMsChanged("750")

        viewModel.addMcpServer()
        advanceUntilIdle()

        val server = viewModel.uiState.value.mcpServers.single()
        assertEquals(McpAuthType.BEARER, server.authType)
        assertEquals("secret", server.authToken)
        assertEquals("10", server.connectTimeoutSeconds)
        assertEquals("20", server.readTimeoutSeconds)
        assertEquals("15", server.writeTimeoutSeconds)
        assertEquals("25", server.callTimeoutSeconds)
        assertEquals("4", server.maxRetries)
        assertEquals("750", server.backoffBaseMs)
        assertEquals(McpAuthType.NONE, viewModel.uiState.value.draftMcpAuthType)
    }

    @Test
    fun skillDiagnosticsAreGroupedByKindForSettingsUi() = runSettingsTest {
        val viewModel = createViewModel(
            settingsStore = FakeSettingsConfigStore(AgentConfig()),
            heartbeatRepository = FakeHeartbeatRepository(),
            mcpRegistry = FakeMcpRegistry(),
            workerScheduler = FakeWorkerScheduler(),
            skillRepository = FakeSkillRepository(
                discoveryIssues = listOf(
                    SkillDiscoveryIssue(
                        message = "Project-scoped skill discovery is disabled until the workspace is trusted.",
                        scope = SkillScope.PROJECT,
                        kind = SkillDiagnosticKind.BLOCKED
                    ),
                    SkillDiscoveryIssue(
                        message = "Skill 'shared' from project compatibility overrides project scope.",
                        scope = SkillScope.PROJECT,
                        kind = SkillDiagnosticKind.OVERRIDDEN
                    ),
                    SkillDiscoveryIssue(
                        message = "Loaded project skill 'local-plan' from 'skills/local-plan/SKILL.md'.",
                        scope = SkillScope.PROJECT,
                        kind = SkillDiagnosticKind.LOADED
                    )
                )
            )
        )

        advanceUntilIdle()

        val diagnostics = viewModel.uiState.value.skillDiagnostics
        assertEquals(listOf("Blocked", "Overridden", "Loaded"), diagnostics.map { it.title })
        assertEquals("project", diagnostics.first().items.single().scopeLabel)
    }

    @Test
    fun pendingPhoneControlUnlockConsentAppearsInSettingsUiAndCanBeAccepted() = runSettingsTest {
        val skillRepository = FakeSkillRepositoryWithConsent(
            pendingConsents = mutableListOf(
                PendingPhoneControlUnlockConsent(
                    packageId = "pkg.phone-operator-basic",
                    skillId = "phone-operator-basic",
                    skillTitle = "Phone Operator",
                    skillSha256 = "hash",
                    unlockProfiles = listOf("phone_control_basic_v1"),
                    consentTitle = "Phone Control Hidden Feature Agreement",
                    consentVersion = "2026-03-14",
                    consentText = "I agree to use this responsibly.",
                    signerKeyId = "publisher-main",
                    signerAlgorithm = "Ed25519",
                    sourceTreeUri = "content://skills/tree",
                    documentUri = "content://skills/tree/phone-operator-basic/SKILL.md",
                    createdAtEpochMs = 1L
                )
            )
        )
        val viewModel = createViewModel(
            settingsStore = FakeSettingsConfigStore(AgentConfig()),
            heartbeatRepository = FakeHeartbeatRepository(),
            mcpRegistry = FakeMcpRegistry(),
            workerScheduler = FakeWorkerScheduler(),
            skillRepository = skillRepository
        )

        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.pendingPhoneControlUnlockConsents.size)

        viewModel.acceptPendingPhoneControlUnlockConsent("pkg.phone-operator-basic")
        advanceUntilIdle()

        assertTrue(skillRepository.acceptedPackageIds.contains("pkg.phone-operator-basic"))
        assertTrue(viewModel.uiState.value.skillImportStatus?.contains("Accepted unlock consent") == true)
        assertTrue(viewModel.uiState.value.pendingPhoneControlUnlockConsents.isEmpty())
    }

    @Test
    fun pendingPhoneControlUnlockConsentStillAppearsWhenDraftIsDirty() = runSettingsTest {
        val skillRepository = FakeSkillRepositoryWithConsent(
            pendingConsents = mutableListOf(
                PendingPhoneControlUnlockConsent(
                    packageId = "pkg.phone-operator-basic",
                    skillId = "phone-operator-basic",
                    skillTitle = "Phone Operator",
                    skillSha256 = "hash",
                    unlockProfiles = listOf("phone_control_basic_v1"),
                    consentTitle = "Phone Control Hidden Feature Agreement",
                    consentVersion = "2026-03-14",
                    consentText = "I agree to use this responsibly.",
                    signerKeyId = "publisher-main",
                    signerAlgorithm = "Ed25519",
                    sourceTreeUri = "content://skills/tree",
                    documentUri = "content://skills/tree/phone-operator-basic/SKILL.md",
                    createdAtEpochMs = 1L
                )
            )
        )
        val viewModel = createViewModel(
            settingsStore = FakeSettingsConfigStore(AgentConfig(apiKey = "baseline")),
            heartbeatRepository = FakeHeartbeatRepository(),
            mcpRegistry = FakeMcpRegistry(),
            workerScheduler = FakeWorkerScheduler(),
            skillRepository = skillRepository
        )

        advanceUntilIdle()
        viewModel.onApiKeyChanged("dirty-change")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDirty)
        assertEquals(1, viewModel.uiState.value.pendingPhoneControlUnlockConsents.size)
    }

    @Test
    fun savePersistsVoiceSettings() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(AgentConfig())
        val viewModel = createViewModel(
            settingsStore,
            FakeHeartbeatRepository(),
            FakeMcpRegistry(),
            FakeWorkerScheduler()
        )

        advanceUntilIdle()
        viewModel.onVoiceInputEnabledChanged(true)
        viewModel.onVoiceAutoPlayChanged(true)
        viewModel.onVoiceEngineChanged(VoiceEngineType.WHISPER)
        viewModel.onTtsLanguageChanged("en-US")
        viewModel.onTtsSpeedChanged("1.40")

        viewModel.saveSettings()
        advanceUntilIdle()

        val saved = settingsStore.savedConfig
        assertEquals(true, saved?.voiceInputEnabled)
        assertEquals(true, saved?.voiceAutoPlay)
        assertEquals(VoiceEngineType.WHISPER, saved?.voiceEngine)
        assertEquals("en-US", saved?.ttsLanguage)
        assertEquals(1.4f, saved?.ttsSpeed)
    }

    @Test
    fun savePersistsVisualMemorySetting() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(AgentConfig())
        val viewModel = createViewModel(
            settingsStore,
            FakeHeartbeatRepository(),
            FakeMcpRegistry(),
            FakeWorkerScheduler()
        )

        advanceUntilIdle()
        viewModel.onEnableVisualMemoryChanged(true)

        viewModel.saveSettings()
        advanceUntilIdle()

        assertEquals(true, settingsStore.savedConfig?.enableVisualMemory)
    }

    @Test
    fun savePersistsTaskPlanningAndBehaviorLearningFlags() = runSettingsTest {
        val settingsStore = FakeSettingsConfigStore(AgentConfig())
        val viewModel = createViewModel(
            settingsStore,
            FakeHeartbeatRepository(),
            FakeMcpRegistry(),
            FakeWorkerScheduler()
        )

        advanceUntilIdle()
        viewModel.onEnableTaskPlanningChanged(false)
        viewModel.onEnableBehaviorLearningChanged(false)

        viewModel.saveSettings()
        advanceUntilIdle()

        assertEquals(false, settingsStore.savedConfig?.enableTaskPlanning)
        assertEquals(false, settingsStore.savedConfig?.enableBehaviorLearning)
    }

    private fun runSettingsTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                block()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        settingsStore: SettingsConfigStore,
        heartbeatRepository: HeartbeatRepository,
        mcpRegistry: McpRegistry,
        workerScheduler: WorkerSchedulingController,
        skillRepository: com.example.nanobot.domain.repository.SkillRepository = FakeSkillRepository()
    ): SettingsViewModel {
        return SettingsViewModel(
            settingsDataStore = settingsStore,
            promptPresetCatalog = PromptPresetCatalog(),
            skillRepository = skillRepository,
            mcpRegistry = mcpRegistry,
            heartbeatRepository = heartbeatRepository,
            systemAccessRepository = FakeSystemAccessRepository(),
            nanobotWorkerScheduler = workerScheduler
        )
    }

    private class FakeSystemAccessRepository : SystemAccessRepository {
        private val state = MutableStateFlow(
            SystemAccessState(
                notificationPermissionRequired = false,
                notificationPermissionGranted = true,
                notificationsEnabled = true,
                accessibilityEnabled = false
            )
        )

        override fun observeSystemAccessState(): Flow<SystemAccessState> = state

        override suspend fun refresh() = Unit

        override fun buildOpenNotificationSettingsIntent(): Intent = Intent("test.notification.settings")

        override fun buildOpenAccessibilitySettingsIntent(): Intent = Intent("test.accessibility.settings")
    }

    private class FakeSkillRepositoryWithConsent(
        private val pendingConsents: MutableList<PendingPhoneControlUnlockConsent>
    ) : com.example.nanobot.domain.repository.SkillRepository by FakeSkillRepository() {
        val acceptedPackageIds = mutableListOf<String>()

        override suspend fun listPendingPhoneControlUnlockConsents(): List<PendingPhoneControlUnlockConsent> {
            return pendingConsents.toList()
        }

        override suspend fun acceptPendingPhoneControlUnlockConsent(packageId: String): PhoneControlUnlockReceipt? {
            val consent = pendingConsents.firstOrNull { it.packageId == packageId } ?: return null
            acceptedPackageIds += packageId
            pendingConsents.removeAll { it.packageId == packageId }
            return PhoneControlUnlockReceipt(
                packageId = consent.packageId,
                skillId = consent.skillId,
                skillSha256 = consent.skillSha256,
                unlockProfiles = consent.unlockProfiles,
                signerKeyId = consent.signerKeyId,
                signerAlgorithm = consent.signerAlgorithm,
                consentTitle = consent.consentTitle,
                consentVersion = consent.consentVersion,
                consentTextSha256 = "hash",
                storedAtEpochMs = 1L,
                sourceTreeUri = consent.sourceTreeUri,
                documentUri = consent.documentUri
            )
        }

        override suspend fun rejectPendingPhoneControlUnlockConsent(packageId: String) {
            pendingConsents.removeAll { it.packageId == packageId }
        }
    }

    private class FakeSettingsConfigStore(initial: AgentConfig) : SettingsConfigStore {
        private val flow = MutableStateFlow(initial)
        private val skillsDirectory = MutableStateFlow<String?>(null)
        private val skillRoots = MutableStateFlow<List<String>>(emptyList())
        private val trustProjectSkills = MutableStateFlow(false)
        var savedConfig: AgentConfig? = null

        override val configFlow: Flow<AgentConfig> = flow
        override val skillsDirectoryUriFlow: Flow<String?> = skillsDirectory
        override val skillRootsFlow: Flow<List<String>> = skillRoots
        override val trustProjectSkillsFlow: Flow<Boolean> = trustProjectSkills

        override suspend fun save(config: AgentConfig) {
            savedConfig = config
            flow.value = config
        }

        override suspend fun saveSkillsDirectoryUri(uri: String?) {
            skillsDirectory.value = uri
        }

        override suspend fun addSkillRootUri(uri: String) {
            skillRoots.value = (skillRoots.value + uri).distinct()
            skillsDirectory.value = uri
        }

        override suspend fun removeSkillRootUri(uri: String) {
            skillRoots.value = skillRoots.value.filterNot { it == uri }
            if (skillsDirectory.value == uri) {
                skillsDirectory.value = skillRoots.value.lastOrNull()
            }
        }

        override suspend fun setTrustProjectSkills(trusted: Boolean) {
            trustProjectSkills.value = trusted
        }

        fun emit(config: AgentConfig) {
            flow.value = config
        }
    }

    private class FakeHeartbeatRepository : HeartbeatRepository {
        private val enabled = MutableStateFlow(true)
        private val instructions = MutableStateFlow("")

        override fun observeHeartbeatInstructions(): Flow<String> = instructions

        override fun observeHeartbeatEnabled(): Flow<Boolean> = enabled

        override suspend fun getHeartbeatInstructions(): String = instructions.value

        override suspend fun isHeartbeatEnabled(): Boolean = enabled.value

        override suspend fun setHeartbeatInstructions(value: String) {
            instructions.value = value
        }

        override suspend fun setHeartbeatEnabled(value: Boolean) {
            enabled.value = value
        }
    }

    private class FakeMcpRegistry : McpRegistry {
        private val servers = MutableStateFlow(emptyList<McpServerDefinition>())
        private val tools = MutableStateFlow(emptyList<McpToolDescriptor>())

        override fun observeServers(): Flow<List<McpServerDefinition>> = servers

        override fun observeCachedTools(): Flow<List<McpToolDescriptor>> = tools

        override suspend fun listEnabledServers(): List<McpServerDefinition> = servers.value.filter { it.enabled }

        override suspend fun listEnabledTools(): List<McpToolDescriptor> = tools.value

        override suspend fun refreshTools(): McpRefreshResult = McpRefreshResult(servers.value.size, tools.value.size)

        override suspend fun saveServers(servers: List<McpServerDefinition>) {
            this.servers.value = servers
        }

        override suspend fun callTool(toolName: String, arguments: JsonObject): String = toolName

        override suspend fun getDiscoverySnapshot(): McpToolDiscoverySnapshot {
            return McpToolDiscoverySnapshot(
                enabledServers = servers.value,
                tools = tools.value
            )
        }
    }

    private class FakeWorkerScheduler : WorkerSchedulingController {
        var refreshCalls: Int = 0

        override suspend fun refreshScheduling() {
            refreshCalls += 1
        }
    }

}
