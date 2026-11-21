package com.shod.client.ui.screens.routing.apps

/**
 * Keeps Shod itself and VK applications that may carry the tunnel transport
 * out of the user-controlled split-tunnel list.
 */
internal object AppRoutingCatalogPolicy {
    fun isSelectable(packageName: String, shodPackageName: String): Boolean =
        packageName != shodPackageName &&
            !packageName.contains("vkontakte") &&
            !packageName.contains("vk.calls")
}
