package com.example.nanobot.core.ai.provider

import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.ProviderType

data class ProviderSpec(
    val name: String,
    val keywords: Set<String> = emptySet(),
    val displayName: String = name,
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val isGateway: Boolean = false,
    val detectByKeyPrefix: String? = null,
    val detectByBaseKeyword: String? = null,
    val defaultBaseUrl: String? = null,
    val stripLeadingProviderPrefix: Boolean = false,
    val minTemperature: Double? = null,
    val supportsPromptCaching: Boolean = false,
    val requiresOpenAiChatEndpoint: Boolean = true,
    val supportsImageAttachments: Boolean = false
)

data class ResolvedProviderRoute(
    val spec: ProviderSpec,
    val providerType: ProviderType,
    val providerLabel: String,
    val effectiveBaseUrl: String,
    val resolvedModel: String,
    val resolvedTemperature: Double,
    val supportsPromptCaching: Boolean,
    val supportsImageAttachments: Boolean
)

object ProviderRegistry {
    private val providers: List<ProviderSpec> = listOf(
        ProviderSpec(
            name = "custom",
            displayName = "Custom",
            providerType = ProviderType.OPENAI_COMPATIBLE
        ),
        ProviderSpec(
            name = "azure_openai",
            keywords = setOf("azure", "azure-openai"),
            displayName = "Azure OpenAI",
            providerType = ProviderType.AZURE_OPENAI,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "openrouter",
            keywords = setOf("openrouter"),
            displayName = "OpenRouter",
            providerType = ProviderType.OPEN_ROUTER,
            isGateway = true,
            detectByKeyPrefix = "sk-or-",
            detectByBaseKeyword = "openrouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            supportsPromptCaching = true,
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "aihubmix",
            keywords = setOf("aihubmix"),
            displayName = "AiHubMix",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            isGateway = true,
            detectByBaseKeyword = "aihubmix",
            defaultBaseUrl = "https://aihubmix.com/v1",
            stripLeadingProviderPrefix = true,
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "siliconflow",
            keywords = setOf("siliconflow"),
            displayName = "SiliconFlow",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            isGateway = true,
            detectByBaseKeyword = "siliconflow",
            defaultBaseUrl = "https://api.siliconflow.cn/v1",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "volcengine",
            keywords = setOf("volcengine", "volces", "ark"),
            displayName = "VolcEngine",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            isGateway = true,
            detectByBaseKeyword = "volces",
            defaultBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "anthropic",
            keywords = setOf("anthropic", "claude"),
            displayName = "Anthropic",
            supportsPromptCaching = true
        ),
        ProviderSpec(
            name = "openai",
            keywords = setOf("openai", "gpt"),
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1/",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "deepseek",
            keywords = setOf("deepseek"),
            displayName = "DeepSeek",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "gemini",
            keywords = setOf("gemini"),
            displayName = "Gemini",
            detectByBaseKeyword = "generativelanguage.googleapis.com",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "zhipu",
            keywords = setOf("zhipu", "glm", "zai"),
            displayName = "Zhipu AI",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "dashscope",
            keywords = setOf("qwen", "dashscope"),
            displayName = "DashScope",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "moonshot",
            keywords = setOf("moonshot", "kimi"),
            displayName = "Moonshot",
            defaultBaseUrl = "https://api.moonshot.ai/v1",
            minTemperature = 1.0,
            requiresOpenAiChatEndpoint = true
        ),
        ProviderSpec(
            name = "minimax",
            keywords = setOf("minimax"),
            displayName = "MiniMax",
            defaultBaseUrl = "https://api.minimax.io/v1",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "vllm",
            keywords = setOf("vllm"),
            displayName = "vLLM/Local",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        ),
        ProviderSpec(
            name = "groq",
            keywords = setOf("groq"),
            displayName = "Groq",
            requiresOpenAiChatEndpoint = true,
            supportsImageAttachments = true
        )
    )

    private val gateways: List<ProviderSpec> = providers.filter { it.isGateway }
    private val metadataProviders: List<ProviderSpec> = providers.filter { !it.isGateway && it.providerType != ProviderType.AZURE_OPENAI }

    fun findByName(name: String?): ProviderSpec? {
        if (name.isNullOrBlank()) return null
        val normalized = name.lowercase().replace('-', '_')
        return providers.firstOrNull { it.name == normalized }
    }

    fun findByModel(model: String): ProviderSpec? {
        val modelLower = model.lowercase()
        val modelNormalized = modelLower.replace('-', '_')
        val modelPrefix = modelLower.substringBefore('/', missingDelimiterValue = "")
        val normalizedPrefix = modelPrefix.replace('-', '_')

        metadataProviders.firstOrNull { normalizedPrefix.isNotEmpty() && it.name == normalizedPrefix }?.let {
            return it
        }

        return metadataProviders.firstOrNull { spec ->
            spec.keywords.any { keyword ->
                keyword in modelLower || keyword.replace('-', '_') in modelNormalized
            }
        }
    }

    fun findGateway(apiKey: String?, apiBase: String?): ProviderSpec? {
        val normalizedBase = apiBase?.lowercase()
        return gateways.firstOrNull { spec ->
            (spec.detectByKeyPrefix != null && !apiKey.isNullOrBlank() && apiKey.startsWith(spec.detectByKeyPrefix)) ||
                (spec.detectByBaseKeyword != null && !normalizedBase.isNullOrBlank() && normalizedBase.contains(spec.detectByBaseKeyword))
        }
    }

    fun findByBaseUrl(apiBase: String?): ProviderSpec? {
        val normalizedBase = apiBase?.lowercase().orEmpty()
        if (normalizedBase.isBlank()) return null
        return metadataProviders.firstOrNull { spec ->
            spec.detectByBaseKeyword != null && normalizedBase.contains(spec.detectByBaseKeyword)
        }
    }

    fun resolve(
        config: AgentConfig
    ): ResolvedProviderRoute {
        return resolve(
            providerType = config.providerType,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            temperature = config.temperature
        )
    }

    fun resolve(
        providerType: ProviderType,
        apiKey: String,
        baseUrl: String,
        model: String,
        temperature: Double
    ): ResolvedProviderRoute {
        val normalizedModel = model.trim().ifBlank { "gpt-4o-mini" }
        val explicitSpec = when (providerType) {
            ProviderType.AZURE_OPENAI -> findByName("azure_openai")
            ProviderType.OPEN_ROUTER -> findByName("openrouter")
            ProviderType.OPENAI_COMPATIBLE -> null
        }
        val detectedGateway = if (providerType == ProviderType.OPENAI_COMPATIBLE) {
            findGateway(apiKey = apiKey, apiBase = baseUrl)
        } else {
            null
        }
        val detectedByBaseUrl = if (providerType == ProviderType.OPENAI_COMPATIBLE && detectedGateway == null) {
            findByBaseUrl(baseUrl)
        } else {
            null
        }
        val metadataSpec = explicitSpec ?: detectedGateway ?: detectedByBaseUrl ?: findByModel(normalizedModel) ?: findByName("custom")!!
        val resolvedProviderType = explicitSpec?.providerType ?: detectedGateway?.providerType ?: providerType
        val resolvedBaseUrl = baseUrl.trim().ifBlank {
            metadataSpec.defaultBaseUrl ?: "https://api.openai.com/v1/"
        }.cleanCustomBaseUrl()
        val resolvedModel = if (metadataSpec.stripLeadingProviderPrefix && '/' in normalizedModel) {
            normalizedModel.substringAfterLast('/')
        } else {
            normalizedModel
        }
        val resolvedTemperature = metadataSpec.minTemperature?.let { floor ->
            maxOf(floor, temperature)
        } ?: temperature

        return ResolvedProviderRoute(
            spec = metadataSpec,
            providerType = resolvedProviderType,
            providerLabel = metadataSpec.displayName,
            effectiveBaseUrl = resolvedBaseUrl,
            resolvedModel = resolvedModel,
            resolvedTemperature = resolvedTemperature,
            supportsPromptCaching = metadataSpec.supportsPromptCaching,
            supportsImageAttachments = metadataSpec.supportsImageAttachments
        )
    }
}

private fun String.cleanCustomBaseUrl(): String {
    var url = this.trim()
    url = url.removeSuffix("/chat/completions/")
    url = url.removeSuffix("/chat/completions")
    return if (url.endsWith('/')) url else "$url/"
}
