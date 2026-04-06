package com.example.nanobot.core.voice

import com.example.nanobot.core.ai.provider.ProviderRegistry
import com.example.nanobot.core.model.AgentConfig
import com.example.nanobot.core.model.ProviderType
import com.example.nanobot.core.network.HttpClientFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

@Singleton
class OpenAiCompatibleAudioTranscriber @Inject constructor(
    httpClientFactory: HttpClientFactory
) : AudioTranscriber {
    private val client = httpClientFactory.audioClient()

    override suspend fun transcribe(audioFile: File, languageTag: String, config: AgentConfig): String = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("Whisper voice input requires an API key.")
        }
        val route = ProviderRegistry.resolve(config)
        if (route.providerType == ProviderType.AZURE_OPENAI) {
            throw IllegalStateException("Whisper voice input is not configured for Azure OpenAI in this build. Use an OpenAI-compatible endpoint instead.")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("language", languageTag.substringBefore('-').ifBlank { "zh" })
            .addFormDataPart("response_format", "json")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(route.effectiveBaseUrl.cleanAudioBaseUrl() + "audio/transcriptions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = body.ifBlank { response.message }
                throw IllegalStateException("Whisper transcription failed with HTTP ${response.code}: $detail")
            }
            val parsed = runCatching { whisperJson.decodeFromString(WhisperTranscriptionResponse.serializer(), body) }
                .getOrElse {
                    throw IllegalStateException("Whisper transcription returned an unreadable response.")
                }
            parsed.text.trim()
        }
    }

    private fun String.cleanAudioBaseUrl(): String {
        var url = trim()
        url = url.removeSuffix("/chat/completions/")
        url = url.removeSuffix("/chat/completions")
        url = url.removeSuffix("/audio/transcriptions/")
        url = url.removeSuffix("/audio/transcriptions")
        url = url.removeSuffix("/openai/deployments")
        return if (url.endsWith('/')) url else "$url/"
    }

    @Serializable
    private data class WhisperTranscriptionResponse(
        val text: String = ""
    )

    private companion object {
        val whisperJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
