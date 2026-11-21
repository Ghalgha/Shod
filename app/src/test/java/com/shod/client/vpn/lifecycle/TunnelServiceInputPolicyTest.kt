package com.shod.client.vpn.lifecycle

import com.shod.client.ShodConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelServiceInputPolicyTest {
    @Test fun captchaModeAcceptsSupportedValuesAndDefaultsUnknownValues() {
        assertEquals("auto", TunnelServiceInputPolicy.sanitizeCaptchaMode("AUTO"))
        assertEquals("rjs", TunnelServiceInputPolicy.sanitizeCaptchaMode("RJS"))
        assertEquals("wv", TunnelServiceInputPolicy.sanitizeCaptchaMode("WV"))
        assertEquals("auto", TunnelServiceInputPolicy.sanitizeCaptchaMode("invalid"))
        assertEquals("auto", TunnelServiceInputPolicy.sanitizeCaptchaMode(null))
    }

    @Test fun vkAuthModeAcceptsSupportedValuesAndDefaultsUnknownValues() {
        assertEquals(ShodConstants.VkAuth.MODE_CAPTCHA, TunnelServiceInputPolicy.sanitizeVkAuthMode(ShodConstants.VkAuth.MODE_CAPTCHA.uppercase()))
        assertEquals(ShodConstants.VkAuth.MODE_AUTO_JS, TunnelServiceInputPolicy.sanitizeVkAuthMode(ShodConstants.VkAuth.MODE_AUTO_JS.uppercase()))
        assertEquals(ShodConstants.VkAuth.MODE_CALLS, TunnelServiceInputPolicy.sanitizeVkAuthMode("invalid"))
        assertEquals(ShodConstants.VkAuth.MODE_CALLS, TunnelServiceInputPolicy.sanitizeVkAuthMode(null))
    }

    @Test fun srtpModeAcceptsSupportedValuesAndDefaultsUnknownValues() {
        assertEquals(ShodConstants.SrtpMode.WRAP, TunnelServiceInputPolicy.sanitizeSrtpMode(ShodConstants.SrtpMode.WRAP.uppercase()))
        assertEquals(ShodConstants.SrtpMode.DTLS, TunnelServiceInputPolicy.sanitizeSrtpMode(ShodConstants.SrtpMode.DTLS.uppercase()))
        assertEquals(ShodConstants.SrtpMode.AUTO, TunnelServiceInputPolicy.sanitizeSrtpMode("invalid"))
        assertEquals(ShodConstants.SrtpMode.AUTO, TunnelServiceInputPolicy.sanitizeSrtpMode(null))
    }
}
