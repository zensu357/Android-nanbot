package com.example.nanobot.core.voice

interface SpeechRecognizer {
    suspend fun recognize(languageTag: String): String

    fun stop()

    fun cancel()
}
