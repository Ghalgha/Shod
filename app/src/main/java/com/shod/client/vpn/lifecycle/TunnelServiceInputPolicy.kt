package com.shod.client.vpn.lifecycle

import com.shod.client.ShodConstants

/** Normalizes values received from Android service start intents and persisted settings. */
internal object TunnelServiceInputPolicy {
    fun sanitizeCaptchaMode(mode: String?): String = when (mode?.lowercase()) {
        "auto" -> "auto"
        "rjs" -> "rjs"
        "wv" -> "wv"
        else -> "auto"
    }

    fun sanitizeVkAuthMode(mode: String?): String = when (mode?.lowercase()) {
        ShodConstants.VkAuth.MODE_CAPTCHA -> ShodConstants.VkAuth.MODE_CAPTCHA
        ShodConstants.VkAuth.MODE_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
        else -> ShodConstants.VkAuth.MODE_CALLS
    }

    fun sanitizeSrtpMode(mode: String?): String = when (mode?.lowercase()) {
        ShodConstants.SrtpMode.WRAP -> ShodConstants.SrtpMode.WRAP
        ShodConstants.SrtpMode.DTLS -> ShodConstants.SrtpMode.DTLS
        else -> ShodConstants.SrtpMode.AUTO
    }
}
