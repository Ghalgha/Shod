package com.shod.client.vpn.diagnostics.probe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeIpv4AddressTest {
    @Test
    fun parsesNumericIpv4Literal() {
        assertArrayEquals(byteArrayOf(192.toByte(), 0, 2, 10), parseIpv4Literal("192.0.2.10")?.address)
    }

    @Test
    fun rejectsHostnamesAndMalformedAddressesWithoutResolution() {
        listOf("", "example.com", "127.0.0.1.example", "256.1.1.1", "1.2.3", "1.2.3.4.5", "1..3.4")
            .forEach { assertNull(it, parseIpv4Literal(it)) }
    }
}
