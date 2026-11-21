package com.shod.client.integration.vk.calls.policy

import com.shod.client.ShodConstants

internal enum class ProfileProbeVkAuthStrategy {
    MANUAL_HASHES,
    VK_CALLS_API,
    VK_AUTO_JS,
}

internal data class ProfileProbeVkAuthPlan(
    val strategy: ProfileProbeVkAuthStrategy,
    val accessToken: String,
    val processAuthMode: String,
    val firstHash: String,
)

/**
 * Preserves the archive's precedence rules while keeping profile diagnostics independent
 * from SettingsStore, Android services and the active VPN's VK call manager.
 */
internal object ProfileProbeVkAuthPolicy {
    fun resolve(
        hashes: String,
        hashesFromLink: Boolean,
        tokenFromLink: Boolean,
        linkToken: String,
        storedToken: String,
        configuredHashMode: String,
        configuredAuthMode: String,
    ): ProfileProbeVkAuthPlan {
        val manualHashesFromLink = hashesFromLink && !tokenFromLink
        val strategy = when {
            manualHashesFromLink -> ProfileProbeVkAuthStrategy.MANUAL_HASHES
            configuredHashMode == ShodConstants.VkAutoHash.MODE_AUTO_JS ||
                configuredAuthMode == ShodConstants.VkAuth.MODE_AUTO_JS ->
                ProfileProbeVkAuthStrategy.VK_AUTO_JS
            tokenFromLink || configuredHashMode == ShodConstants.VkAutoHash.MODE_AUTO_API ->
                ProfileProbeVkAuthStrategy.VK_CALLS_API
            else -> ProfileProbeVkAuthStrategy.MANUAL_HASHES
        }
        val token = if (tokenFromLink) linkToken else storedToken
        val firstHash = hashes.split(Regex("[,\\s]+")).firstOrNull(String::isNotBlank).orEmpty()
        val processAuthMode = when (strategy) {
            ProfileProbeVkAuthStrategy.VK_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
            else -> if (configuredAuthMode == ShodConstants.VkAuth.MODE_AUTO_JS) {
                ShodConstants.VkAuth.MODE_CALLS
            } else configuredAuthMode
        }
        return ProfileProbeVkAuthPlan(
            strategy = strategy,
            accessToken = token,
            processAuthMode = processAuthMode,
            firstHash = firstHash,
        )
    }
}
