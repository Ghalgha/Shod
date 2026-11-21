package com.shod.client.vpn.diagnostics.probe

import com.shod.client.ShodConstants
import com.shod.client.integration.vk.calls.policy.ProfileProbeVkAuthPlan
import com.shod.client.integration.vk.calls.policy.ProfileProbeVkAuthStrategy

/**
 * Builds the native command for a diagnostic process. The caller supplies a unique abstract
 * socket name; this command never reuses the production tunnel's TUN socket.
 */
internal object ProfileProbeProcessCommand {
    fun build(
        binaryPath: String,
        peer: String,
        udsName: String,
        authPlan: ProfileProbeVkAuthPlan,
        turnTransport: String,
        srtpMode: String,
        customTurnIps: String,
        captchaMode: String,
        fingerprint: String,
        clientIds: String,
        sessionSalt: String,
        generationId: Long,
    ): List<String> {
        require(binaryPath.isNotBlank())
        require(peer.isNotBlank())
        require(udsName.isNotBlank())
        require(sessionSalt.isNotBlank())
        require(generationId >= 0L)
        val jsMode = authPlan.strategy == ProfileProbeVkAuthStrategy.VK_AUTO_JS
        val hash = authPlan.firstHash
        require(jsMode || hash.isNotBlank()) { "Manual VK hash is required" }
        if (jsMode) require(authPlan.accessToken.isNotBlank()) { "VK access token is required" }

        return buildList {
            add(binaryPath)
            addAll(listOf("-peer", peer))
            addAll(listOf("-n", ShodConstants.Tunnel.WORKERS_PER_GROUP.toString()))
            addAll(listOf("-listen", "${ShodConstants.Network.LOCAL_LISTEN_HOST}:${ShodConstants.Network.DEFAULT_LOCAL_PORT}"))
            addAll(listOf("-tun-uds", udsName))
            if (hash.isNotBlank()) addAll(listOf("-vk", hash))
            addAll(listOf("-vk-hash-mode", if (jsMode) ShodConstants.VkAutoHash.MODE_AUTO_JS else ShodConstants.VkAutoHash.MODE_MANUAL))
            addAll(listOf("-vk-auth-mode", authPlan.processAuthMode))
            if (fingerprint.isNotBlank()) addAll(listOf("-fingerprint", fingerprint))
            if (clientIds.isNotBlank()) addAll(listOf("-client-ids", clientIds))
            addAll(listOf("-obfs", ShodConstants.Tunnel.DEFAULT_OBFS_MODE))
            addAll(listOf("-turn-transport", turnTransport))
            addAll(listOf("-srtp-mode", srtpMode))
            if (customTurnIps.isNotBlank()) addAll(listOf("-custom-turn-ips", customTurnIps))
            addAll(listOf("-gen", generationId.toString()))
            addAll(listOf("-salt", sessionSalt))
            addAll(listOf("-captcha-mode", captchaMode))
        }
    }
}
