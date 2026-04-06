package com.example.nanobot.core.voice

import kotlinx.coroutines.flow.StateFlow

interface VoiceEngine {
    val state: StateFlow<VoiceState>

    suspend fun listen(): String

    suspend fun speak(text: String)

    fun stopListening()

    fun stopSpeaking()

    fun release()
}
