package com.example.nanobot.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nanobot.core.mcp.McpAuthType
import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.feature.settings.sections.AboutSection
import com.example.nanobot.feature.settings.sections.FeaturesSection
import com.example.nanobot.feature.settings.sections.McpSection
import com.example.nanobot.feature.settings.sections.PermissionsSection
import com.example.nanobot.feature.settings.sections.ProviderSection
import com.example.nanobot.feature.settings.sections.SectionOutlinedButton
import com.example.nanobot.feature.settings.sections.SkillsSection
import com.example.nanobot.feature.settings.sections.WorkspaceSection
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.GlowButton
import com.example.nanobot.ui.components.NanobotTopBar
import com.example.nanobot.ui.theme.NanobotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    onMaxToolIterationsChange: (String) -> Unit,
    onMemoryWindowChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onEnableToolsChange: (Boolean) -> Unit,
    onEnableMemoryChange: (Boolean) -> Unit,
    onEnableVisualMemoryChange: (Boolean) -> Unit,
    onEnableBackgroundWorkChange: (Boolean) -> Unit,
    onEnableTaskPlanningChange: (Boolean) -> Unit,
    onEnableBehaviorLearningChange: (Boolean) -> Unit,
    onVoiceInputEnabledChange: (Boolean) -> Unit,
    onVoiceAutoPlayChange: (Boolean) -> Unit,
    onVoiceEngineChange: (VoiceEngineType) -> Unit,
    onTtsSpeedChange: (String) -> Unit,
    onTtsLanguageChange: (String) -> Unit,
    onHeartbeatEnabledChange: (Boolean) -> Unit,
    onHeartbeatInstructionsChange: (String) -> Unit,
    onWebSearchApiKeyChange: (String) -> Unit,
    onWebProxyChange: (String) -> Unit,
    onRestrictToWorkspaceChange: (Boolean) -> Unit,
    onPresetChange: (String) -> Unit,
    onSkillToggle: (String, Boolean) -> Unit,
    onSkillDirectorySelected: (android.net.Uri) -> Unit,
    onSkillZipSelected: (android.net.Uri) -> Unit,
    onTrustProjectSkillsChange: (Boolean) -> Unit,
    onRescanImportedSkills: () -> Unit,
    onRemoveSkillRoot: (String) -> Unit,
    onRemoveImportedSkill: (String) -> Unit,
    onAcceptPendingPhoneControlUnlockConsent: (String) -> Unit,
    onRejectPendingPhoneControlUnlockConsent: (String) -> Unit,
    onDraftMcpLabelChange: (String) -> Unit,
    onDraftMcpEndpointChange: (String) -> Unit,
    onDraftMcpAuthTypeChange: (McpAuthType) -> Unit,
    onDraftMcpAuthTokenChange: (String) -> Unit,
    onDraftMcpAuthHeaderNameChange: (String) -> Unit,
    onDraftMcpAuthHeaderValueChange: (String) -> Unit,
    onDraftMcpConnectTimeoutChange: (String) -> Unit,
    onDraftMcpReadTimeoutChange: (String) -> Unit,
    onDraftMcpWriteTimeoutChange: (String) -> Unit,
    onDraftMcpCallTimeoutChange: (String) -> Unit,
    onDraftMcpMaxRetriesChange: (String) -> Unit,
    onDraftMcpBackoffBaseMsChange: (String) -> Unit,
    onMcpServerToggle: (String, Boolean) -> Unit,
    onAddMcpServer: () -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onRefreshMcpTools: () -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshSystemAccess: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTools: () -> Unit,
    onResetClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ext = NanobotTheme.extendedColors
    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshSystemAccess()
    }

    DisposableEffect(lifecycleOwner, onRefreshSystemAccess) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshSystemAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val skillsDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onSkillDirectorySelected(uri)
        }
    }

    val skillsZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSkillZipSelected(uri)
        }
    }

    var selectedPendingConsent by remember { mutableStateOf<PendingPhoneControlUnlockConsentUiState?>(null) }
    LaunchedEffect(state.pendingPhoneControlUnlockConsents) {
        val currentPackageId = selectedPendingConsent?.packageId
        selectedPendingConsent = when {
            state.pendingPhoneControlUnlockConsents.isEmpty() -> null
            currentPackageId == null -> state.pendingPhoneControlUnlockConsents.first()
            else -> state.pendingPhoneControlUnlockConsents.firstOrNull { it.packageId == currentPackageId }
                ?: state.pendingPhoneControlUnlockConsents.first()
        }
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            NanobotTopBar(
                title = "Settings",
                subtitle = if (state.isDirty) "Unsaved changes" else "Configuration is synced",
                badgeText = if (state.isSaving) "Saving" else null,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "Back",
                onNavigationClick = onBackClick
            )
        },
        floatingActionButton = {
            if (state.isDirty) {
                GlowButton(
                    text = if (state.isSaving) "Saving..." else "Save",
                    onClick = onSaveClick,
                    enabled = !state.isSaving
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 18.dp, highlighted = state.isDirty) {
                    Text(
                        text = "Control Nanobot's provider, tools, memory, skills, and runtime behavior from one place.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = ext.textPrimary
                    )
                    Text(
                        text = if (state.isDirty) {
                            "You have pending draft changes. Save when you're ready to apply them."
                        } else {
                            "Your current configuration matches the saved device state."
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                }
            }
            item {
                AboutSection(
                    onOpenRepository = { uriHandler.openUri("https://github.com/zensu357/Android-nanobot") },
                    onOpenAuthor = { uriHandler.openUri("https://github.com/zensu357") },
                    onOpenTelegram = { uriHandler.openUri("https://t.me/***") }
                )
            }
            item {
                PermissionsSection(
                    state = state,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onRequestNotificationPermission()
                        }
                    },
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }
            item {
                ProviderSection(
                    state = state,
                    onProviderChange = onProviderChange,
                    onApiKeyChange = onApiKeyChange,
                    onBaseUrlChange = onBaseUrlChange,
                    onModelChange = onModelChange,
                    onMaxTokensChange = onMaxTokensChange,
                    onMaxToolIterationsChange = onMaxToolIterationsChange,
                    onMemoryWindowChange = onMemoryWindowChange,
                    onReasoningEffortChange = onReasoningEffortChange,
                    onPresetChange = onPresetChange
                )
            }
            if (state.skillOptions.isNotEmpty()) {
                item {
                    SkillsSection(
                        state = state,
                        onImportDirectory = { skillsDirectoryLauncher.launch(null) },
                        onImportZip = {
                            skillsZipLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/octet-stream",
                                    "application/x-zip-compressed"
                                )
                            )
                        },
                        onTrustProjectSkillsChange = onTrustProjectSkillsChange,
                        onRescanImportedSkills = onRescanImportedSkills,
                        onRemoveSkillRoot = onRemoveSkillRoot,
                        onSkillToggle = onSkillToggle,
                        onRemoveImportedSkill = onRemoveImportedSkill
                    )
                }
            }
            item {
                McpSection(
                    state = state,
                    onDraftMcpLabelChange = onDraftMcpLabelChange,
                    onDraftMcpEndpointChange = onDraftMcpEndpointChange,
                    onDraftMcpAuthTypeChange = onDraftMcpAuthTypeChange,
                    onDraftMcpAuthTokenChange = onDraftMcpAuthTokenChange,
                    onDraftMcpAuthHeaderNameChange = onDraftMcpAuthHeaderNameChange,
                    onDraftMcpAuthHeaderValueChange = onDraftMcpAuthHeaderValueChange,
                    onDraftMcpConnectTimeoutChange = onDraftMcpConnectTimeoutChange,
                    onDraftMcpReadTimeoutChange = onDraftMcpReadTimeoutChange,
                    onDraftMcpWriteTimeoutChange = onDraftMcpWriteTimeoutChange,
                    onDraftMcpCallTimeoutChange = onDraftMcpCallTimeoutChange,
                    onDraftMcpMaxRetriesChange = onDraftMcpMaxRetriesChange,
                    onDraftMcpBackoffBaseMsChange = onDraftMcpBackoffBaseMsChange,
                    onMcpServerToggle = onMcpServerToggle,
                    onAddMcpServer = onAddMcpServer,
                    onRemoveMcpServer = onRemoveMcpServer,
                    onRefreshMcpTools = onRefreshMcpTools
                )
            }
            item {
                FeaturesSection(
                    state = state,
                    onEnableToolsChange = onEnableToolsChange,
                    onEnableMemoryChange = onEnableMemoryChange,
                    onEnableVisualMemoryChange = onEnableVisualMemoryChange,
                    onEnableBackgroundWorkChange = onEnableBackgroundWorkChange,
                    onEnableTaskPlanningChange = onEnableTaskPlanningChange,
                    onEnableBehaviorLearningChange = onEnableBehaviorLearningChange,
                    onVoiceInputEnabledChange = onVoiceInputEnabledChange,
                    onVoiceAutoPlayChange = onVoiceAutoPlayChange,
                    onVoiceEngineChange = onVoiceEngineChange,
                    onTtsSpeedChange = onTtsSpeedChange,
                    onTtsLanguageChange = onTtsLanguageChange,
                    onHeartbeatEnabledChange = onHeartbeatEnabledChange,
                    onHeartbeatInstructionsChange = onHeartbeatInstructionsChange
                )
            }
            item {
                WorkspaceSection(
                    state = state,
                    onWebSearchApiKeyChange = onWebSearchApiKeyChange,
                    onWebProxyChange = onWebProxyChange,
                    onRestrictToWorkspaceChange = onRestrictToWorkspaceChange,
                    onSystemPromptChange = onSystemPromptChange
                )
            }
            item {
                SectionOutlinedButton(
                    text = "Reset Draft",
                    onClick = onResetClick,
                    enabled = state.isDirty && !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlowButton(
                        text = "Memory",
                        onClick = onOpenMemory,
                        modifier = Modifier.weight(1f)
                    )
                    GlowButton(
                        text = "Tools",
                        onClick = onOpenTools,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    selectedPendingConsent?.let { consent ->
        AlertDialog(
            onDismissRequest = { selectedPendingConsent = null },
            title = { Text(consent.consentTitle) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            text = "Skill: ${consent.skillTitle}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item {
                        Text(text = "Version: ${consent.consentVersion}")
                    }
                    item {
                        Text(text = "Unlock profiles: ${consent.unlockProfiles.joinToString()}")
                    }
                    item {
                        Text(text = consent.consentText)
                    }
                    item {
                        Text(
                            text = "Signer: ${consent.signerKeyId} (${consent.signerAlgorithm})",
                            color = ext.textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAcceptPendingPhoneControlUnlockConsent(consent.packageId) }) {
                    Text("I Agree")
                }
            },
            dismissButton = {
                TextButton(onClick = { onRejectPendingPhoneControlUnlockConsent(consent.packageId) }) {
                    Text("Reject")
                }
            }
        )
    }
}
