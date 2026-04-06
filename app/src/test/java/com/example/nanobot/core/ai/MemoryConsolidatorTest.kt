package com.example.nanobot.core.ai

import com.example.nanobot.core.memory.VisualMemoryExtractor
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import com.example.nanobot.core.model.ChatMessage
import com.example.nanobot.core.model.LlmChatRequest
import com.example.nanobot.core.model.MemoryFact
import com.example.nanobot.core.model.MemorySummary
import com.example.nanobot.core.model.MessageRole
import com.example.nanobot.core.model.ProviderChatResult
import com.example.nanobot.domain.repository.ChatRepository
import com.example.nanobot.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryConsolidatorTest {
    @Test
    fun shouldConsolidateWhenNoSummaryExistsAndEnoughMessages() = runTest {
        val repository = FakeMemoryRepository()
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = FakeChatRepository(),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(FakeChatRepository())
        )

        val result = consolidator.shouldConsolidate(
            sessionId = "session-1",
            historySize = 4,
            config = AgentConfig(enableMemory = true),
            minMessages = 4,
            minNewMessagesDelta = 2
        )

        assertTrue(result)
    }

    @Test
    fun shouldNotConsolidateWhenNewMessageDeltaIsTooSmall() = runTest {
        val repository = FakeMemoryRepository().apply {
            summary = MemorySummary(
                sessionId = "session-1",
                summary = "Existing summary",
                updatedAt = 123L,
                sourceMessageCount = 5
            )
        }
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = FakeChatRepository(),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(FakeChatRepository())
        )

        val result = consolidator.shouldConsolidate(
            sessionId = "session-1",
            historySize = 6,
            config = AgentConfig(enableMemory = true),
            minMessages = 4,
            minNewMessagesDelta = 2
        )

        assertFalse(result)
    }

    @Test
    fun consolidatesSummaryAndFactCandidate() = runTest {
        val repository = FakeMemoryRepository()
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = FakeChatRepository(),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(FakeChatRepository())
        )

        val result = consolidator.consolidate(
            sessionId = "session-1",
            history = listOf(
                ChatMessage(id = "msg-1", sessionId = "session-1", role = MessageRole.USER, content = "I am building an Android assistant."),
                ChatMessage(id = "msg-2", sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Nice."),
                ChatMessage(id = "msg-3", sessionId = "session-1", role = MessageRole.USER, content = "Please remember that I prefer Kotlin."),
                ChatMessage(id = "msg-4", sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Understood.")
            ),
            config = AgentConfig(enableMemory = true)
        )

        assertTrue(result)
        assertNotNull(repository.summary)
        assertTrue(repository.summary!!.summary.contains("Android assistant"))
        assertEquals("The user is building an Android assistant.", repository.facts.single().fact)
        assertEquals(65, (repository.facts.single().confidence * 100).toInt())
        assertTrue(repository.facts.single().provenance.messageIds.isNotEmpty())
        assertTrue(repository.summary!!.provenance.evidenceExcerpt.orEmpty().isNotBlank())
    }

    @Test
    fun buildMemoryContextSeparatesSessionAndLongTermFacts() = runTest {
        val repository = FakeMemoryRepository().apply {
            summary = MemorySummary(
                sessionId = "session-1",
                summary = "Current task is building an Android assistant.",
                updatedAt = 123L,
                sourceMessageCount = 8
            )
            facts += MemoryFact(
                id = "fact-1",
                fact = "The user prefers Kotlin.",
                sourceSessionId = "session-1",
                createdAt = 1L,
                updatedAt = 10L,
                confidence = 0.82f
            )
            facts += MemoryFact(
                id = "fact-2",
                fact = "The user likes concise answers.",
                sourceSessionId = "session-2",
                createdAt = 2L,
                updatedAt = 20L,
                confidence = 0.74f
            )
        }
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = FakeChatRepository(),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(FakeChatRepository())
        )

        val context = consolidator.buildMemoryContext("session-1")

        assertNotNull(context)
        assertTrue(context.contains("Session summary:"))
        assertTrue(context.contains("Current session facts:"))
        assertTrue(context.contains("Long-term user facts:"))
        assertTrue(context.contains("The user prefers Kotlin."))
        assertTrue(context.contains("The user likes concise answers."))
        assertTrue(context.contains("confidence="))
    }

    @Test
    fun consolidateRefreshesDuplicateFactInsteadOfCreatingNewOne() = runTest {
        val repository = FakeMemoryRepository().apply {
            facts += MemoryFact(
                id = "fact-1",
                fact = "The user is building an Android assistant.",
                sourceSessionId = "session-1",
                createdAt = 1L,
                updatedAt = 2L
            )
        }
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = FakeChatRepository(),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(FakeChatRepository())
        )

        val result = consolidator.consolidate(
            sessionId = "session-1",
            history = listOf(
                ChatMessage(sessionId = "session-1", role = MessageRole.USER, content = "I am building an Android assistant."),
                ChatMessage(sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Nice."),
                ChatMessage(sessionId = "session-1", role = MessageRole.USER, content = "Please remember that I prefer Kotlin."),
                ChatMessage(sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Understood.")
            ),
            config = AgentConfig(enableMemory = true)
        )

        assertTrue(result)
        assertEquals(1, repository.facts.size)
        assertEquals("fact-1", repository.facts.single().id)
    }

    @Test
    fun consolidateReplacesConflictingPreferenceFact() = runTest {
        val repository = FakeMemoryRepository().apply {
            facts += MemoryFact(
                id = "fact-pref",
                fact = "The user prefers Kotlin for Android projects.",
                sourceSessionId = "session-1",
                createdAt = 1L,
                updatedAt = 2L
            )
        }
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = ParameterizedChatRepository(
                """
                    {"updatedSummary":"The user now prefers Java for Android projects.","candidateFacts":["The user prefers Java for Android projects."]}
                """.trimIndent()
            ),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(ParameterizedChatRepository(
                """
                    {"updatedSummary":"The user now prefers Java for Android projects.","candidateFacts":["The user prefers Java for Android projects."]}
                """.trimIndent()
            ))
        )

        val result = consolidator.consolidate(
            sessionId = "session-2",
            history = listOf(
                ChatMessage(sessionId = "session-2", role = MessageRole.USER, content = "I prefer Java for Android projects now."),
                ChatMessage(sessionId = "session-2", role = MessageRole.ASSISTANT, content = "Understood."),
                ChatMessage(sessionId = "session-2", role = MessageRole.USER, content = "Please update that preference."),
                ChatMessage(sessionId = "session-2", role = MessageRole.ASSISTANT, content = "Done.")
            ),
            config = AgentConfig(enableMemory = true)
        )

        assertTrue(result)
        assertEquals(1, repository.facts.size)
        assertEquals("fact-pref", repository.facts.single().id)
        assertEquals("The user prefers Java for Android projects.", repository.facts.single().fact)
        assertEquals("session-2", repository.facts.single().sourceSessionId)
    }

    @Test
    fun consolidateUsesStructuredExplainabilityMetadataWhenProvided() = runTest {
        val repository = FakeMemoryRepository()
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = ParameterizedChatRepository(
                """
                    {
                      "updatedSummary":"The user is evaluating Kotlin for Android work.",
                      "summaryConfidence":0.91,
                      "summaryEvidenceExcerpt":"I am building an Android assistant and I prefer Kotlin.",
                      "summarySourceMessageIds":["msg-1","msg-3"],
                      "structuredFacts":[
                        {
                          "fact":"The user prefers Kotlin.",
                          "confidence":0.88,
                          "evidenceExcerpt":"Please remember that I prefer Kotlin.",
                          "sourceMessageIds":["msg-3"]
                        }
                      ]
                    }
                """.trimIndent()
            ),
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(ParameterizedChatRepository(
                """
                    {
                      "updatedSummary":"The user is evaluating Kotlin for Android work.",
                      "summaryConfidence":0.91,
                      "summaryEvidenceExcerpt":"I am building an Android assistant and I prefer Kotlin.",
                      "summarySourceMessageIds":["msg-1","msg-3"],
                      "structuredFacts":[
                        {
                          "fact":"The user prefers Kotlin.",
                          "confidence":0.88,
                          "evidenceExcerpt":"Please remember that I prefer Kotlin.",
                          "sourceMessageIds":["msg-3"]
                        }
                      ]
                    }
                """.trimIndent()
            ))
        )

        val result = consolidator.consolidate(
            sessionId = "session-1",
            history = listOf(
                ChatMessage(id = "msg-1", sessionId = "session-1", role = MessageRole.USER, content = "I am building an Android assistant."),
                ChatMessage(id = "msg-2", sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Nice."),
                ChatMessage(id = "msg-3", sessionId = "session-1", role = MessageRole.USER, content = "Please remember that I prefer Kotlin."),
                ChatMessage(id = "msg-4", sessionId = "session-1", role = MessageRole.ASSISTANT, content = "Understood.")
            ),
            config = AgentConfig(enableMemory = true)
        )

        assertTrue(result)
        assertEquals(0.91f, repository.summary!!.confidence)
        assertEquals(listOf("msg-1", "msg-3"), repository.summary!!.provenance.messageIds)
        assertEquals(0.88f, repository.facts.single().confidence)
        assertEquals(listOf("msg-3"), repository.facts.single().provenance.messageIds)
    }

    @Test
    fun consolidateExtractsVisualFactsFromLatestScreenshotAttachment() = runTest {
        val repository = FakeMemoryRepository()
        val chatRepository = SequencedChatRepository(
            responses = listOf(
                ProviderChatResult(
                    content = """
                        {"updatedSummary":"The user is reviewing app settings.","candidateFacts":[]}
                    """.trimIndent()
                ),
                ProviderChatResult(
                    content = """
                        [{"fact":"The Settings app shows Wi-Fi is enabled.","confidence":0.82}]
                    """.trimIndent()
                )
            )
        )
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = chatRepository,
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(chatRepository)
        )

        val result = consolidator.consolidate(
            sessionId = "session-visual",
            history = listOf(
                ChatMessage(id = "msg-1", sessionId = "session-visual", role = MessageRole.USER, content = "Check whether Wi-Fi is on."),
                ChatMessage(id = "msg-2", sessionId = "session-visual", role = MessageRole.ASSISTANT, content = "I'll inspect the current screen."),
                ChatMessage(
                    id = "msg-3",
                    sessionId = "session-visual",
                    role = MessageRole.TOOL,
                    content = "Screenshot captured.",
                    toolName = "take_screenshot",
                    attachments = listOf(
                        Attachment(
                            id = "att-1",
                            type = AttachmentType.IMAGE,
                            displayName = "screen.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 3,
                            localPath = "attachments/images/screen.jpg"
                        )
                    )
                ),
                ChatMessage(id = "msg-4", sessionId = "session-visual", role = MessageRole.ASSISTANT, content = "The screenshot is ready.")
            ),
            config = AgentConfig(enableMemory = true, enableVisualMemory = true, model = "gpt-4o-mini")
        )

        assertTrue(result)
        assertTrue(repository.facts.any { it.fact == "The Settings app shows Wi-Fi is enabled." })
        val visualFact = repository.facts.first { it.fact == "The Settings app shows Wi-Fi is enabled." }
        assertEquals("visual_extraction", visualFact.provenance.sourceKind)
        assertEquals("visual_memory_extractor", visualFact.provenance.extractor)
        assertEquals(listOf("msg-3"), visualFact.provenance.messageIds)
        assertTrue(chatRepository.requests.last().messages.last().attachments.isNotEmpty())
    }

    @Test
    fun consolidateSkipsVisualExtractionWhenVisualMemoryDisabled() = runTest {
        val repository = FakeMemoryRepository()
        val chatRepository = SequencedChatRepository(
            responses = listOf(
                ProviderChatResult(content = "{" + "\"updatedSummary\":\"Summary\",\"candidateFacts\":[]}"),
                ProviderChatResult(content = "[{\"fact\":\"Should not be used\",\"confidence\":0.9}]")
            )
        )
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = chatRepository,
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(chatRepository)
        )

        consolidator.consolidate(
            sessionId = "session-visual-disabled",
            history = screenshotHistory("session-visual-disabled"),
            config = AgentConfig(enableMemory = true, enableVisualMemory = false, model = "gpt-4o-mini")
        )

        assertEquals(1, chatRepository.requests.size)
        assertTrue(repository.facts.none { it.provenance.sourceKind == "visual_extraction" })
    }

    @Test
    fun consolidateSkipsDuplicateVisualExtractionForSameScreenshotMessage() = runTest {
        val repository = FakeMemoryRepository().apply {
            facts += MemoryFact(
                id = "visual-fact-existing",
                fact = "The Settings app shows Wi-Fi is enabled.",
                sourceSessionId = "session-visual",
                createdAt = 1L,
                updatedAt = 2L,
                confidence = 0.8f,
                provenance = com.example.nanobot.core.model.MemoryProvenance(
                    messageIds = listOf("msg-3"),
                    sourceKind = "visual_extraction",
                    extractor = "visual_memory_extractor"
                )
            )
        }
        val chatRepository = SequencedChatRepository(
            responses = listOf(
                ProviderChatResult(content = "{" + "\"updatedSummary\":\"Summary\",\"candidateFacts\":[]}")
            )
        )
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = chatRepository,
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(chatRepository)
        )

        consolidator.consolidate(
            sessionId = "session-visual",
            history = screenshotHistory("session-visual"),
            config = AgentConfig(enableMemory = true, enableVisualMemory = true, model = "gpt-4o-mini")
        )

        assertEquals(1, chatRepository.requests.size)
        assertEquals(1, repository.facts.count { it.provenance.sourceKind == "visual_extraction" })
    }

    @Test
    fun consolidateFiltersOutLowConfidenceVisualFacts() = runTest {
        val repository = FakeMemoryRepository()
        val chatRepository = SequencedChatRepository(
            responses = listOf(
                ProviderChatResult(content = "{" + "\"updatedSummary\":\"Summary\",\"candidateFacts\":[]}"),
                ProviderChatResult(content = "[{\"fact\":\"Low confidence fact\",\"confidence\":0.4}]")
            )
        )
        val consolidator = MemoryConsolidator(
            memoryRepository = repository,
            chatRepository = chatRepository,
            memoryPromptBuilder = MemoryPromptBuilder(),
            visualMemoryExtractor = VisualMemoryExtractor(chatRepository)
        )

        consolidator.consolidate(
            sessionId = "session-visual-low",
            history = screenshotHistory("session-visual-low"),
            config = AgentConfig(enableMemory = true, enableVisualMemory = true, model = "gpt-4o-mini")
        )

        assertTrue(repository.facts.none { it.fact == "Low confidence fact" })
    }

    private class FakeMemoryRepository : MemoryRepository {
        val facts = mutableListOf<MemoryFact>()
        var summary: MemorySummary? = null

        override fun observeFacts(): Flow<List<MemoryFact>> = flowOf(facts)
        override fun observeSummaries(): Flow<List<MemorySummary>> = flowOf(listOfNotNull(summary))
        override suspend fun getFacts(): List<MemoryFact> = facts
        override suspend fun getFactsForSession(sessionId: String): List<MemoryFact> = facts.filter { it.sourceSessionId == sessionId }
        override suspend fun getAllSummaries(): List<MemorySummary> = listOfNotNull(summary)
        override suspend fun getFactsForQuery(query: String): List<MemoryFact> =
            facts.filter { it.fact.contains(query, ignoreCase = true) }
        override suspend fun observeSummariesSnapshot(): List<MemorySummary> = listOfNotNull(summary)
        override suspend fun getSummaryForSession(sessionId: String): MemorySummary? = summary
        override suspend fun deleteFact(factId: String) {
            facts.removeAll { it.id == factId }
        }
        override suspend fun deleteSummary(sessionId: String) {
            if (summary?.sessionId == sessionId) {
                summary = null
            }
        }
        override suspend fun pruneFacts(maxFacts: Int) {
            if (maxFacts >= 0 && facts.size > maxFacts) {
                val trimmed = facts.sortedByDescending { it.updatedAt }.take(maxFacts)
                facts.clear()
                facts += trimmed
            }
        }
        override suspend fun upsertFact(fact: MemoryFact) {
            facts.removeAll { it.id == fact.id }
            facts += fact
        }
        override suspend fun upsertSummary(summary: MemorySummary) {
            this.summary = summary
        }
    }

    private class FakeChatRepository : ChatRepository {
        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            return ProviderChatResult(
                content = """
                    {"updatedSummary":"The user is building an Android assistant and prefers Kotlin.","summaryConfidence":0.75,"summaryEvidenceExcerpt":"I am building an Android assistant.","summarySourceMessageIds":["msg-1","msg-3"],"candidateFacts":["The user is building an Android assistant."]}
                """.trimIndent()
            )
        }
    }

    private class ParameterizedChatRepository(
        private val responseText: String
    ) : ChatRepository {
        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            return ProviderChatResult(content = responseText)
        }
    }

    private class SequencedChatRepository(
        private val responses: List<ProviderChatResult>
    ) : ChatRepository {
        val requests = mutableListOf<LlmChatRequest>()
        private var index = 0

        override suspend fun completeChat(request: LlmChatRequest, config: AgentConfig): ProviderChatResult {
            requests += request
            return responses.getOrElse(index++) { responses.last() }
        }
    }

    private fun screenshotHistory(sessionId: String): List<ChatMessage> {
        return listOf(
            ChatMessage(id = "msg-1", sessionId = sessionId, role = MessageRole.USER, content = "Check whether Wi-Fi is on."),
            ChatMessage(id = "msg-2", sessionId = sessionId, role = MessageRole.ASSISTANT, content = "I'll inspect the current screen."),
            ChatMessage(
                id = "msg-3",
                sessionId = sessionId,
                role = MessageRole.TOOL,
                content = "Screenshot captured.",
                toolName = "take_screenshot",
                attachments = listOf(
                    Attachment(
                        id = "att-1",
                        type = AttachmentType.IMAGE,
                        displayName = "screen.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 3,
                        localPath = "attachments/images/screen.jpg"
                    )
                )
            ),
            ChatMessage(id = "msg-4", sessionId = sessionId, role = MessageRole.ASSISTANT, content = "The screenshot is ready.")
        )
    }
}
