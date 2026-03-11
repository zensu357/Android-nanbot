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
import com.example.nanobot.core.preferences.SettingsConfigStore
import com.example.nanobot.core.worker.WorkerSchedulingController
import com.example.nanobot.domain.repository.HeartbeatRepository
import com.example.nanobot.testutil.FakeSkillRepository
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
        workerScheduler: WorkerSchedulingController
    ): SettingsViewModel {
        return SettingsViewModel(
            settingsDataStore = settingsStore,
            promptPresetCatalog = PromptPresetCatalog(),
            skillRepository = FakeSkillRepository(),
            mcpRegistry = mcpRegistry,
            heartbeatRepository = heartbeatRepository,
            nanobotWorkerScheduler = workerScheduler
        )
    }

    private class FakeSettingsConfigStore(initial: AgentConfig) : SettingsConfigStore {
        private val flow = MutableStateFlow(initial)
        private val skillsDirectory = MutableStateFlow<String?>(null)
        var savedConfig: AgentConfig? = null

        override val configFlow: Flow<AgentConfig> = flow
        override val skillsDirectoryUriFlow: Flow<String?> = skillsDirectory

        override suspend fun save(config: AgentConfig) {
            savedConfig = config
            flow.value = config
        }

        override suspend fun saveSkillsDirectoryUri(uri: String?) {
            skillsDirectory.value = uri
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
