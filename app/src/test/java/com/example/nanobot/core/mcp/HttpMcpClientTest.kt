package com.example.nanobot.core.mcp

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class HttpMcpClientTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun discoveryParsesToolsFromJsonRpcResponse() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "tools": [
                          {
                            "name": "Issue Search",
                            "description": "Search issues",
                            "inputSchema": {
                              "type": "object",
                              "properties": {
                                "query": { "type": "string" }
                              },
                              "required": ["query"]
                            },
                            "annotations": {
                              "readOnlyHint": true
                            }
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )

        val client = HttpMcpClient()
        val tools = client.discoverTools(
            McpServerDefinition(
                id = "github",
                label = "GitHub",
                endpoint = server.url("/mcp").toString(),
                enabled = true
            )
        )

        assertEquals(1, tools.size)
        assertEquals("issue_search", tools.single().name)
    }

    @Test
    fun discoveryRejectsNonObjectSchema() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "tools": [
                          {
                            "name": "Broken Tool",
                            "inputSchema": []
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )

        val client = HttpMcpClient()

        assertFailsWith<IllegalArgumentException> {
            client.discoverTools(
                McpServerDefinition(
                    id = "broken",
                    label = "Broken",
                    endpoint = server.url("/mcp").toString(),
                    enabled = true
                )
            )
        }
    }

    @Test
    fun discoveryAddsBearerAuthorizationHeader() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "tools": []
                      }
                    }
                    """.trimIndent()
                )
        )

        val client = HttpMcpClient()
        client.discoverTools(
            McpServerDefinition(
                id = "github",
                label = "GitHub",
                endpoint = server.url("/mcp").toString(),
                enabled = true,
                auth = McpAuthConfig(type = McpAuthType.BEARER, token = "secret-token")
            )
        )

        val request = assertNotNull(server.takeRequest())
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
    }

    @Test
    fun discoveryRetriesOnServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "tools": []
                      }
                    }
                    """.trimIndent()
                )
        )

        val client = HttpMcpClient()
        client.discoverTools(
            McpServerDefinition(
                id = "retry",
                label = "Retry",
                endpoint = server.url("/mcp").toString(),
                enabled = true,
                maxRetries = 1,
                backoffBaseMs = 0
            )
        )

        assertEquals(2, server.requestCount)
    }
}
