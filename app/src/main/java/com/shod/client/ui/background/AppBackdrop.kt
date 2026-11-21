package com.shod.client.ui.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
internal fun AppBackdrop(
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    val baseBrush = remember(colors.background, colors.surface, colors.surfaceVariant, isDark) {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    lerp(colors.background, colors.surface, 0.08f),
                    colors.background,
                    lerp(colors.surfaceVariant, colors.background, 0.88f),
                )
            } else {
                listOf(
                    lerp(colors.background, colors.surface, 0.16f),
                    colors.background,
                    lerp(colors.surfaceVariant, colors.background, 0.12f),
                )
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBrush),
    )
}
