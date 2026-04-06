package com.example.nanobot.core.voice

import com.example.nanobot.core.preferences.SettingsConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WhisperSpeechRecognizer @Inject constructor(
    private val settingsConfigStore: SettingsConfigStore,
    private val audioRecorder: AudioRecorder,
    private val audioTranscriber: AudioTranscriber
) : SpeechRecognizer {
    override suspend fun recognize(languageTag: String): String {
        val config = settingsConfigStore.configFlow.first()
        val audioFile = audioRecorder.recordUntilStopped()
        return try {
            audioTranscriber.transcribe(audioFile, languageTag, config)
        } finally {
            runCatching { audioFile.delete() }
        }
    }

    override fun stop() = audioRecorder.stop()

    override fun cancel() = audioRecorder.cancel()
}
