package com.shod.client.integration.vk.policy
import com.shod.client.ShodConstants
internal data class VkModeSelection(
    val authMode: String,
    val hashMode: String,
)

internal fun normalizeVkModeSelection(
    requestedAuthMode: String?,
    requestedHashMode: String?,
    hasVkToken: Boolean,
): VkModeSelection {
    val authMode = when (requestedAuthMode) {
        ShodConstants.VkAuth.MODE_CAPTCHA -> ShodConstants.VkAuth.MODE_CAPTCHA
        ShodConstants.VkAuth.MODE_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
        else -> ShodConstants.VkAuth.MODE_CALLS
    }
    val hashMode = when (requestedHashMode) {
        "auto", ShodConstants.VkAutoHash.MODE_AUTO_API -> ShodConstants.VkAutoHash.MODE_AUTO_API
        ShodConstants.VkAutoHash.MODE_AUTO_JS -> ShodConstants.VkAutoHash.MODE_AUTO_JS
        else -> ShodConstants.VkAutoHash.MODE_MANUAL
    }
    return when {
        authMode == ShodConstants.VkAuth.MODE_AUTO_JS ||
            hashMode == ShodConstants.VkAutoHash.MODE_AUTO_JS ->
            VkModeSelection(
                authMode = ShodConstants.VkAuth.MODE_AUTO_JS,
                hashMode = ShodConstants.VkAutoHash.MODE_AUTO_JS,
            )
        else -> VkModeSelection(authMode = authMode, hashMode = hashMode)
    }
}

internal fun vkHashModeForAuthMode(
    requestedAuthMode: String,
    currentHashMode: String?,
    hasVkToken: Boolean,
): String = when (requestedAuthMode) {
    ShodConstants.VkAuth.MODE_AUTO_JS -> ShodConstants.VkAutoHash.MODE_AUTO_JS
    ShodConstants.VkAuth.MODE_CALLS -> if (currentHashMode == ShodConstants.VkAutoHash.MODE_AUTO_JS) {
        if (hasVkToken) ShodConstants.VkAutoHash.MODE_AUTO_API else ShodConstants.VkAutoHash.MODE_MANUAL
    } else {
        normalizeVkModeSelection(requestedAuthMode, currentHashMode, hasVkToken).hashMode
    }
    else -> normalizeVkModeSelection(requestedAuthMode, currentHashMode, hasVkToken).hashMode
}

internal fun vkAuthModeForHashMode(requestedHashMode: String, currentAuthMode: String?): String =
    when (requestedHashMode) {
        ShodConstants.VkAutoHash.MODE_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
        ShodConstants.VkAutoHash.MODE_AUTO_API -> ShodConstants.VkAuth.MODE_CALLS
        else -> if (currentAuthMode == ShodConstants.VkAuth.MODE_AUTO_JS) {
            ShodConstants.VkAuth.MODE_CALLS
        } else {
            normalizeVkModeSelection(currentAuthMode, requestedHashMode, false).authMode
        }
    }

internal fun shouldReplaceUnavailableVkHash(hashMode: String): Boolean =
    hashMode != ShodConstants.VkAutoHash.MODE_MANUAL

internal fun shouldConfirmAutoJsMode(
    currentMode: String,
    requestedMode: String,
    warningAcknowledged: Boolean,
): Boolean =
    !warningAcknowledged &&
        currentMode != ShodConstants.VkAuth.MODE_AUTO_JS &&
        requestedMode == ShodConstants.VkAuth.MODE_AUTO_JS
