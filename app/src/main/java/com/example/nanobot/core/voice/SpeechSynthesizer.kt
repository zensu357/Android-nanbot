package com.example.nanobot.core.voice

interface SpeechSynthesizer {
    suspend fun speak(text: String, languageTag: String, speed: Float)

    fun stop()

    fun release()
}
