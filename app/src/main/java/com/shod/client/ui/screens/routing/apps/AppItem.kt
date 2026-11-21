package com.shod.client.ui.screens.routing.apps

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Presentation model for an installed application in the split-tunnel picker.
 * Package identity is kept separate from the localized display label.
 */
@Immutable
internal data class AppItem(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean,
)
