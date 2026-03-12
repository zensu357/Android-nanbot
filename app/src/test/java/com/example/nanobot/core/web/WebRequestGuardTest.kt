package com.example.nanobot.core.web

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebRequestGuardTest {
    @Test
    fun lookupAndValidateAllowsPublicAddressesWhenMixedWithBlockedOnes() {
        val publicIp = InetAddress.getByName("93.184.216.34")
        val privateIp = InetAddress.getByName("127.0.0.1")
        val guard = WebRequestGuard(
            hostOverrides = mapOf("mixed.example" to listOf(privateIp, publicIp))
        )

        val allowed = guard.lookupAndValidate("mixed.example")

        assertEquals(listOf(publicIp), allowed)
    }

    @Test
    fun lookupAndValidateRejectsWhenAllAddressesAreBlocked() {
        val guard = WebRequestGuard(
            hostOverrides = mapOf("private.example" to listOf(InetAddress.getByName("127.0.0.1")))
        )

        val error = assertFailsWith<IllegalArgumentException> {
            guard.lookupAndValidate("private.example")
        }

        kotlin.test.assertTrue(error.message.orEmpty().contains("Private"))
    }
}
