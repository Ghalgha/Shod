package com.shod.client.vpn.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsProfileNameTest {
    @Test
    fun knownServerDnsProfilesHaveStableDisplayNames() {
        assertEquals("Yandex DNS", TunnelLogSanitizer.dnsProfileName("77.88.8.8,77.88.8.1"))
        assertEquals("Xbox DNS", TunnelLogSanitizer.dnsProfileName("111.88.96.54,111.88.96.55"))
        assertEquals("Xbox DNS", TunnelLogSanitizer.dnsProfileName("111.88.96.50,111.88.96.51"))
        assertEquals("Geohide DNS", TunnelLogSanitizer.dnsProfileName("193.233.112.67,193.233.112.88"))
        assertEquals("Geohide DNS", TunnelLogSanitizer.dnsProfileName("95.182.120.241,37.230.192.51"))
        assertEquals("BI.ZONE DNS", TunnelLogSanitizer.dnsProfileName("195.208.6.1,195.208.7.1"))
        assertEquals("НСДИ DNS", TunnelLogSanitizer.dnsProfileName("195.208.4.1,195.208.5.1"))
    }

    @Test
    fun unknownDnsPairRemainsExplicitlyCustom() {
        assertEquals(
            "Пользовательский DNS",
            TunnelLogSanitizer.dnsProfileName("203.0.113.1,203.0.113.2"),
        )
    }
}
