package com.example.nanobot.core.ai.provider

import com.example.nanobot.core.model.ProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderRegistryTest {
    @Test
    fun resolvesOpenRouterFromApiKeyPrefix() {
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "sk-or-test",
            baseUrl = "",
            model = "openai/gpt-4o-mini",
            temperature = 0.2
        )

        assertEquals(ProviderType.OPEN_ROUTER, route.providerType)
        assertEquals("https://openrouter.ai/api/v1/", route.effectiveBaseUrl)
    }

    @Test
    fun enforcesMoonshotMinimumTemperature() {
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            baseUrl = "",
            model = "moonshot-v1-8k",
            temperature = 0.2
        )

        assertEquals(1.0, route.resolvedTemperature)
    }

    @Test
    fun marksOpenAiMetadataAsImageCapable() {
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            baseUrl = "",
            model = "gpt-4o-mini",
            temperature = 0.2
        )

        assertTrue(route.supportsImageAttachments)
    }

    @Test
    fun detectsGeminiFromBaseUrlEvenWhenModelNameLooksOpenAiLike() {
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            model = "gpt-4o-mini",
            temperature = 0.2
        )

        assertEquals("Gemini", route.providerLabel)
    }

    @Test
    fun detectsGeminiFromModelNameWhenConfiguredDirectly() {
        val route = ProviderRegistry.resolve(
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            model = "gemini-2.5-flash",
            temperature = 0.2
        )

        assertEquals("Gemini", route.providerLabel)
        assertTrue(route.supportsImageAttachments)
    }
}
