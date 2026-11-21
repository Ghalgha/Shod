package com.shod.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme

/** Shared surface colours for grouped settings and other application sections. */
@Composable
fun appSectionCardColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < 0.22f) {
        lerp(colors.background, colors.surface, 0.90f)
    } else {
        lerp(colors.surface, colors.surfaceVariant, 0.16f)
    }
}

@Composable
fun appSectionCardBorderColor(): Color {
    val colors = MaterialTheme.colorScheme
    val alpha = if (colors.background.luminance() < 0.22f) 0.42f else 0.36f
    return colors.outlineVariant.copy(alpha = alpha)
}
