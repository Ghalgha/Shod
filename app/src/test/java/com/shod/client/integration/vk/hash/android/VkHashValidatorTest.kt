package com.shod.client.integration.vk.hash.android

import com.shod.client.integration.vk.hash.codec.VkHashValidationCodec
import com.shod.client.integration.vk.hash.codec.VkHashValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VkHashValidatorTest {
    @Test
    fun processOutputAcceptsOnlyFinalValidOrInvalidStates() {
        val valid = VkHashValidationCodec.parseProcessOutputLine(
            "HASH_CHECK:{\"hash\":\"hash-123456789012\",\"status\":\"valid\"}",
        )
        val invalid = VkHashValidator.parseOutputLine(
            "HASH_CHECK:{\"hash\":\"hash-210987654321\",\"status\":\"invalid\",\"code\":9008}",
        )
        assertEquals(VkHashValidationStatus.Valid, valid?.second)
        assertEquals(VkHashValidationStatus.Invalid, invalid?.second)
        assertNull(
            VkHashValidator.parseOutputLine(
                "HASH_CHECK:{\"hash\":\"hash-123456789012\",\"status\":\"unavailable\"}",
            ),
        )
    }

    @Test
    fun validatorModeDoesNotContainTunnelOrTurnArguments() {
        val command = VkHashValidationCodec.buildProcessCommand(
            "/native/libclient.so",
            listOf("hash-123456789012"),
            "firefox",
            "8202606,6287487",
        )
        assertTrue("--validate-vk-hashes" in command)
        assertTrue("--vk" in command)
        assertFalse("--peer" in command)
        assertFalse("--password" in command)
        assertFalse("--workers" in command)
    }
}
