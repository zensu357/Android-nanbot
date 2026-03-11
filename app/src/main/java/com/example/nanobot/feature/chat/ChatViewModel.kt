package com.example.nanobot.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanobot.core.attachments.AttachmentStore
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.AgentProgressEvent
import com.example.nanobot.core.preferences.SettingsDataStore
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
    sessionRepository: SessionRepository,
    settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val input = MutableStateFlow("")
    private val uiStateInternal = MutableStateFlow(ChatUiState())
    private var currentConfig: AgentConfig = AgentConfig()
    private var runningJob: Job? = null

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
                        messages = messageList,
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
                updateState { copy(sessionTitle = session?.title ?: "New Chat") }
            }
        }
        viewModelScope.launch {
            settingsDataStore.configFlow.collect { config ->
                currentConfig = config
            }
        }
    }

    fun onInputChanged(value: String) {
        input.value = value
    }

    fun sendMessage() {
        val content = input.value.trim()
        val attachments = uiStateInternal.value.pendingAttachments
        if ((content.isEmpty() && attachments.isEmpty()) || uiStateInternal.value.isRunning) {
            return
        }

        runningJob = viewModelScope.launch {
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
                sendMessageUseCase(content, attachments, currentConfig) { event ->
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

    private fun updateState(transform: ChatUiState.() -> ChatUiState) {
        uiStateInternal.value = uiStateInternal.value.transform()
    }
}
