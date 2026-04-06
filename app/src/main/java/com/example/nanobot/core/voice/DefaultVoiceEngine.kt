package com.example.nanobot.core.voice

import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.core.preferences.SettingsConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

@Singleton
class DefaultVoiceEngine @Inject constructor(
    private val settingsConfigStore: SettingsConfigStore,
    @AndroidVoiceRecognizer private val androidSpeechRecognizer: SpeechRecognizer,
    @WhisperVoiceRecognizer private val whisperSpeechRecognizer: SpeechRecognizer,
    @AndroidVoiceSynthesizer private val androidSpeechSynthesizer: SpeechSynthesizer
) : VoiceEngine {
    private val operationMutex = Mutex()
    private val stateLock = Any()
    private val mutableState = MutableStateFlow(VoiceState.IDLE)
    override val state: StateFlow<VoiceState> = mutableState.asStateFlow()
    private var activeOperationId: Long = 0L
    private var activeVoiceState: VoiceState = VoiceState.IDLE

    override suspend fun listen(): String {
        stopSpeaking()
        return operationMutex.withLock {
            val config = settingsConfigStore.configFlow.first()
            val recognizer = when (config.voiceEngine) {
                VoiceEngineType.ANDROID -> androidSpeechRecognizer
                VoiceEngineType.WHISPER -> whisperSpeechRecognizer
            }
            val operationId = beginOperation(VoiceState.LISTENING)
            try {
                val recognized = recognizer.recognize(config.ttsLanguage).trim()
                moveOperationTo(operationId, from = VoiceState.LISTENING, to = VoiceState.PROCESSING)
                yield()
                recognized
            } finally {
                finishOperation(operationId)
            }
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        stopListening()
        operationMutex.withLock {
            val config = settingsConfigStore.configFlow.first()
            val operationId = beginOperation(VoiceState.SPEAKING)
            try {
                androidSpeechSynthesizer.speak(
                    text = text,
                    languageTag = config.ttsLanguage,
                    speed = config.ttsSpeed
                )
            } finally {
                finishOperation(operationId)
            }
        }
    }

    override fun stopListening() {
        androidSpeechRecognizer.stop()
        whisperSpeechRecognizer.stop()
        moveActiveState(from = VoiceState.LISTENING, to = VoiceState.PROCESSING)
    }

    override fun stopSpeaking() {
        androidSpeechSynthesizer.stop()
        moveActiveState(from = VoiceState.SPEAKING, to = VoiceState.IDLE)
    }

    override fun release() {
        androidSpeechRecognizer.cancel()
        whisperSpeechRecognizer.cancel()
        androidSpeechSynthesizer.stop()
        androidSpeechSynthesizer.release()
        synchronized(stateLock) {
            activeOperationId += 1L
            activeVoiceState = VoiceState.IDLE
            mutableState.value = VoiceState.IDLE
        }
    }

    private fun beginOperation(initialState: VoiceState): Long {
        synchronized(stateLock) {
            activeOperationId += 1L
            activeVoiceState = initialState
            mutableState.value = initialState
            return activeOperationId
        }
    }

    private fun moveOperationTo(operationId: Long, from: VoiceState, to: VoiceState) {
        synchronized(stateLock) {
            if (activeOperationId == operationId && activeVoiceState == from) {
                activeVoiceState = to
                mutableState.value = to
            }
        }
    }

    private fun moveActiveState(from: VoiceState, to: VoiceState) {
        synchronized(stateLock) {
            if (activeVoiceState == from) {
                activeVoiceState = to
                mutableState.value = to
            }
        }
    }

    private fun finishOperation(operationId: Long) {
        synchronized(stateLock) {
            if (activeOperationId == operationId) {
                activeVoiceState = VoiceState.IDLE
                mutableState.value = VoiceState.IDLE
            }
        }
    }
}
