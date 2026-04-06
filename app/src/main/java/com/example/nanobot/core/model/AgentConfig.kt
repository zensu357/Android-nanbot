package com.example.nanobot.core.model

data class AgentConfig(
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/",
    val model: String = "gpt-4o-mini",
    val maxTokens: Int = 4096,
    val maxToolIterations: Int = 8,
    val memoryWindow: Int = 100,
    val reasoningEffort: String? = null,
    val enableTools: Boolean = true,
    val enableMemory: Boolean = true,
    val enableVisualMemory: Boolean = false,
    val enableBackgroundWork: Boolean = true,
    val webSearchApiKey: String = "",
    val webProxy: String = "",
    val restrictToWorkspace: Boolean = false,
    val presetId: String = "assistant_default",
    val enabledSkillIds: List<String> = emptyList(),
    val maxSubagentDepth: Int = 1,
    val systemPrompt: String = "You are Nanobot, a helpful Android-native assistant.",
    val temperature: Double = 0.2,
    val voiceInputEnabled: Boolean = false,
    val voiceAutoPlay: Boolean = false,
    val voiceEngine: VoiceEngineType = VoiceEngineType.ANDROID,
    val ttsSpeed: Float = 1.0f,
    val ttsLanguage: String = "zh-CN"
)
