package com.shod.client.vpn.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TunnelLogSanitizerTest {
    private val owner = "AbCdEfGh1234567890xyz"
    private val own = "ZzYyXxWw0987654321abc"

    @Test
    fun ownerHashIsMaskedInFullAndInDisplayedPrefixes() {
        val masked = TunnelLogSanitizer.maskOwnerHashes(
            "[VK] Хеш \"${TunnelLogSanitizer.displayVkHash(owner)}\" устарел · [КРЕД #1] Запрос (хеш: ${owner.take(8)}...) · $owner",
            listOf(owner),
        )
        assertFalse(masked.contains(owner.take(8)))
        assertEquals(
            "[VK] Хеш \"от владельца\" устарел · [КРЕД #1] Запрос (хеш: от владельца) · от владельца",
            masked,
        )
    }

    @Test
    fun participantHashesAndUnrelatedTextRemainUnchanged() {
        val line = "Запрос (хеш: ${own.take(8)}...) порт 46000"
        assertEquals(line, TunnelLogSanitizer.maskOwnerHashes(line, listOf(owner)))
        assertEquals(line, TunnelLogSanitizer.maskOwnerHashes(line, emptyList()))
    }
}
