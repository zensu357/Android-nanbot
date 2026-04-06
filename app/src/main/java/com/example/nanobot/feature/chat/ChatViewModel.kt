package com.example.nanobot.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.skills.ActivatedSkillSource
import com.example.nanobot.core.preferences.SettingsDataStore
import com.example.nanobot.core.skills.ActivatedSkillSessionStore
import com.example.nanobot.core.skills.SkillActivationFormatter
import com.example.nanobot.core.voice.VoiceEngine
import com.example.nanobot.core.voice.VoiceState
import com.example.nanobot.domain.repository.SkillRepository
import com.example.nanobot.domain.repository.SessionRepository
import com.example.nanobot.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val attachmentStore: AttachmentStore,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sessionRepository: SessionRepository,
    private val skillRepository: SkillRepository,
    private val skillActivationFormatter: SkillActivationFormatter,
    private val activatedSkillSessionStore: ActivatedSkillSessionStore,
    private val voiceEngine: VoiceEngine,
    settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val input = MutableStateFlow("")
    private val uiStateInternal = MutableStateFlow(ChatUiState())
    private var currentConfig: AgentConfig = AgentConfig()
    private var runningJob: Job? = null
    private var voiceInputJob: Job? = null
    private var lastSessionId: String? = null

    private val currentSession = sessionRepository.observeCurrentSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages = currentSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                sessionRepository.observeMessages(session.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<ChatUiState> = uiStateInternal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch {
            messages.collect { messageList ->
                updateState {
                    copy(
                        messages = messageList.filterNot { it.isHiddenSkillActivationMessage() },
                        sessionTitle = currentSession.value?.title ?: sessionTitle
                    )
                }
            }
        }
        viewModelScope.launch {
            input.collect { inputValue ->
                updateState { copy(input = inputValue) }
            }
        }
        viewModelScope.launch {
            currentSession.collect { session ->
                val sessionId = session?.id
                val sessionChanged = lastSessionId != null && sessionId != lastSessionId
                lastSessionId = sessionId
                updateState { applySessionSelection(session?.title ?: "New Chat", sessionChanged) }
            }
        }
        viewModelScope.launch {
            currentSession.flatMapLatest { session ->
                if (session == null) {
                    flowOf(emptyList())
                } else {
                    activatedSkillSessionStore.observeActivated(session.id)
                }
            }.collect { activated ->
                val titlesByName = uiStateInternal.value.availableSkills.associateBy { it.name }
                updateState {
                    copy(
                        activeSkills = activated.map { record ->
                            ActiveSkillUiState(
                                name = record.skillName,
                                title = titlesByName[record.skillName]?.title ?: record.skillName,
                                source = record.source
                            )
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            voiceEngine.state.collect { voiceState ->
                updateState {
                    copy(
                        voiceState = voiceState,
                        voiceStatusHint = voiceHintFor(voiceState)
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.configFlow.collect { config ->
                currentConfig = config
                val enabled = skillRepository.getEnabledSkills(config).map { skill ->
                    ActivatableSkillUiState(
                        name = skill.primaryActivationName(),
                        title = skill.title,
                        description = skill.description
                    )
                }
                val activeNames = uiStateInternal.value.activeSkills.map { it.name }.toSet()
                updateState {
                    copy(
                        availableSkills = enabled,
                        voiceInputEnabled = config.voiceInputEnabled,
                        voiceAutoPlayEnabled = config.voiceAutoPlay,
                        activeSkills = activeSkills.map { active ->
                            active.copy(title = enabled.firstOrNull { it.name == active.name }?.title ?: active.title)
                        }.filter { it.name in activeNames }
                    )
                }
            }
        }
    }

    fun onInputChanged(value: String) {
        input.value = value
    }

    fun sendMessage() {
        val content = input.value.trim()
        val attachments = uiStateInternal.value.pendingAttachments
        sendMessage(content, attachments)
    }

    fun toggleVoiceInput() {
        if (voiceEngine.state.value == VoiceState.LISTENING || voiceEngine.state.value == VoiceState.PROCESSING) {
            finishVoiceInput()
        } else {
            startVoiceInput()
        }
    }

    fun stopVoiceOutput() {
        voiceEngine.stopSpeaking()
    }

    fun startVoiceInput() {
        if (uiStateInternal.value.isRunning) return
        if (voiceInputJob?.isActive == true) return
        voiceInputJob = viewModelScope.launch {
            runCatching {
                val text = voiceEngine.listen()
                if (text.isNotBlank()) {
                    sendMessage(text.trim())
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                updateState {
                    copy(
                        errorMessage = throwable.message ?: "Voice input failed.",
                        voiceStatusHint = "Voice input failed"
                    )
                }
            }
        }.also { job ->
            job.invokeOnCompletion { voiceInputJob = null }
        }
    }

    fun finishVoiceInput() {
        voiceEngine.stopListening()
    }

    fun onVoicePermissionDenied() {
        updateState {
            copy(
                errorMessage = "Microphone permission is required for voice input.",
                voiceStatusHint = "Microphone permission required"
            )
        }
    }

    private fun sendMessage(
        content: String,
        attachments: List<com.example.nanobot.core.model.Attachment> = uiStateInternal.value.pendingAttachments
    ) {
        if ((content.isEmpty() && attachments.isEmpty()) || uiStateInternal.value.isRunning) {
            return
        }

        runningJob = viewModelScope.launch {
            voiceEngine.stopListening()
            updateState {
                copy(
                    isSending = true,
                    isRunning = true,
                    isCancelling = false,
                    statusText = "Starting...",
                    activeToolName = null,
                    errorMessage = null
                )
            }
            try {
                val turnMessages = sendMessageUseCase(content, attachments, currentConfig) { event ->
                    when (event) {
                        AgentProgressEvent.Started -> updateState {
                            copy(statusText = "Starting...", activeToolName = null)
                        }
                        AgentProgressEvent.Thinking -> updateState {
                            copy(statusText = "Thinking...", activeToolName = null)
                        }
                        is AgentProgressEvent.ToolCalling -> updateState {
                            copy(
                                statusText = "Calling tool: ${event.toolName}",
                                activeToolName = event.toolName
                            )
                        }
                        is AgentProgressEvent.ToolResult -> updateState {
                            copy(
                                statusText = "Finished tool: ${event.toolName}",
                                activeToolName = event.toolName
                            )
                        }
                        AgentProgressEvent.Finishing -> updateState {
                            copy(statusText = "Finishing response...", activeToolName = null)
                        }
                        AgentProgressEvent.Completed -> updateState {
                            copy(statusText = null, activeToolName = null)
                        }
                        AgentProgressEvent.Cancelled -> updateState {
                            copy(statusText = "Cancelled.", activeToolName = null)
                        }
                        is AgentProgressEvent.Error -> updateState {
                            copy(statusText = event.message)
                        }
                    }
                }
                maybeAutoSpeakAssistantReply(turnMessages)
                input.value = ""
                updateState { copy(statusText = null, pendingAttachments = emptyList()) }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    updateState {
                        copy(statusText = null, errorMessage = null)
                    }
                } else {
                    updateState {
                        copy(errorMessage = throwable.message ?: "Failed to send message.")
                    }
                }
            } finally {
                updateState {
                    copy(
                        isSending = false,
                        isRunning = false,
                        isCancelling = false,
                        activeToolName = null
                    )
                }
                runningJob = null
            }
        }
    }

    fun cancelSend() {
        val job = runningJob ?: return
        if (!job.isActive) return
        updateState { copy(isCancelling = true, statusText = "Cancelling...") }
        job.cancel(CancellationException("User cancelled the current run."))
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                attachmentStore.importImage(uri)
            }.onSuccess { attachment ->
                updateState {
                    copy(
                        pendingAttachments = pendingAttachments + attachment,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                updateState {
                    copy(errorMessage = throwable.message ?: "Failed to attach image.")
                }
            }
        }
    }

    fun attachFile(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                attachmentStore.importFile(uri)
            }.onSuccess { attachment ->
                updateState {
                    copy(
                        pendingAttachments = pendingAttachments + attachment,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                updateState {
                    copy(errorMessage = throwable.message ?: "Failed to attach file.")
                }
            }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        updateState {
            copy(
                pendingAttachments = pendingAttachments.filterNot { it.id == attachmentId }
            )
        }
    }

    fun toggleToolMessage(messageId: String) {
        updateState {
            toggleToolMessageExpansion(messageId)
        }
    }

    fun activateSkill(skillName: String) {
        viewModelScope.launch {
            runCatching {
                val session = sessionRepository.getOrCreateCurrentSession()
                val payload = skillRepository.activateSkill(skillName)
                    ?: throw IllegalArgumentException("Skill '$skillName' is unavailable.")
                val alreadyActivated = activatedSkillSessionStore.isActivated(
                    sessionId = session.id,
                    skillName = payload.skill.primaryActivationName(),
                    contentHash = payload.skill.contentHash
                )
                val effectiveAllowedTools = (
                    payload.skill.allowedTools + skillRepository.getHiddenToolEntitlements(payload.skill)
                    ).toSortedSet()
                val content = skillActivationFormatter.format(payload, alreadyActivated, effectiveAllowedTools)
                activatedSkillSessionStore.markActivated(
                    session.id,
                    payload.skill.primaryActivationName(),
                    payload.skill.contentHash,
                    ActivatedSkillSource.USER
                )
                sessionRepository.saveMessage(
                    com.example.nanobot.core.model.ChatMessage(
                        sessionId = session.id,
                        role = MessageRole.ASSISTANT,
                        content = content,
                        toolName = "activate_skill:${payload.skill.primaryActivationName()}",
                        protectedContext = true
                    )
                )
                sessionRepository.touchSession(session, makeCurrent = true)
            }.onFailure { throwable ->
                updateState {
                    copy(errorMessage = throwable.message ?: "Failed to activate skill.")
                }
            }
        }
    }

    fun deactivateSkill(skillName: String) {
        viewModelScope.launch {
            val session = currentSession.value ?: return@launch
            activatedSkillSessionStore.deactivate(session.id, skillName)
            updateState {
                copy(activeSkills = activeSkills.filterNot { it.name == skillName })
            }
        }
    }

    private fun updateState(transform: ChatUiState.() -> ChatUiState) {
        uiStateInternal.value = uiStateInternal.value.transform()
    }

    override fun onCleared() {
        voiceInputJob?.cancel()
        voiceEngine.release()
        super.onCleared()
    }

    private fun maybeAutoSpeakAssistantReply(turnMessages: List<com.example.nanobot.core.model.ChatMessage>) {
        if (!currentConfig.voiceAutoPlay) return
        val latestAssistantMessage = turnMessages.lastOrNull { message ->
            message.role == MessageRole.ASSISTANT &&
                !message.protectedContext &&
                message.toolCallsJson.isNullOrBlank() &&
                !message.content.isNullOrBlank()
        }
        val latestAssistant = latestAssistantMessage?.content.orEmpty().trim()
        if (latestAssistant.isBlank()) return
        viewModelScope.launch {
            runCatching {
                voiceEngine.speak(latestAssistant)
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                updateState {
                    copy(
                        errorMessage = throwable.message ?: "Voice playback failed.",
                        voiceStatusHint = "Voice playback failed"
                    )
                }
            }
        }
    }

    private fun voiceHintFor(state: VoiceState): String? {
        return when (state) {
            VoiceState.IDLE -> null
            VoiceState.LISTENING -> "Listening... tap to finish"
            VoiceState.PROCESSING -> "Transcribing voice input..."
            VoiceState.SPEAKING -> "Playing voice reply... tap to stop"
        }
    }
}

internal fun com.example.nanobot.core.model.ChatMessage.isHiddenSkillActivationMessage(): Boolean {
    if (!protectedContext) return false
    val name = toolName.orEmpty()
    return name.startsWith("activate_skill:")
}

internal fun ChatUiState.applySessionSelection(sessionTitle: String, sessionChanged: Boolean): ChatUiState {
    return copy(
        sessionTitle = sessionTitle,
        pendingAttachments = if (sessionChanged) emptyList() else pendingAttachments,
        expandedToolMessageIds = if (sessionChanged) emptySet() else expandedToolMessageIds,
        errorMessage = if (sessionChanged) null else errorMessage
    )
}
