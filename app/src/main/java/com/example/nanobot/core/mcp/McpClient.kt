package com.example.nanobot.core.mcp

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

interface McpClient {
    suspend fun discoverTools(server: McpServerDefinition): List<McpToolDescriptor>
    suspend fun callTool(server: McpServerDefinition, remoteToolName: String, arguments: JsonObject): String
}

@Singleton
class HttpMcpClient @Inject constructor() : McpClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val baseHttpClient = OkHttpClient.Builder().build()

    override suspend fun discoverTools(server: McpServerDefinition): List<McpToolDescriptor> = withContext(Dispatchers.IO) {
        validateEndpoint(server)
        val response = postJson(server, buildRequestPayload("tools/list", buildJsonObject {}))
        val result = response["result"]?.jsonObject
            ?: throw IllegalStateException("MCP tools/list response was missing result.")
        val tools = result["tools"]?.jsonArray.orEmpty()

        tools.map { element ->
            parseToolDescriptor(server, element.jsonObject)
        }
    }

    override suspend fun callTool(server: McpServerDefinition, remoteToolName: String, arguments: JsonObject): String = withContext(Dispatchers.IO) {
        validateEndpoint(server)
        val response = postJson(
            server,
            buildRequestPayload(
                method = "tools/call",
                params = buildJsonObject {
                    put("name", remoteToolName)
                    put("arguments", arguments)
                }
            )
        )
        val result = response["result"]?.jsonObject
            ?: throw IllegalStateException("MCP tools/call response was missing result.")
        val content = result["content"]?.jsonArray.orEmpty()
        if (content.isEmpty()) {
            return@withContext "MCP tool '$remoteToolName' completed without content."
        }

        content.joinToString("\n") { item ->
            val obj = item.jsonObject
            val text = obj["text"]?.jsonPrimitive?.contentOrNull
            val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            text?.takeIf { it.isNotBlank() } ?: obj.toString().let {
                if (type.isBlank()) it else "$type: $it"
            }
        }
    }

    private fun parseToolDescriptor(server: McpServerDefinition, tool: JsonObject): McpToolDescriptor {
        val remoteName = tool["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(remoteName.isNotBlank()) { "MCP tool name cannot be blank." }
        val safeName = sanitizeName(remoteName)
        require(safeName.isNotBlank()) { "MCP tool name '$remoteName' is not supported." }
        val schemaElement = tool["inputSchema"]
        val inputSchema = when (schemaElement) {
            null -> buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
                put("required", buildJsonArray {})
            }
            is JsonObject -> schemaElement
            else -> throw IllegalArgumentException("MCP tool '$remoteName' returned a non-object input schema.")
        }
        require(isSupportedSchema(inputSchema)) { "MCP tool '$remoteName' returned an unsupported input schema." }
        return McpToolDescriptor(
            serverId = server.id,
            serverLabel = server.label,
            name = safeName,
            remoteName = remoteName,
            description = tool["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { "MCP tool exposed by ${server.label}" },
            inputSchema = inputSchema,
            readOnlyHint = parseReadOnlyHint(tool["annotations"] ?: tool["meta"])
        )
    }

    private fun parseReadOnlyHint(element: JsonElement?): Boolean? {
        val obj = element as? JsonObject ?: return null
        return obj["readOnlyHint"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
    }

    private fun isSupportedSchema(schema: JsonObject): Boolean {
        val type = schema["type"]?.jsonPrimitive?.contentOrNull
        return type == null || type == "object"
    }

    private fun sanitizeName(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
    }

    private suspend fun postJson(server: McpServerDefinition, payload: JsonObject): JsonObject {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt <= server.maxRetries) {
            try {
                return executePostJson(server, payload)
            } catch (throwable: Throwable) {
                lastError = throwable
                val shouldRetry = (throwable is RetryableMcpException || throwable is IOException) && attempt < server.maxRetries
                if (!shouldRetry) throw throwable
                delay(computeBackoffDelayMs(attempt, server.backoffBaseMs))
                attempt += 1
            }
        }

        throw lastError ?: IllegalStateException("MCP request failed.")
    }

    private fun executePostJson(server: McpServerDefinition, payload: JsonObject): JsonObject {
        val requestBuilder = Request.Builder()
            .url(server.endpoint)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
        applyAuth(requestBuilder, server)

        clientFor(server).newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = "MCP request failed with HTTP ${response.code}."
                if (response.code == 408 || response.code == 429 || response.code in 500..599) {
                    throw RetryableMcpException(message)
                }
                throw IllegalStateException(message)
            }
            if (body.isBlank()) {
                throw IllegalStateException("MCP response body was empty.")
            }
            return runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IllegalStateException("MCP response was not valid JSON.", it) }
        }
    }

    private fun clientFor(server: McpServerDefinition): OkHttpClient {
        return baseHttpClient.newBuilder()
            .connectTimeout(server.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(server.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(server.writeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(server.callTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private fun applyAuth(requestBuilder: Request.Builder, server: McpServerDefinition) {
        when (server.auth.type) {
            McpAuthType.NONE -> Unit
            McpAuthType.BEARER -> requestBuilder.header("Authorization", "Bearer ${server.auth.token.trim()}")
            McpAuthType.HEADER -> requestBuilder.header(server.auth.headerName.trim(), server.auth.headerValue)
        }
    }

    private fun computeBackoffDelayMs(attempt: Int, baseDelayMs: Long): Long {
        if (baseDelayMs <= 0L) return 0L
        val multiplier = 1L shl attempt.coerceAtMost(10)
        return (baseDelayMs * multiplier).coerceAtMost(MAX_BACKOFF_DELAY_MS)
    }

    private fun buildRequestPayload(method: String, params: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", JsonPrimitive(1))
        put("method", method)
        put("params", params)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val MAX_BACKOFF_DELAY_MS = 10_000L
    }

    private fun validateEndpoint(server: McpServerDefinition) {
        val raw = server.endpoint.trim()
        require(raw.isNotBlank()) { "${server.label}: endpoint is required." }
        val uri = runCatching { URI(raw) }
            .getOrElse { throw IllegalArgumentException("${server.label}: endpoint is not a valid URL.") }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "${server.label}: only http/https MCP endpoints are supported right now."
        }
        require(!uri.host.isNullOrBlank()) {
            "${server.label}: endpoint must include a host."
        }
        require(server.connectTimeoutSeconds > 0) {
            "${server.label}: connect timeout must be greater than 0 seconds."
        }
        require(server.readTimeoutSeconds > 0) {
            "${server.label}: read timeout must be greater than 0 seconds."
        }
        require(server.writeTimeoutSeconds > 0) {
            "${server.label}: write timeout must be greater than 0 seconds."
        }
        require(server.callTimeoutSeconds > 0) {
            "${server.label}: call timeout must be greater than 0 seconds."
        }
        require(server.maxRetries >= 0) {
            "${server.label}: max retries cannot be negative."
        }
        require(server.backoffBaseMs >= 0) {
            "${server.label}: backoff delay cannot be negative."
        }
        when (server.auth.type) {
            McpAuthType.NONE -> Unit
            McpAuthType.BEARER -> require(server.auth.token.isNotBlank()) {
                "${server.label}: bearer token is required when bearer auth is enabled."
            }
            McpAuthType.HEADER -> {
                require(server.auth.headerName.isNotBlank()) {
                    "${server.label}: header name is required when header auth is enabled."
                }
                require(server.auth.headerValue.isNotBlank()) {
                    "${server.label}: header value is required when header auth is enabled."
                }
            }
        }
    }
}

private class RetryableMcpException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
