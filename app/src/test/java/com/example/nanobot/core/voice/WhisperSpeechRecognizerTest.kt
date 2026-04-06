package com.example.nanobot.core.voice

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.core.preferences.SettingsConfigStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class WhisperSpeechRecognizerTest {
    @Test
    fun recordsThenTranscribesAudioWithCurrentConfig() = runTest {
        val tempFile = File.createTempFile("voice-test-", ".m4a")
        val recorder = FakeAudioRecorder(tempFile)
        val transcriber = FakeAudioTranscriber()
        val recognizer = WhisperSpeechRecognizer(
            settingsConfigStore = FakeSettingsStore(
                AgentConfig(
                    apiKey = "sk-test",
                    voiceEngine = VoiceEngineType.WHISPER,
                    ttsLanguage = "en-US"
                )
            ),
            audioRecorder = recorder,
            audioTranscriber = transcriber
        )

        val result = recognizer.recognize("en-US")

        assertEquals("transcribed text", result)
        assertEquals(listOf("record", "transcribe:en-US:sk-test"), recorder.calls + transcriber.calls)
    }

    private class FakeSettingsStore(initial: AgentConfig) : SettingsConfigStore {
        override val configFlow: Flow<AgentConfig> = MutableStateFlow(initial)
        override val skillsDirectoryUriFlow: Flow<String?> = MutableStateFlow(null)
        override val skillRootsFlow: Flow<List<String>> = MutableStateFlow(emptyList())
        override val trustProjectSkillsFlow: Flow<Boolean> = MutableStateFlow(false)

        override suspend fun save(config: AgentConfig) = Unit
        override suspend fun saveSkillsDirectoryUri(uri: String?) = Unit
        override suspend fun addSkillRootUri(uri: String) = Unit
        override suspend fun removeSkillRootUri(uri: String) = Unit
        override suspend fun setTrustProjectSkills(trusted: Boolean) = Unit
    }

    private class FakeAudioRecorder(
        private val file: File
    ) : AudioRecorder {
        val calls = mutableListOf<String>()

        override suspend fun recordUntilStopped(): File {
            calls += "record"
            return file
        }

        override fun stop() {
            calls += "stop"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private class FakeAudioTranscriber : AudioTranscriber {
        val calls = mutableListOf<String>()

        override suspend fun transcribe(audioFile: File, languageTag: String, config: AgentConfig): String {
            calls += "transcribe:$languageTag:${config.apiKey}"
            return "transcribed text"
        }
    }
}
