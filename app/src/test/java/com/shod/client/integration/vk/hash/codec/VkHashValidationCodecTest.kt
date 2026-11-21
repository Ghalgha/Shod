package com.shod.client.integration.vk.hash.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VkHashValidationCodecTest {
    @Test
    fun validationResultsRoundTripThroughPersistentJson() {
        val source = mapOf(
            "valid-hash-123456" to VkHashValidationStatus.Valid,
            "invalid-hash-1234" to VkHashValidationStatus.Invalid,
        )
        assertEquals(source, VkHashValidationCodec.decode(VkHashValidationCodec.encode(source)))
    }

    @Test
    fun onlyChangedOrUncheckedHashesArePending() {
        val valid = "valid-hash-123456"
        val invalid = "invalid-hash-1234"
        val replacement = "replacement-123456"
        val checked = mapOf(
            valid to VkHashValidationStatus.Valid,
            invalid to VkHashValidationStatus.Invalid,
        )
        val afterEdit = VkHashValidationCodec.invalidate(checked, invalid)
        assertEquals(
            listOf(replacement),
            VkHashValidationCodec.pending(listOf(valid, replacement), afterEdit),
        )
        assertTrue(valid in afterEdit)
        assertFalse(invalid in afterEdit)
    }

    @Test
    fun invalidHashIsRetainedForUiButExcludedFromTunnelSource() {
        val active = "active-hash-123456"
        val invalid = "invalid-hash-1234"
        val results = mapOf(invalid to VkHashValidationStatus.Invalid)

        assertEquals(
            listOf(active),
            VkHashValidationCodec.active(listOf(active, invalid, active), results),
        )
        assertEquals(VkHashValidationStatus.Invalid, results[invalid])
    }
}
