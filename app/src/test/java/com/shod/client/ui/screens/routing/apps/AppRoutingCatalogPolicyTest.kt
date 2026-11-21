package com.shod.client.ui.screens.routing.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoutingCatalogPolicyTest {
    @Test
    fun excludesShodAndVkTransportApplications() {
        assertFalse(AppRoutingCatalogPolicy.isSelectable("com.example.shod", "com.example.shod"))
        assertFalse(AppRoutingCatalogPolicy.isSelectable("com.vkontakte.android", "com.example.shod"))
        assertFalse(AppRoutingCatalogPolicy.isSelectable("com.vk.calls", "com.example.shod"))
    }

    @Test
    fun allowsOtherApplications() {
        assertTrue(AppRoutingCatalogPolicy.isSelectable("com.example.browser", "com.example.shod"))
        assertTrue(AppRoutingCatalogPolicy.isSelectable("com.vk.music", "com.example.shod"))
    }
}
