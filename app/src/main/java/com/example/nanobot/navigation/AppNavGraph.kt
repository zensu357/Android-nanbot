package com.example.nanobot.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nanobot.feature.chat.ChatScreen
import com.example.nanobot.feature.chat.ChatViewModel
import com.example.nanobot.feature.onboarding.OnboardingScreen
import com.example.nanobot.feature.onboarding.OnboardingViewModel
import com.example.nanobot.feature.memory.MemoryScreen
import com.example.nanobot.feature.memory.MemoryViewModel
import com.example.nanobot.feature.settings.SettingsScreen
import com.example.nanobot.feature.settings.SettingsViewModel
import com.example.nanobot.feature.sessions.SessionsScreen
import com.example.nanobot.feature.sessions.SessionsViewModel
import com.example.nanobot.feature.tools.ToolDebugScreen
import com.example.nanobot.feature.tools.ToolDebugViewModel

@Composable
fun AppNavGraph() {
    val appViewModel: AppNavGraphViewModel = hiltViewModel()
    val navController = rememberNavController()
    val isCompleted by appViewModel.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = null)

    if (isCompleted == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val startDestination = if (isCompleted == true) Destinations.Chat.route else Destinations.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.Onboarding.route) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            OnboardingScreen(
                state = state.value,
                onProviderChange = viewModel::onProviderChanged,
                onApiKeyChange = viewModel::onApiKeyChanged,
                onBaseUrlChange = viewModel::onBaseUrlChanged,
                onModelChange = viewModel::onModelChanged,
                onPresetChange = viewModel::onPresetChanged,
                onSystemPromptChange = viewModel::onSystemPromptChanged,
                onContinue = {
                    viewModel.complete {
                        navController.navigate(Destinations.Chat.route) {
                            popUpTo(Destinations.Onboarding.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Destinations.Chat.route) {
            val viewModel: ChatViewModel = hiltViewModel()
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            ChatScreen(
                state = state.value,
                onMessageChange = viewModel::onInputChanged,
                onAttachImage = viewModel::attachImage,
                onAttachFile = viewModel::attachFile,
                onRemovePendingAttachment = viewModel::removePendingAttachment,
                onActivateSkill = viewModel::activateSkill,
                onDeactivateSkill = viewModel::deactivateSkill,
                onSendClick = viewModel::sendMessage,
                onCancelClick = viewModel::cancelSend,
                onToggleToolMessage = viewModel::toggleToolMessage,
                onOpenSessions = { navController.navigate(Destinations.Sessions.route) },
                onOpenSettings = { navController.navigate(Destinations.Settings.route) }
            )
        }
        composable(Destinations.Sessions.route) {
            val viewModel: SessionsViewModel = hiltViewModel()
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            SessionsScreen(
                state = state.value,
                onCreateSession = viewModel::createSession,
                onSelectSession = {
                    viewModel.selectSession(it)
                    navController.popBackStack()
                },
                onDeleteSession = viewModel::deleteSession,
                onDismissError = viewModel::clearError,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Destinations.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state.value,
                onProviderChange = viewModel::onProviderChanged,
                onApiKeyChange = viewModel::onApiKeyChanged,
                onBaseUrlChange = viewModel::onBaseUrlChanged,
                onModelChange = viewModel::onModelChanged,
                onMaxTokensChange = viewModel::onMaxTokensChanged,
                onMaxToolIterationsChange = viewModel::onMaxToolIterationsChanged,
                onMemoryWindowChange = viewModel::onMemoryWindowChanged,
                onReasoningEffortChange = viewModel::onReasoningEffortChanged,
                onEnableToolsChange = viewModel::onEnableToolsChanged,
                onEnableMemoryChange = viewModel::onEnableMemoryChanged,
                onEnableBackgroundWorkChange = viewModel::onEnableBackgroundWorkChanged,
                onHeartbeatEnabledChange = viewModel::onHeartbeatEnabledChanged,
                onHeartbeatInstructionsChange = viewModel::onHeartbeatInstructionsChanged,
                onWebSearchApiKeyChange = viewModel::onWebSearchApiKeyChanged,
                onWebProxyChange = viewModel::onWebProxyChanged,
                onRestrictToWorkspaceChange = viewModel::onRestrictToWorkspaceChanged,
                onPresetChange = viewModel::onPresetChanged,
                onSkillToggle = viewModel::onSkillToggled,
                onSkillDirectorySelected = viewModel::onSkillDirectorySelected,
                onSkillZipSelected = viewModel::onSkillZipSelected,
                onTrustProjectSkillsChange = viewModel::onTrustProjectSkillsChanged,
                onRescanImportedSkills = viewModel::rescanImportedSkills,
                onRemoveSkillRoot = viewModel::onRemoveSkillRoot,
                onRemoveImportedSkill = viewModel::onRemoveImportedSkill,
                onDraftMcpLabelChange = viewModel::onDraftMcpLabelChanged,
                onDraftMcpEndpointChange = viewModel::onDraftMcpEndpointChanged,
                onDraftMcpAuthTypeChange = viewModel::onDraftMcpAuthTypeChanged,
                onDraftMcpAuthTokenChange = viewModel::onDraftMcpAuthTokenChanged,
                onDraftMcpAuthHeaderNameChange = viewModel::onDraftMcpAuthHeaderNameChanged,
                onDraftMcpAuthHeaderValueChange = viewModel::onDraftMcpAuthHeaderValueChanged,
                onDraftMcpConnectTimeoutChange = viewModel::onDraftMcpConnectTimeoutChanged,
                onDraftMcpReadTimeoutChange = viewModel::onDraftMcpReadTimeoutChanged,
                onDraftMcpWriteTimeoutChange = viewModel::onDraftMcpWriteTimeoutChanged,
                onDraftMcpCallTimeoutChange = viewModel::onDraftMcpCallTimeoutChanged,
                onDraftMcpMaxRetriesChange = viewModel::onDraftMcpMaxRetriesChanged,
                onDraftMcpBackoffBaseMsChange = viewModel::onDraftMcpBackoffBaseMsChanged,
                onMcpServerToggle = viewModel::onMcpServerToggled,
                onAddMcpServer = viewModel::addMcpServer,
                onRemoveMcpServer = viewModel::removeMcpServer,
                onRefreshMcpTools = viewModel::refreshMcpTools,
                onSystemPromptChange = viewModel::onSystemPromptChanged,
                onOpenMemory = { navController.navigate(Destinations.Memory.route) },
                onOpenTools = { navController.navigate(Destinations.Tools.route) },
                onResetClick = viewModel::resetDraft,
                onSaveClick = viewModel::saveSettings,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Destinations.Memory.route) {
            val viewModel: MemoryViewModel = hiltViewModel()
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            MemoryScreen(
                state = state.value,
                onEditFact = { factId ->
                    state.value.facts.firstOrNull { it.id == factId }?.let(viewModel::startEditingFact)
                },
                onDeleteFact = viewModel::deleteFact,
                onDeleteSummary = viewModel::deleteSummary,
                onRebuildSummary = viewModel::rebuildSummary,
                onFactDraftChange = viewModel::updateFactDraft,
                onSaveFactEdit = viewModel::saveFactEdit,
                onCancelFactEdit = viewModel::cancelEditingFact,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Destinations.Tools.route) {
            val viewModel: ToolDebugViewModel = hiltViewModel()
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            ToolDebugScreen(
                state = state.value,
                onArgumentsChange = viewModel::updateArguments,
                onRunTool = viewModel::runTool,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
