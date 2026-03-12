package com.example.nanobot.core.web

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class WebRequestGuard(
    private val delegateDns: Dns = Dns.SYSTEM,
    private val hostOverrides: Map<String, List<InetAddress>> = emptyMap(),
    private val allowPrivateHostsForTesting: Set<String> = emptySet()
) {
    fun validateUrl(
        rawUrl: String,
        allowResolvedPrivateHosts: Set<String> = emptySet(),
        resolveDns: Boolean = true
    ): HttpUrl {
        val normalized = rawUrl.trim()
        val httpUrl = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Only http:// and https:// URLs are allowed.")

        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            throw IllegalArgumentException("Only http:// and https:// URLs are allowed.")
        }

        validateHost(httpUrl.host, allowResolvedPrivateHosts)
        validateLiteralHostAddress(httpUrl.host)
        if (resolveDns) {
            validateResolvedAddresses(httpUrl.host, resolveAddresses(httpUrl.host), allowResolvedPrivateHosts)
        }
        return httpUrl
    }

    fun validateRedirectTarget(
        currentUrl: HttpUrl,
        locationHeader: String,
        allowResolvedPrivateHosts: Set<String> = emptySet(),
        resolveDns: Boolean = true
    ): HttpUrl {
        val redirectUrl = currentUrl.resolve(locationHeader)
            ?: throw IllegalArgumentException("Invalid redirect target.")

        validateHost(redirectUrl.host, allowResolvedPrivateHosts)
        validateLiteralHostAddress(redirectUrl.host)
        if (resolveDns) {
            validateResolvedAddresses(redirectUrl.host, resolveAddresses(redirectUrl.host), allowResolvedPrivateHosts)
        }
        return redirectUrl
    }

    fun validateResolvedAddresses(host: String, addresses: List<InetAddress>, allowResolvedPrivateHosts: Set<String> = emptySet()) {
        if (allowedAddresses(host, addresses, allowResolvedPrivateHosts).isEmpty()) {
            if (addresses.isEmpty()) {
                throw IllegalArgumentException("Unable to resolve host for web request.")
            }
            throw IllegalArgumentException("Private, loopback, link-local, and reserved network targets are not allowed.")
        }
    }

    fun lookupAndValidate(hostname: String, allowResolvedPrivateHosts: Set<String> = emptySet()): List<InetAddress> {
        val addresses = resolveAddresses(hostname)
        val allowed = allowedAddresses(hostname, addresses, allowResolvedPrivateHosts)
        validateResolvedAddresses(hostname, addresses, allowResolvedPrivateHosts)
        return allowed
    }

    private fun allowedAddresses(host: String, addresses: List<InetAddress>, allowResolvedPrivateHosts: Set<String>): List<InetAddress> {
        if (host.lowercase() in normalizedAllowedPrivateHosts(allowResolvedPrivateHosts)) {
            return addresses
        }
        if (addresses.isEmpty()) {
            return emptyList()
        }
        return addresses.filterNot { isBlockedAddress(it) }
    }

    private fun resolveAddresses(hostname: String): List<InetAddress> {
        val key = hostname.lowercase()
        return hostOverrides[key] ?: delegateDns.lookup(hostname)
    }

    private fun validateHost(host: String, allowResolvedPrivateHosts: Set<String> = emptySet()) {
        val normalized = host.lowercase()
        if (normalized in normalizedAllowedPrivateHosts(allowResolvedPrivateHosts)) {
            return
        }
        if (normalized == "localhost" || normalized.endsWith(".local")) {
            throw IllegalArgumentException("Localhost and local network hosts are not allowed.")
        }
    }

    private fun validateLiteralHostAddress(host: String) {
        val candidate = host.removePrefix("[").removeSuffix("]")
        if (!candidate.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' }) {
            return
        }
        val literalAddress = runCatching { InetAddress.getByName(candidate) }.getOrNull() ?: return
        if (isBlockedAddress(literalAddress)) {
            throw IllegalArgumentException("Private, loopback, link-local, and reserved network targets are not allowed.")
        }
    }

    private fun normalizedAllowedPrivateHosts(extraHosts: Set<String>): Set<String> {
        return allowPrivateHostsForTesting + extraHosts.map { it.lowercase() }
    }

    private fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        if (address is Inet4Address) {
            val bytes = address.address.map { it.toInt() and 0xFF }
            val first = bytes[0]
            val second = bytes[1]
            return (first == 100 && second in 64..127) ||
                (first == 198 && second in 18..19) ||
                (first == 192 && second == 0)
        }

        if (address is Inet6Address) {
            val firstByte = address.address.firstOrNull()?.toInt()?.and(0xFF) ?: return false
            if ((firstByte and 0xFE) == 0xFC) {
                return true
            }
        }

        return false
    }
}
