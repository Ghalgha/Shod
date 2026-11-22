package com.shod.client.ui.telemetry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shod.client.TunnelManager
import com.shod.client.TunnelTrafficText

@Composable
internal fun TunnelTrafficSummary(
    modifier: Modifier = Modifier,
    primary: Color = MaterialTheme.colorScheme.onSurface,
    secondary: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val traffic by TunnelManager.traffic.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            TunnelTrafficText.speed(traffic),
            color = primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Text(
            TunnelTrafficText.total(traffic),
            color = secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}
