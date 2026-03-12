package com.example.nanobot.domain.usecase

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.example.nanobot.core.preferences.OnboardingStore
import com.example.nanobot.core.preferences.SettingsDataStore
import com.example.nanobot.domain.repository.SessionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompleteOnboardingUseCaseTest {
    @Test
    fun persistsProviderHintDuringOnboarding() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferencesDataStoreFile("nanobot_settings.preferences_pb").delete()
        context.preferencesDataStoreFile("nanobot_onboarding.preferences_pb").delete()
        val settingsStore = SettingsDataStore(context)
        val sessionRepository = FakeSessionRepository()
        val onboardingStore = OnboardingStore(context)
        val useCase = CompleteOnboardingUseCase(settingsStore, sessionRepository, onboardingStore)

        useCase(
            providerType = "openai_compatible",
            apiKey = "test-key",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            model = "gemini-2.5-flash",
            presetId = "assistant_default",
            systemPrompt = "You are helpful."
        )

        assertEquals("gemini-2.5-flash", settingsStore.configFlow.first().model)
    }

    private class FakeSessionRepository : SessionRepository {
        override fun observeCurrentSession() = throw UnsupportedOperationException()
        override fun observeSessions() = throw UnsupportedOperationException()
        override fun observeMessages(sessionId: String) = throw UnsupportedOperationException()
        override suspend fun observeSessionsSnapshot() = emptyList<com.example.nanobot.core.model.ChatSession>()
        override suspend fun getOrCreateCurrentSession() =
            com.example.nanobot.core.model.ChatSession(id = "session-1", title = "New Chat")
        override suspend fun getSessionByTitle(title: String) = null
        override suspend fun createSession(
            title: String,
            makeCurrent: Boolean,
            parentSessionId: String?,
            subagentDepth: Int
        ) = throw UnsupportedOperationException()
        override suspend fun upsertSession(
            session: com.example.nanobot.core.model.ChatSession,
            makeCurrent: Boolean
        ) = session
        override suspend fun selectSession(sessionId: String) = Unit
        override suspend fun deleteSession(sessionId: String) = Unit
        override suspend fun deleteSessionsOlderThan(cutoffMillis: Long) = Unit
        override suspend fun getMessages(sessionId: String) = emptyList<com.example.nanobot.core.model.ChatMessage>()
        override suspend fun getHistoryForModel(sessionId: String, maxMessages: Int) = emptyList<com.example.nanobot.core.model.ChatMessage>()
        override suspend fun saveMessage(message: com.example.nanobot.core.model.ChatMessage) = Unit
        override suspend fun touchSession(session: com.example.nanobot.core.model.ChatSession, makeCurrent: Boolean) = Unit
    }
}
