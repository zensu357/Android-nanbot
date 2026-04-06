package com.example.nanobot.core.voice

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.VoiceEngineType
import com.example.nanobot.core.preferences.SettingsConfigStore
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class VoiceEngineTest {
    @Test
    fun transitionsIdleListeningProcessingIdleAroundListen() = runTest {
        val settingsStore = FakeSettingsStore(
            AgentConfig(
                voiceEngine = VoiceEngineType.ANDROID,
                ttsLanguage = "zh-CN"
            )
        )
        val androidRecognizer = FakeSpeechRecognizer(result = "hello")
        val engine = DefaultVoiceEngine(
            settingsConfigStore = settingsStore,
            androidSpeechRecognizer = androidRecognizer,
            whisperSpeechRecognizer = FakeSpeechRecognizer(result = "unused"),
            androidSpeechSynthesizer = FakeSpeechSynthesizer()
        )

        val deferred = async { engine.listen() }
        testScheduler.advanceUntilIdle()

        assertEquals(VoiceState.IDLE, engine.state.value)
        assertEquals("hello", deferred.await())
        assertEquals(listOf("recognize:zh-CN"), androidRecognizer.calls)
    }

    @Test
    fun transitionsSpeakingIdleAroundSpeak() = runTest {
        val settingsStore = FakeSettingsStore(
            AgentConfig(
                voiceEngine = VoiceEngineType.ANDROID,
                ttsLanguage = "zh-CN",
                ttsSpeed = 1.25f
            )
        )
        val synthesizer = FakeSpeechSynthesizer()
        val engine = DefaultVoiceEngine(
            settingsConfigStore = settingsStore,
            androidSpeechRecognizer = FakeSpeechRecognizer(result = "unused"),
            whisperSpeechRecognizer = FakeSpeechRecognizer(result = "unused"),
            androidSpeechSynthesizer = synthesizer
        )

        engine.speak("Hi")

        assertEquals(VoiceState.IDLE, engine.state.value)
        assertEquals(listOf("speak:Hi:zh-CN:1.25"), synthesizer.calls)
    }

    @Test
    fun stopListeningTransitionsListeningToProcessing() = runTest {
        val settingsStore = FakeSettingsStore(
            AgentConfig(voiceEngine = VoiceEngineType.ANDROID)
        )
        val recognizer = BlockingSpeechRecognizer()
        val engine = DefaultVoiceEngine(
            settingsConfigStore = settingsStore,
            androidSpeechRecognizer = recognizer,
            whisperSpeechRecognizer = FakeSpeechRecognizer(result = "unused"),
            androidSpeechSynthesizer = FakeSpeechSynthesizer()
        )

        val deferred = async { engine.listen() }
        testScheduler.advanceUntilIdle()
        engine.stopListening()

        assertEquals(VoiceState.PROCESSING, engine.state.value)
        recognizer.resumeWith("done")
        assertEquals("done", deferred.await())
        assertEquals(VoiceState.IDLE, engine.state.value)
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

    private class FakeSpeechRecognizer(
        private val result: String
    ) : SpeechRecognizer {
        val calls = mutableListOf<String>()

        override suspend fun recognize(languageTag: String): String {
            calls += "recognize:$languageTag"
            return result
        }

        override fun stop() {
            calls += "stop"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private class FakeSpeechSynthesizer : SpeechSynthesizer {
        val calls = mutableListOf<String>()

        override suspend fun speak(text: String, languageTag: String, speed: Float) {
            calls += "speak:$text:$languageTag:$speed"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun release() {
            calls += "release"
        }
    }

    private class BlockingSpeechRecognizer : SpeechRecognizer {
        private lateinit var resumeBlock: (String) -> Unit
        val calls = mutableListOf<String>()

        override suspend fun recognize(languageTag: String): String {
            calls += "recognize:$languageTag"
            return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                resumeBlock = { result -> continuation.resume(result) }
            }
        }

        fun resumeWith(result: String) {
            val block = resumeBlock
            resumeBlock = {}
            block(result)
        }

        override fun stop() {
            calls += "stop"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }
}
