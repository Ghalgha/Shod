package com.shod.client.integration.vk.policy
import org.junit.Assert.assertEquals
import org.junit.Test

import com.shod.client.ShodConstants
class VkModePolicyTest {
    @Test
    fun autoVkHashSelectsAutoVkWorkMode() {
        assertEquals(
            ShodConstants.VkAuth.MODE_AUTO_JS,
            vkAuthModeForHashMode(
                ShodConstants.VkAutoHash.MODE_AUTO_JS,
                ShodConstants.VkAuth.MODE_CALLS,
            ),
        )
    }

    @Test
    fun autoVkWorkModeSelectsAutoVkHashes() {
        assertEquals(
            ShodConstants.VkAutoHash.MODE_AUTO_JS,
            vkHashModeForAuthMode(
                ShodConstants.VkAuth.MODE_AUTO_JS,
                ShodConstants.VkAutoHash.MODE_AUTO_API,
                hasVkToken = true,
            ),
        )
    }

    @Test
    fun autoApiHashLeavesAutoVkWorkMode() {
        assertEquals(
            ShodConstants.VkAuth.MODE_CALLS,
            vkAuthModeForHashMode(
                ShodConstants.VkAutoHash.MODE_AUTO_API,
                ShodConstants.VkAuth.MODE_AUTO_JS,
            ),
        )
    }

    @Test
    fun autoWorkModeMovesAutoVkHashesToAvailableNonVkMode() {
        assertEquals(
            ShodConstants.VkAutoHash.MODE_AUTO_API,
            vkHashModeForAuthMode(
                ShodConstants.VkAuth.MODE_CALLS,
                ShodConstants.VkAutoHash.MODE_AUTO_JS,
                hasVkToken = true,
            ),
        )
        assertEquals(
            ShodConstants.VkAutoHash.MODE_MANUAL,
            vkHashModeForAuthMode(
                ShodConstants.VkAuth.MODE_CALLS,
                ShodConstants.VkAutoHash.MODE_AUTO_JS,
                hasVkToken = false,
            ),
        )
    }

    @Test
    fun manualHashLeavesAutoVkWorkMode() {
        assertEquals(
            ShodConstants.VkAuth.MODE_CALLS,
            vkAuthModeForHashMode(
                ShodConstants.VkAutoHash.MODE_MANUAL,
                ShodConstants.VkAuth.MODE_AUTO_JS,
            ),
        )
    }

    @Test
    fun unavailableManualHashStaysInCurrentProcessWhileAutoHashIsReplaced() {
        assertEquals(
            false,
            shouldReplaceUnavailableVkHash(ShodConstants.VkAutoHash.MODE_MANUAL),
        )
        assertEquals(
            true,
            shouldReplaceUnavailableVkHash(ShodConstants.VkAutoHash.MODE_AUTO_API),
        )
        assertEquals(
            true,
            shouldReplaceUnavailableVkHash(ShodConstants.VkAutoHash.MODE_AUTO_JS),
        )
    }

    @Test
    fun autoVkWarningAppearsOnlyWhenEnteringTheModeWithoutAcknowledgement() {
        assertEquals(
            true,
            shouldConfirmAutoJsMode(
                currentMode = ShodConstants.VkAuth.MODE_CALLS,
                requestedMode = ShodConstants.VkAuth.MODE_AUTO_JS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmAutoJsMode(
                currentMode = ShodConstants.VkAuth.MODE_AUTO_JS,
                requestedMode = ShodConstants.VkAuth.MODE_AUTO_JS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmAutoJsMode(
                currentMode = ShodConstants.VkAuth.MODE_CALLS,
                requestedMode = ShodConstants.VkAuth.MODE_AUTO_JS,
                warningAcknowledged = true,
            ),
        )
    }
}
