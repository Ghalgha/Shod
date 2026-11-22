package com.shod.client.vpn.config

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnConfigurationTest {
    @Test
    fun defaultMtuIs1500() {
        assertEquals(1500, ShodConstants.Vpn.DEFAULT_MTU)
    }
}

