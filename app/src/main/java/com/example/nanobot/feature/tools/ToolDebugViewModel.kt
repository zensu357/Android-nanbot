package com.example.nanobot.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanobot.core.ai.PromptDiagnosticsStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentRunContext
import com.example.nanobot.core.preferences.SettingsDataStore
import com.example.nanobot.core.tools.ToolRegistry
import com.example.nanobot.core.web.WebDiagnosticsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@HiltViewModel
class ToolDebugViewModel @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val settingsDataStore: SettingsDataStore,
    private val promptDiagnosticsStore: PromptDiagnosticsStore,
    private val webDiagnosticsStore: WebDiagnosticsStore
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var debugConfig = AgentConfig()

    private val _uiState = MutableStateFlow(ToolDebugUiState())
    val uiState: StateFlow<ToolDebugUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.configFlow.collect { config ->
                debugConfig = config
                val previous = _uiState.value
                val visibleTools = toolRegistry.visibleTools(
                    config,
                    AgentRunContext.root("tool-debug", config.maxSubagentDepth)
                )
                _uiState.value = previous.copy(
                    tools = visibleTools.map { tool ->
                        val existingItem = previous.tools.firstOrNull { it.name == tool.name }
                        ToolDebugItem(
                            name = tool.name,
                            description = "${tool.description} (${tool.availabilityHint})",
                            schema = json.encodeToString(JsonObject.serializer(), tool.parametersSchema),
                            sampleArguments = existingItem?.sampleArguments ?: sampleArgumentsFor(tool.name),
                            lastResult = existingItem?.lastResult
                        )
                    },
                    policySummary = toolRegistry.accessPolicySummary(config),
                    restrictToWorkspace = config.restrictToWorkspace
                )
            }
        }
        viewModelScope.launch {
            promptDiagnosticsStore.snapshot.collect { snapshot ->
                _uiState.value = _uiState.value.copy(promptDiagnostics = snapshot)
            }
        }
        viewModelScope.launch {
            webDiagnosticsStore.snapshot.collect { snapshot ->
                _uiState.value = _uiState.value.copy(webDiagnostics = snapshot)
            }
        }
    }

    fun updateArguments(toolName: String, value: String) {
        _uiState.value = _uiState.value.copy(
            tools = _uiState.value.tools.map { item ->
                if (item.name == toolName) item.copy(sampleArguments = value) else item
            },
            errorMessage = null
        )
    }

    fun runTool(toolName: String) {
        val tool = _uiState.value.tools.firstOrNull { it.name == toolName } ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, errorMessage = null)
            runCatching {
                val arguments = json.parseToJsonElement(tool.sampleArguments).jsonObject
                toolRegistry.execute(toolName, arguments, debugConfig, AgentRunContext.root("tool-debug", debugConfig.maxSubagentDepth))
            }.onSuccess { result ->
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    tools = _uiState.value.tools.map { item ->
                        if (item.name == toolName) item.copy(lastResult = result) else item
                    }
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    errorMessage = throwable.message ?: "Failed to run tool."
                )
            }
        }
    }

    private fun sampleArgumentsFor(toolName: String): String {
        val sample = when (toolName) {
            "list_workspace" -> buildJsonObject {
                put("relativePath", "")
                put("limit", 20)
            }
            "read_file" -> buildJsonObject {
                put("relativePath", "notes.txt")
                put("maxChars", 1500)
            }
            "search_workspace" -> buildJsonObject {
                put("query", "TODO")
                put("relativePath", "")
                put("limit", 10)
            }
            "write_file" -> buildJsonObject {
                put("relativePath", "notes.txt")
                put("content", "Hello from Nanobot workspace write.\n")
                put("overwrite", true)
            }
            "replace_in_file" -> buildJsonObject {
                put("relativePath", "notes.txt")
                put("find", "Nanobot")
                put("replaceWith", "Android Nanobot")
                put("expectedOccurrences", 1)
            }
            "web_fetch" -> buildJsonObject {
                put("url", "https://example.com")
                put("maxChars", 2000)
            }
            "web_search" -> buildJsonObject {
                put("query", "Android WorkManager periodic work")
                put("limit", 5)
            }
            "notify_user" -> buildJsonObject { put("message", "Hello from the tool debug page") }
            "memory_lookup" -> buildJsonObject { put("query", "android") }
            "schedule_reminder" -> buildJsonObject {
                put("message", "Review the Android nanobot reminder flow")
                put("delayMinutes", 15)
                put("title", "Nanobot Reminder")
            }
            "read_current_ui" -> buildJsonObject {
                put("includeNonInteractive", false)
                put("maxNodes", 40)
            }
            "tap_ui_node" -> buildJsonObject {
                put("nodeId", "node-1")
            }
            "input_text" -> buildJsonObject {
                put("nodeId", "input-1")
                put("text", "Hello from hidden tool debug")
            }
            "scroll_ui" -> buildJsonObject {
                put("nodeId", "list-1")
                put("direction", "forward")
            }
            "press_global_action" -> buildJsonObject {
                put("action", "back")
            }
            "launch_app" -> buildJsonObject {
                put("packageName", "com.android.settings")
            }
            "wait_for_ui" -> buildJsonObject {
                put("text", "Settings")
                put("timeoutMs", 3000)
            }
            "delegate_task" -> buildJsonObject {
                put("task", "Inspect the workspace and return a compact summary")
                put("title", "Debug Delegated Task")
            }
            "session_snapshot" -> buildJsonObject { }
            else -> buildJsonObject { }
        }
        return json.encodeToString(JsonObject.serializer(), sample)
    }
}
