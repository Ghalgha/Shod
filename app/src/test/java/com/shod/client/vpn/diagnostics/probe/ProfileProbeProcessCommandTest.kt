package com.shod.client.vpn.diagnostics.probe

import com.shod.client.ShodConstants
import com.shod.client.integration.vk.calls.policy.ProfileProbeVkAuthPlan
import com.shod.client.integration.vk.calls.policy.ProfileProbeVkAuthStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProbeProcessCommandTest {
    private fun plan(strategy: ProfileProbeVkAuthStrategy, hash: String = "hash-a") =
        ProfileProbeVkAuthPlan(
            strategy = strategy,
            accessToken = if (strategy == ProfileProbeVkAuthStrategy.VK_AUTO_JS) "token" else "",
            processAuthMode = if (strategy == ProfileProbeVkAuthStrategy.VK_AUTO_JS)
                ShodConstants.VkAuth.MODE_AUTO_JS else ShodConstants.VkAuth.MODE_CALLS,
            firstHash = hash,
        )

    @Test
    fun usesDedicatedUdsAndDoesNotReuseProductionTunnelSocket() {
        val command = ProfileProbeProcessCommand.build(
            binaryPath = "/native/libclient.so",
            peer = "server.example:46000",
            udsName = "shod_probe_session-1",
            authPlan = plan(ProfileProbeVkAuthStrategy.MANUAL_HASHES),
            turnTransport = "udp",
            srtpMode = ShodConstants.SrtpMode.DEFAULT,
            customTurnIps = "",
            captchaMode = "auto",
            fingerprint = "firefox",
            clientIds = "8202606",
            sessionSalt = "session-salt",
            generationId = 123L,
        )
        assertEquals("shod_probe_session-1", command[command.indexOf("-tun-uds") + 1])
        assertFalse(command.contains("shod_tun_uds"))
        assertEquals("${ShodConstants.Network.LOCAL_LISTEN_HOST}:0", command[command.indexOf("-listen") + 1])
        assertEquals("hash-a", command[command.indexOf("-vk") + 1])
    }

    @Test
    fun automaticJsOmitsManualHashAndUsesJsHashMode() {
        val command = ProfileProbeProcessCommand.build(
            binaryPath = "/native/libclient.so",
            peer = "server.example:46000",
            udsName = "shod_probe_session-2",
            authPlan = plan(ProfileProbeVkAuthStrategy.VK_AUTO_JS, ""),
            turnTransport = "udp",
            srtpMode = ShodConstants.SrtpMode.DEFAULT,
            customTurnIps = "",
            captchaMode = "auto",
            fingerprint = "",
            clientIds = "",
            sessionSalt = "session-salt",
            generationId = 124L,
        )
        assertTrue(command.contains(ShodConstants.VkAutoHash.MODE_AUTO_JS))
        assertFalse(command.contains("-vk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun manualModeRejectsMissingHash() {
        ProfileProbeProcessCommand.build(
            binaryPath = "/native/libclient.so",
            peer = "server.example:46000",
            udsName = "shod_probe_session-3",
            authPlan = plan(ProfileProbeVkAuthStrategy.MANUAL_HASHES, ""),
            turnTransport = "udp",
            srtpMode = ShodConstants.SrtpMode.DEFAULT,
            customTurnIps = "",
            captchaMode = "auto",
            fingerprint = "",
            clientIds = "",
            sessionSalt = "session-salt",
            generationId = 125L,
        )
    }
}
