package com.shod.client.platform.android.apps

import android.graphics.Bitmap

/**
 * Android-side representation of an installed package.
 * Compose-specific image types are intentionally kept out of this layer.
 */
internal data class InstalledAppEntry(
    val label: String,
    val packageName: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
)
