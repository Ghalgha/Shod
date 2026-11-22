package com.shod.client.vpn.session

import java.security.SecureRandom

internal fun reserveTunnelGenerationId(nowSeconds: Long, proposed: Long, previous: Long): Long {
    val nextPrevious = if (previous == Long.MAX_VALUE) Long.MAX_VALUE else previous + 1L
    return maxOf(1L, nowSeconds, proposed, nextPrevious)
}

internal fun newTunnelSessionId(fillBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes): String {
    val bytes = ByteArray(16)
    fillBytes(bytes)
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
