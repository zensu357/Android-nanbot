package com.example.nanobot.core.mcp

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class HttpMcpClientValidationTest {
    private val client = HttpMcpClient()

    @Test
    fun discoverRejectsInvalidEndpointFormat() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            client.discoverTools(
                McpServerDefinition(
                    id = "bad",
                    label = "Bad Server",
                    endpoint = "not a url",
                    enabled = true
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("valid URL"))
    }

    @Test
    fun discoverRejectsUnsupportedScheme() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            client.discoverTools(
                McpServerDefinition(
                    id = "bad",
                    label = "Bad Server",
                    endpoint = "ftp://mcp.example/tools",
                    enabled = true
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("http/https"))
    }

    @Test
    fun discoverRejectsBearerAuthWithoutToken() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            client.discoverTools(
                McpServerDefinition(
                    id = "bad",
                    label = "Bad Server",
                    endpoint = "https://mcp.example/tools",
                    enabled = true,
                    auth = McpAuthConfig(type = McpAuthType.BEARER, token = "")
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("bearer token"))
    }

    @Test
    fun discoverRejectsNegativeBackoff() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            client.discoverTools(
                McpServerDefinition(
                    id = "bad",
                    label = "Bad Server",
                    endpoint = "https://mcp.example/tools",
                    enabled = true,
                    backoffBaseMs = -1
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("backoff"))
    }
}
