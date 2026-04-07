@file:OptIn(ExperimentalFoundationApi::class)

package com.example.nanobot.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatSession
import com.example.nanobot.core.skills.ActivatedSkillSource
import com.example.nanobot.core.voice.VoiceState
import com.example.nanobot.ui.components.AnimatedTypingIndicator
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.NanobotInputBar
import com.example.nanobot.ui.components.NanobotMessageBubble
import com.example.nanobot.ui.components.NanobotTopBar
import com.example.nanobot.ui.components.shimmerEffect
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onMessageChange: (String) -> Unit,
    onAttachImage: (Uri) -> Unit,
    onAttachFile: (Uri) -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    onActivateSkill: (String) -> Unit,
    onDeactivateSkill: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onToggleVoiceInput: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onFinishVoiceInput: () -> Unit,
    onStopVoiceOutput: () -> Unit,
    onVoicePermissionDenied: () -> Unit,
    onToggleToolMessage: (String) -> Unit,
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTools: () -> Unit,
    sessionsErrorMessage: String? = null
) {
    val ext = NanobotTheme.extendedColors
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val listState = rememberLazyListState()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onAttachImage)
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleVoiceInput()
        } else {
            onVoicePermissionDenied()
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onAttachFile)
    }

    var showAttachSheet by remember { mutableStateOf(false) }
    var showSkillMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = NanobotShapes.Drawer
            ) {
                SessionDrawer(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    errorMessage = sessionsErrorMessage,
                    onCreateSession = {
                        onCreateSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { id ->
                        onSelectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = onDeleteSession,
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    onOpenMemory = {
                        scope.launch { drawerState.close() }
                        onOpenMemory()
                    },
                    onOpenTools = {
                        scope.launch { drawerState.close() }
                        onOpenTools()
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ext.neonGlow.copy(alpha = 0.18f), Color.Transparent)
                            )
                        )
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    NanobotTopBar(
                        title = state.sessionTitle,
                        subtitle = state.activeToolName?.let { "Running $it" }
                            ?: if (state.activeSkills.isNotEmpty()) "${state.activeSkills.size} skill${if (state.activeSkills.size == 1) "" else "s"} active" else null,
                        badgeText = state.modelName,
                        navigationIcon = Icons.Default.Menu,
                        navigationContentDescription = "Sessions",
                        onNavigationClick = { scope.launch { drawerState.open() } },
                        actions = {
                            Box {
                                IconButton(onClick = { showSkillMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = "Activate Skill",
                                        tint = ext.textSecondary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSkillMenu,
                                    onDismissRequest = { showSkillMenu = false }
                                ) {
                                    if (state.availableSkills.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No enabled skills") },
                                            onClick = { showSkillMenu = false }
                                        )
                                    } else {
                                        state.availableSkills.forEach { skill ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(skill.title)
                                                        Text(
                                                            text = skill.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    showSkillMenu = false
                                                    onActivateSkill(skill.name)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = ext.textSecondary
                                )
                            }
                        }
                    )

                    if (state.isLoadingHistory) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 196.dp)
                        ) {
                            items(5) { index ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (index % 2 == 0) 72.dp else 96.dp)
                                        .padding(horizontal = if (index % 2 == 0) 48.dp else 0.dp)
                                        .background(
                                            color = ext.glassOverlay,
                                            shape = if (index % 2 == 0) NanobotShapes.UserBubble else NanobotShapes.AssistantBubble
                                        )
                                        .shimmerEffect()
                                )
                            }
                        }
                    } else if (state.messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                innerPadding = 24.dp,
                                borderColor = ext.neonGlow.copy(alpha = 0.6f),
                                highlighted = true
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = NanobotShapes.Chip,
                                        color = ext.neonDim.copy(alpha = 0.24f)
                                    ) {
                                        Text(
                                            text = "J.A.R.V.I.S online",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ext.neon,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    Text(
                                        text = "What can I help with?",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = ext.textPrimary
                                    )
                                    Text(
                                        text = "Start a conversation, activate a skill, or open a previous session from the drawer.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ext.textSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 196.dp)
                        ) {
                            if (state.activeSkills.isNotEmpty()) {
                                item {
                                    ActiveSkillsStrip(
                                        skills = state.activeSkills,
                                        onDeactivateSkill = onDeactivateSkill
                                    )
                                }
                            }

                            if (state.pendingAttachments.isNotEmpty()) {
                                item {
                                    PendingAttachmentsCard(
                                        attachments = state.pendingAttachments,
                                        onRemovePendingAttachment = onRemovePendingAttachment
                                    )
                                }
                            }

                            items(state.messages, key = { it.id }) { message ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn()
                                ) {
                                    NanobotMessageBubble(
                                        message = message,
                                        expanded = message.id in state.expandedToolMessageIds,
                                        onToggleToolMessage = onToggleToolMessage
                                    )
                                }
                            }

                            if (state.isRunning) {
                                item {
                                    AnimatedTypingIndicator()
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.errorMessage?.let { errorText ->
                        StatusBanner(
                            text = errorText,
                            textColor = ext.errorRed,
                            accentColor = ext.errorRed
                        )
                    }

                    state.statusText?.let { status ->
                        StatusBanner(
                            text = status,
                            textColor = ext.neon,
                            accentColor = ext.neon
                        )
                    }

                    state.voiceStatusHint?.let { hint ->
                        StatusBanner(
                            text = hint,
                            textColor = if (state.voiceState == VoiceState.LISTENING) ext.errorRed else ext.textSecondary,
                            accentColor = if (state.voiceState == VoiceState.LISTENING) ext.errorRed else ext.textSecondary
                        )
                    }

                    NanobotInputBar(
                        value = state.input,
                        onValueChange = onMessageChange,
                        placeholder = when (state.voiceState) {
                            VoiceState.LISTENING -> "Listening for your message..."
                            VoiceState.PROCESSING -> "Transcribing voice input..."
                            else -> "Message..."
                        },
                        isRunning = state.isRunning,
                        isSending = state.isSending,
                        isCancelling = state.isCancelling,
                        onSendClick = onSendClick,
                        onCancelClick = onCancelClick,
                        onAttachClick = { showAttachSheet = true },
                        voiceContent = if (state.voiceInputEnabled) {
                            {
                                VoiceInputButton(
                                    state = state.voiceState,
                                    enabled = !state.isCancelling,
                                    onTap = {
                                        when (state.voiceState) {
                                            VoiceState.SPEAKING -> onStopVoiceOutput()
                                            VoiceState.LISTENING, VoiceState.PROCESSING -> onFinishVoiceInput()
                                            VoiceState.IDLE -> {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) {
                                                    onToggleVoiceInput()
                                                } else {
                                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        }
                                    },
                                    onLongPress = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onStartVoiceInput()
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NanobotShapes.BottomSheet
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Attachment",
                    style = MaterialTheme.typography.titleLarge,
                    color = ext.textPrimary
                )

                AttachmentOption(
                    icon = Icons.Default.Add,
                    label = "Choose image",
                    supportingText = "Send a photo or screenshot with your prompt."
                ) {
                    showAttachSheet = false
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }

                AttachmentOption(
                    icon = Icons.Default.Build,
                    label = "Choose file",
                    supportingText = "Attach any local document the model can inspect."
                ) {
                    showAttachSheet = false
                    fileLauncher.launch(arrayOf("*/*"))
                }
            }
        }
    }
}

@Composable
private fun ActiveSkillsStrip(
    skills: List<ActiveSkillUiState>,
    onDeactivateSkill: (String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), innerPadding = 12.dp, highlighted = true) {
        Text(
            text = "Active Skills",
            style = MaterialTheme.typography.labelLarge,
            color = NanobotTheme.extendedColors.textSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            skills.forEach { skill ->
                ActiveSkillChip(skill = skill, onDeactivate = { onDeactivateSkill(skill.name) })
            }
        }
    }
}

@Composable
private fun ActiveSkillChip(
    skill: ActiveSkillUiState,
    onDeactivate: () -> Unit
) {
    val ext = NanobotTheme.extendedColors
    val sourceLabel = if (skill.source == ActivatedSkillSource.USER) "You" else "Auto"
    val sourceColor = if (skill.source == ActivatedSkillSource.USER) ext.neonDim else ext.neonGlow

    Surface(
        shape = NanobotShapes.Chip,
        color = ext.glassOverlay,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = skill.title,
                style = MaterialTheme.typography.labelMedium,
                color = ext.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Surface(shape = NanobotShapes.Chip, color = sourceColor) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            IconButton(onClick = onDeactivate, modifier = Modifier.size(18.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Deactivate skill",
                    tint = ext.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentsCard(
    attachments: List<Attachment>,
    onRemovePendingAttachment: (String) -> Unit
) {
    val ext = NanobotTheme.extendedColors

    GlassCard(modifier = Modifier.fillMaxWidth(), highlighted = true) {
        Text(
            text = "Pending Attachments",
            style = MaterialTheme.typography.labelLarge,
            color = ext.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "These files will be sent with your next prompt.",
            style = MaterialTheme.typography.bodySmall,
            color = ext.textSecondary
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { attachment ->
                PendingAttachmentRow(
                    attachment = attachment,
                    onRemove = { onRemovePendingAttachment(attachment.id) }
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentRow(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    val ext = NanobotTheme.extendedColors
    val typeLabel = when (attachment.type) {
        AttachmentType.IMAGE -> "Image"
        AttachmentType.FILE -> "File"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = NanobotShapes.CardSmall,
        innerPadding = 10.dp,
        backgroundColor = ext.glassOverlay.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$typeLabel • ${attachment.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${attachment.mimeType} • ${attachment.sizeBytes} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = ext.textSecondary
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    text: String,
    textColor: Color,
    accentColor: Color
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = NanobotShapes.CardSmall,
        innerPadding = 12.dp,
        borderColor = accentColor.copy(alpha = 0.4f),
        highlighted = true
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

@Composable
private fun VoiceInputButton(
    state: VoiceState,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val ext = NanobotTheme.extendedColors

    Box(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            VoiceState.PROCESSING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = ext.neon
                )
            }

            VoiceState.SPEAKING -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Stop voice output",
                    tint = ext.neon
                )
            }

            VoiceState.LISTENING -> {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Finish voice input",
                    tint = ext.errorRed
                )
            }

            VoiceState.IDLE -> {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Voice input",
                    tint = ext.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supportingText: String,
    onClick: () -> Unit
) {
    val ext = NanobotTheme.extendedColors

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = NanobotShapes.CardSmall,
        innerPadding = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = NanobotShapes.Chip,
                color = ext.neonDim.copy(alpha = 0.3f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ext.neon,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ext.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textSecondary
                )
            }
        }
    }
}
