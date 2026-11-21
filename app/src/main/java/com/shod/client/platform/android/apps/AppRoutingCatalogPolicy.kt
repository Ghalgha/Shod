package com.shod.client.platform.android.apps

/**
 * Keeps Shod and VK transport packages out of user-configurable app routing.
 * VK packages must remain outside the tunnel to prevent transport recursion.
 */
internal object AppRoutingCatalogPolicy {
    fun isSelectable(packageName: String, shodPackageName: String): Boolean =
        packageName != shodPackageName &&
            !packageName.contains("vkontakte") &&
            !packageName.contains("vk.calls")
}
