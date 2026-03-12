package com.example.nanobot.core.web

import com.example.nanobot.core.model.AgentConfig
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Singleton
class WebAccessService @Inject constructor(
    private val configProvider: WebAccessConfigProvider,
    private val searchEndpointProvider: WebSearchEndpointProvider,
    private val safeDns: SafeDns,
    private val webRequestGuard: WebRequestGuard,
    private val webDiagnosticsStore: WebDiagnosticsStore
) {
    private val parserJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun fetch(url: String, maxChars: Int, overrideConfig: AgentConfig? = null): WebFetchResult = withContext(Dispatchers.IO) {
        val config = overrideConfig ?: configProvider.getConfig()
        val proxy = parseProxy(config.webProxy)
        publishDiagnostics(
            requestKind = "web_fetch",
            target = url,
            proxyValue = config.webProxy,
            dnsResolutionSkipped = proxy != null
        )
        val normalizedUrl = runCatching {
            webRequestGuard.validateUrl(url, resolveDns = proxy == null)
        }.getOrElse { throwable ->
            throw IllegalArgumentException(
                "Web fetch target '$url' was blocked by network safety rules: ${throwable.message}",
                throwable
            )
        }
        val charLimit = maxChars.coerceIn(200, MAX_FETCH_CHARS)
        val byteLimit = charLimit * MAX_BYTES_PER_CHAR

        executeWithRedirects(
            buildHttpClient(proxy),
            normalizedUrl,
            requestLabel = "web fetch target",
            resolveDns = proxy == null
        ) { resolvedUrl ->
            Request.Builder()
                .url(resolvedUrl)
                .get()
                .header("Accept", "text/html, text/plain, application/json, application/xml;q=0.9, */*;q=0.5")
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Web fetch failed with HTTP ${response.code}.")
            }

            val contentType = response.body?.contentType()?.toString()
            if (!isSupportedContentType(contentType)) {
                throw IllegalArgumentException("Unsupported web content type: ${contentType ?: "unknown"}.")
            }

            val rawBody = response.peekBody(byteLimit.toLong() + 1024).string()
            val extracted = extractReadableText(rawBody, contentType)
            val safeContent = extracted.take(charLimit)
            val truncated = extracted.length > charLimit || (response.body?.contentLength() ?: -1L) > byteLimit

            WebFetchResult(
                url = response.request.url.toString(),
                title = extractTitle(rawBody),
                contentType = contentType,
                content = safeContent,
                truncated = truncated
            )
        }
    }

    suspend fun search(query: String, limit: Int, overrideConfig: AgentConfig? = null): WebSearchResult = withContext(Dispatchers.IO) {
        val config = overrideConfig ?: configProvider.getConfig()
        val proxy = parseProxy(config.webProxy)
        val apiKey = config.webSearchApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Web search is not configured. Please set Web Search API Key in Settings.")
        }

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            throw IllegalArgumentException("Search query cannot be blank.")
        }

        val resultLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val rawSearchEndpoint = searchEndpointProvider.getSearchEndpoint()
        val allowedSearchHosts = allowedPrivateSearchHosts(rawSearchEndpoint)
        publishDiagnostics(
            requestKind = "web_search",
            target = normalizedQuery,
            endpoint = rawSearchEndpoint,
            proxyValue = config.webProxy,
            dnsResolutionSkipped = proxy != null,
            allowlistedHosts = allowedSearchHosts.toList()
        )
        val searchEndpoint = runCatching {
            webRequestGuard.validateUrl(
                rawSearchEndpoint,
                allowResolvedPrivateHosts = allowedSearchHosts,
                resolveDns = proxy == null
            )
        }.getOrElse { throwable ->
            throw IllegalArgumentException(
                "Configured web search endpoint '$rawSearchEndpoint' was blocked by network safety rules: ${throwable.message}",
                throwable
            )
        }
        val requestBody = """{"q":${parserJson.encodeToString(String.serializer(), normalizedQuery)},"num":$resultLimit}"""
            .toRequestBody("application/json".toMediaType())

        executeWithRedirects(
            buildHttpClient(proxy, allowedSearchHosts),
            searchEndpoint,
            allowResolvedPrivateHosts = allowedSearchHosts,
            requestLabel = "web search endpoint",
            resolveDns = proxy == null
        ) { resolvedUrl ->
            Request.Builder()
                .url(resolvedUrl)
                .post(requestBody)
                .header("X-API-KEY", apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
        }.use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Web search failed with HTTP ${response.code}.")
            }

            val payload = response.body?.string().orEmpty()
            val json = parserJson.parseToJsonElement(payload).jsonObject
            val results = json["organic"]
                ?.jsonArray
                ?.mapNotNull { element ->
                    val item = element.jsonObject
                    val link = item["link"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (link.isBlank()) return@mapNotNull null
                    WebSearchItem(
                        title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { link },
                        url = link,
                        snippet = item["snippet"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().take(MAX_SNIPPET_CHARS)
                    )
                }
                .orEmpty()
                .take(resultLimit)

            WebSearchResult(
                query = normalizedQuery,
                results = results
            )
        }
    }

    private fun buildHttpClient(proxy: Proxy?, allowResolvedPrivateHosts: Set<String> = emptySet()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(30))
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(
                if (proxy == null) {
                    object : Dns {
                        override fun lookup(hostname: String) =
                            webRequestGuard.lookupAndValidate(hostname, allowResolvedPrivateHosts)
                    }
                } else {
                    Dns.SYSTEM
                }
            )

        proxy?.let { builder.proxy(it) }
        return builder.build()
    }

    private fun allowedPrivateSearchHosts(endpoint: String): Set<String> {
        return setOfNotNull(endpoint.toHttpUrlOrNull()?.host)
    }

    private fun publishDiagnostics(
        requestKind: String,
        target: String,
        endpoint: String? = null,
        proxyValue: String,
        dnsResolutionSkipped: Boolean,
        allowlistedHosts: List<String> = emptyList()
    ) {
        webDiagnosticsStore.publish(
            WebDiagnosticsSnapshot(
                requestKind = requestKind,
                target = target,
                endpoint = endpoint,
                proxyConfigured = proxyValue.isNotBlank(),
                proxyValue = proxyValue.ifBlank { null },
                dnsResolutionSkipped = dnsResolutionSkipped,
                allowlistedHosts = allowlistedHosts
            )
        )
    }

    private fun parseProxy(proxyValue: String): Proxy? {
        val trimmed = proxyValue.trim()
        if (trimmed.isBlank()) return null

        SHORTHAND_PROXY_REGEX.matchEntire(trimmed)?.let { match ->
            val host = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid web proxy configuration.")
            return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        }

        val uri = runCatching { URI(trimmed) }.getOrElse {
            throw IllegalArgumentException("Invalid web proxy configuration.")
        }
        val host = uri.host ?: throw IllegalArgumentException("Invalid web proxy configuration.")
        val port = if (uri.port != -1) uri.port else DEFAULT_PROXY_PORT
        val type = when (uri.scheme?.lowercase()) {
            "socks", "socks5" -> Proxy.Type.SOCKS
            "http", "https" -> Proxy.Type.HTTP
            else -> throw IllegalArgumentException("Unsupported web proxy scheme.")
        }
        return Proxy(type, InetSocketAddress(host, port))
    }

    private fun executeWithRedirects(
        client: OkHttpClient,
        initialUrl: okhttp3.HttpUrl,
        allowResolvedPrivateHosts: Set<String> = emptySet(),
        requestLabel: String = "web request",
        resolveDns: Boolean = true,
        requestBuilder: (okhttp3.HttpUrl) -> Request
    ): Response {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = client.newCall(requestBuilder(currentUrl)).execute()
            if (!response.isRedirect) {
                return response
            }

            if (redirectCount >= MAX_REDIRECTS) {
                response.close()
                throw IOException("Too many redirects while performing web request.")
            }

            val location = response.header("Location")
                ?: run {
                    response.close()
                    throw IllegalStateException("Redirect response did not include a Location header.")
            }
            val nextUrl = runCatching {
                webRequestGuard.validateRedirectTarget(currentUrl, location, allowResolvedPrivateHosts, resolveDns)
            }.getOrElse { throwable ->
                throw IllegalArgumentException(
                    buildString {
                        append("Redirect target for $requestLabel was blocked by network safety rules: ")
                        append(throwable.message)
                        if (allowResolvedPrivateHosts.isNotEmpty()) {
                            append(". Only the configured endpoint host(s) ")
                            append(allowResolvedPrivateHosts.joinToString())
                            append(" are allowed to bypass private-address resolution checks.")
                        }
                    },
                    throwable
                )
            }
            response.close()
            currentUrl = nextUrl
        }

        throw IOException("Too many redirects while performing web request.")
    }

    private fun isSupportedContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return true
        val normalized = contentType.lowercase()
        return normalized.startsWith("text/") ||
            normalized.contains("html") ||
            normalized.contains("json") ||
            normalized.contains("xml")
    }

    private fun extractReadableText(raw: String, contentType: String?): String {
        val normalized = if (contentType.orEmpty().contains("html", ignoreCase = true)) {
            raw
                .replace(SCRIPT_REGEX, " ")
                .replace(STYLE_REGEX, " ")
                .replace(TAG_REGEX, " ")
        } else {
            raw
        }

        return decodeHtmlEntities(normalized)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun extractTitle(raw: String): String? {
        return TITLE_REGEX.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeHtmlEntities)
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.ifBlank { null }
    }

    private fun decodeHtmlEntities(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private companion object {
        const val USER_AGENT = "Nanobot-Android/1.0"
        const val MAX_FETCH_CHARS = 6_000
        const val MAX_BYTES_PER_CHAR = 4
        const val MAX_SEARCH_RESULTS = 8
        const val MAX_SNIPPET_CHARS = 220
        const val DEFAULT_PROXY_PORT = 8080
        const val MAX_REDIRECTS = 5
        val SHORTHAND_PROXY_REGEX = Regex("^([^:/\\s]+):(\\d+)$")

        val SCRIPT_REGEX = Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val STYLE_REGEX = Regex("<style\\b[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val TAG_REGEX = Regex("<[^>]+>")
        val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
