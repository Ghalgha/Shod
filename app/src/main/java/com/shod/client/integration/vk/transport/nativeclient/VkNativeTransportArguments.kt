package com.shod.client.integration.vk.transport.nativeclient

import android.util.Base64
import com.shod.client.vpn.model.TunnelParams
import org.json.JSONObject

/**
 * Builds the VK call/ TURN transport arguments consumed by the native tunnel client.
 * Process lifecycle and generic TUN/routing arguments remain in TunnelManager.
 */
internal object VkNativeTransportArguments {
    fun buildJsBootstrapLine(accessToken: String): String {
        val payload = JSONObject()
            .put("token", accessToken)
            .toString()
        val encoded = Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        return "VK_JS_BOOTSTRAP:$encoded\\n"
    }

    fun build(
        params: TunnelParams,
        hashes: List<String>,
        effectiveAuthMode: String,
    ): List<String> = buildList {
        if (hashes.isNotEmpty()) {
            add("-vk")
            add(hashes.joinToString(","))
        }
        add("-vk-hash-mode")
        add(params.vkHashMode)
        if (params.fingerprint.isNotEmpty()) {
            add("-fingerprint")
            add(params.fingerprint)
        }
        if (params.clientIds.isNotEmpty()) {
            add("-client-ids")
            add(params.clientIds)
        }
        add("-obfs")
        add(params.obfsMode)
        add("-turn-transport")
        add(params.turnTransport)
        add("-srtp-mode")
        add(params.srtpMode)
        if (params.customTurnIps.isNotBlank()) {
            add("-custom-turn-ips")
            add(params.customTurnIps)
        }
        add("-vk-auth-mode")
        add(effectiveAuthMode)
        if (params.generationId > 0) {
            add("-gen")
            add(params.generationId.toString())
        }
        if (params.sessionSalt.isNotEmpty()) {
            add("-salt")
            add(params.sessionSalt)
        }
        add("-captcha-mode")
        add(params.captchaMode)
    }
}
