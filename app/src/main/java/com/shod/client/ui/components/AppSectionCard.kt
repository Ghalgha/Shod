package com.shod.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.design.ShodSpacing

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

@Composable
fun AppSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(ShodSpacing.Md),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.22f
    Surface(
        shape = ShodShapes.Card,
        color = appSectionCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, appSectionCardBorderColor()),
        shadowElevation = if (isDark) 2.dp else 3.dp,
        tonalElevation = if (isDark) 0.5.dp else 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}
