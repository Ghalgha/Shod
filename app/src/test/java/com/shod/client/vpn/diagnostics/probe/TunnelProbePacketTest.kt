package com.shod.client.vpn.diagnostics.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelProbePacketTest {
    private val source = byteArrayOf(10, 66, 67, 2)
    private val dns = byteArrayOf(1, 1, 1, 1)

    @Test
    fun buildsIpv4UdpDnsRequestForConfiguredTunnelEndpoints() {
        val packet = TunnelProbePacket(source, dns, 12345).request()

        assertEquals(4, packet[0].toInt() ushr 4)
        assertEquals(17, packet[9].toInt())
        assertTrue(packet.copyOfRange(12, 16).contentEquals(source))
        assertTrue(packet.copyOfRange(16, 20).contentEquals(dns))
        assertEquals(53, TunnelProbePacket.read16(packet, 22))
        assertEquals(12345, TunnelProbePacket.read16(packet, 28))
        assertEquals(1, TunnelProbePacket.read16(packet, 32))
    }

    @Test
    fun acceptsOnlyMatchingDnsReplyFromConfiguredResolver() {
        val probe = TunnelProbePacket(source, dns, 12345)
        val reply = probe.request().also {
            dns.copyInto(it, 12)
            source.copyInto(it, 16)
            val sourcePort = TunnelProbePacket.read16(it, 20)
            TunnelProbePacket.put16(it, 20, 53)
            TunnelProbePacket.put16(it, 22, sourcePort)
            it[30] = 0x80.toByte()
        }

        assertTrue(probe.isReply(reply))
        reply[16] = 2
        assertFalse(probe.isReply(reply))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonIpv4EndpointBytes() {
        TunnelProbePacket(byteArrayOf(10, 0, 0), dns, 1)
    }
}
