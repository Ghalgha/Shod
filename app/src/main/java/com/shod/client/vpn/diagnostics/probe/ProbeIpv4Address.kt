package com.shod.client.vpn.diagnostics.probe

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Parses a numeric IPv4 literal without invoking the system name resolver.
 * Probe configuration must never trigger an out-of-tunnel DNS lookup.
 */
internal fun parseIpv4Literal(value: String): Inet4Address? {
    val octets = value.split('.')
    if (octets.size != 4) return null
    val bytes = ByteArray(4)
    for (index in octets.indices) {
        val part = octets[index]
        if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
        val number = part.toIntOrNull() ?: return null
        if (number !in 0..255) return null
        bytes[index] = number.toByte()
    }
    return InetAddress.getByAddress(bytes) as Inet4Address
}
