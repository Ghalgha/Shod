package com.shod.client.integration.vk.calls.policy

import com.shod.client.ShodConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileProbeVkAuthPolicyTest {
    @Test
    fun linkWithManualHashesOverridesAutomaticModes() {
        val plan = ProfileProbeVkAuthPolicy.resolve(
            hashes = "hash-a, hash-b",
            hashesFromLink = true,
            tokenFromLink = false,
            linkToken = "",
            storedToken = "stored-token",
            configuredHashMode = ShodConstants.VkAutoHash.MODE_AUTO_API,
            configuredAuthMode = ShodConstants.VkAuth.MODE_AUTO_JS,
        )
        assertEquals(ProfileProbeVkAuthStrategy.MANUAL_HASHES, plan.strategy)
        assertEquals("hash-a", plan.firstHash)
        assertEquals(ShodConstants.VkAuth.MODE_CALLS, plan.processAuthMode)
    }

    @Test
    fun automaticApiUsesLinkTokenWhenLinkCarriesToken() {
        val plan = ProfileProbeVkAuthPolicy.resolve(
            hashes = "",
            hashesFromLink = true,
            tokenFromLink = true,
            linkToken = "link-token",
            storedToken = "stored-token",
            configuredHashMode = ShodConstants.VkAutoHash.MODE_MANUAL,
            configuredAuthMode = ShodConstants.VkAuth.MODE_CALLS,
        )
        assertEquals(ProfileProbeVkAuthStrategy.VK_CALLS_API, plan.strategy)
        assertEquals("link-token", plan.accessToken)
    }

    @Test
    fun automaticJsTakesPrecedenceOverApi() {
        val plan = ProfileProbeVkAuthPolicy.resolve(
            hashes = "hash-a",
            hashesFromLink = false,
            tokenFromLink = false,
            linkToken = "",
            storedToken = "stored-token",
            configuredHashMode = ShodConstants.VkAutoHash.MODE_AUTO_API,
            configuredAuthMode = ShodConstants.VkAuth.MODE_AUTO_JS,
        )
        assertEquals(ProfileProbeVkAuthStrategy.VK_AUTO_JS, plan.strategy)
        assertEquals(ShodConstants.VkAuth.MODE_AUTO_JS, plan.processAuthMode)
    }

    @Test
    fun automaticApiRequiresStoredTokenAndDoesNotInventManualHash() {
        val plan = ProfileProbeVkAuthPolicy.resolve(
            hashes = "  ",
            hashesFromLink = false,
            tokenFromLink = false,
            linkToken = "",
            storedToken = "",
            configuredHashMode = ShodConstants.VkAutoHash.MODE_AUTO_API,
            configuredAuthMode = ShodConstants.VkAuth.MODE_CALLS,
        )
        assertEquals(ProfileProbeVkAuthStrategy.VK_CALLS_API, plan.strategy)
        assertEquals("", plan.accessToken)
        assertEquals("", plan.firstHash)
    }

    @Test
    fun manualModeKeepsFirstNonBlankHashAndStoredToken() {
        val plan = ProfileProbeVkAuthPolicy.resolve(
            hashes = " , hash-a hash-b",
            hashesFromLink = false,
            tokenFromLink = false,
            linkToken = "link-token",
            storedToken = "stored-token",
            configuredHashMode = ShodConstants.VkAutoHash.MODE_MANUAL,
            configuredAuthMode = ShodConstants.VkAuth.MODE_CALLS,
        )
        assertEquals(ProfileProbeVkAuthStrategy.MANUAL_HASHES, plan.strategy)
        assertEquals("hash-a", plan.firstHash)
        assertEquals("stored-token", plan.accessToken)
    }
}
