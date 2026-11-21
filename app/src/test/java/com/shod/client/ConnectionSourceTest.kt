package com.shod.client

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionSourceTest {
    @Test
    fun participantHashesKeepsRemainingOwnerHashesBeforeOwnHashes() {
        val result = participantHashes(
            linkHashes = listOf("owner-one-hash-1234", "owner-two-hash-1234"),
            removed = setOf("owner-one-hash-1234"),
            ownHashes = "participant-hash-1234, owner-two-hash-1234",
        )

        assertEquals("owner-two-hash-1234,participant-hash-1234", result)
    }

    @Test
    fun participantHashesTrimsAndRemovesDuplicates() {
        val result = participantHashes(
            linkHashes = listOf(" owner-hash-123456 "),
            removed = emptySet(),
            ownHashes = "own-hash-123456\nowner-hash-123456",
        )

        assertEquals("owner-hash-123456,own-hash-123456", result)
    }
}
