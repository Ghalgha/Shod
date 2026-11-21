package com.shod.client.platform.android.services

import android.content.Context
import android.content.Intent
import com.shod.client.ConnectionSource
import com.shod.client.TunnelService

internal data class TunnelStartOptions(
    val workersPerHash: Int,
    val sni: String,
    val vkAuthMode: String,
    val captchaMode: String,
    val captchaSolveMethod: String,
    val fingerprint: String,
    val clientIds: String,
    val obfsMode: String,
    val turnTransport: String,
    val srtpMode: String,
    val customTurnIps: String,
)

internal object TunnelServiceIntentFactory {
    fun create(
        context: Context,
        source: ConnectionSource,
        options: TunnelStartOptions,
        generationId: Long,
        sessionSalt: String,
    ): Intent = Intent(context, TunnelService::class.java).apply {
        action = "START"
        putExtra("peer", source.peer)
        putExtra("vk_hashes", source.hashes)
        putExtra("vk_hashes_from_link", source.hashesFromLink)
        putExtra("vk_access_token", source.vkAccessToken)
        putExtra("vk_token_from_link", source.tokenFromLink)
        putExtra("workers_per_hash", options.workersPerHash)
        putExtra("port", 0)
        putExtra("sni", options.sni)
        putExtra("connection_password", source.password)
        putExtra("vk_auth_mode", options.vkAuthMode)
        putExtra("captcha_mode", options.captchaMode)
        putExtra("captcha_solve_method", options.captchaSolveMethod)
        putExtra("fingerprint", options.fingerprint)
        putExtra("client_ids", options.clientIds)
        putExtra("obfs_mode", options.obfsMode)
        putExtra("turn_transport", options.turnTransport)
        putExtra("srtp_mode", options.srtpMode)
        putExtra("custom_turn_ips", options.customTurnIps)
        putExtra("generation_id", generationId)
        putExtra("session_salt", sessionSalt)
    }
}
