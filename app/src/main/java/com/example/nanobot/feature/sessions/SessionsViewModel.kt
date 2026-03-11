package com.example.nanobot.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanobot.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val uiStateInternal = MutableStateFlow(SessionsUiState())

    private val sessions = sessionRepository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val currentSession = sessionRepository.observeCurrentSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<SessionsUiState> = combine(
        sessions,
        currentSession,
        uiStateInternal
    ) { allSessions, selectedSession, localState ->
        localState.copy(
            sessions = allSessions,
            currentSessionId = selectedSession?.id
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsUiState())

    fun createSession() {
        viewModelScope.launch {
            uiStateInternal.value = uiStateInternal.value.copy(isCreating = true, errorMessage = null)
            runCatching {
                sessionRepository.createSession()
            }.onFailure { throwable ->
                uiStateInternal.value = uiStateInternal.value.copy(
                    errorMessage = throwable.message ?: "Failed to create session."
                )
            }
            uiStateInternal.value = uiStateInternal.value.copy(isCreating = false)
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            uiStateInternal.value = uiStateInternal.value.copy(errorMessage = null)
            runCatching {
                sessionRepository.selectSession(sessionId)
            }.onFailure { throwable ->
                uiStateInternal.value = uiStateInternal.value.copy(
                    errorMessage = throwable.message ?: "Failed to switch session."
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            uiStateInternal.value = uiStateInternal.value.copy(isDeleting = true, errorMessage = null)
            runCatching {
                sessionRepository.deleteSession(sessionId)
            }.onFailure { throwable ->
                uiStateInternal.value = uiStateInternal.value.copy(
                    errorMessage = throwable.message ?: "Failed to delete session."
                )
            }
            uiStateInternal.value = uiStateInternal.value.copy(isDeleting = false)
        }
    }

    fun clearError() {
        uiStateInternal.value = uiStateInternal.value.copy(errorMessage = null)
    }
}
