package com.shod.client.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shod.client.SettingsStore
import com.shod.client.TunnelManager
import com.shod.client.R
import com.shod.client.platform.android.deploy.AndroidDeployCoordinator
import com.shod.client.platform.android.ui.showRaisedToast
import com.shod.client.ui.design.ShodTheme
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(settingsStore: SettingsStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logs = TunnelManager.logs
    val listState = rememberLazyListState()
    val isDeploying by AndroidDeployCoordinator.isDeploying.collectAsStateWithLifecycle()
    val copiedMessage = stringResource(R.string.logs_copied)
    val loggingEnabled by settingsStore.loggingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(logs.size, isDeploying) {
        if (isDeploying) {
            val latestDeployIndex = logs.indexOfLast { it.key.startsWith("deploy_") }
            if (latestDeployIndex >= 0) listState.animateScrollToItem(latestDeployIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Логи", style = MaterialTheme.typography.headlineSmall)
                Text("· ${logs.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Активное логирование", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = loggingEnabled,
                    onCheckedChange = { enabled ->
                        TunnelManager.isLoggingEnabled = enabled
                        scope.launch { settingsStore.saveLoggingEnabled(enabled) }
                    },
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = ShodTheme.extendedColors.terminalBackground,
            contentColor = ShodTheme.extendedColors.terminalText,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = ShodTheme.extendedColors.terminalMuted)
                            Text(stringResource(R.string.logs_empty_title), color = ShodTheme.extendedColors.terminalMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(items = logs, key = { it.key }) { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text("›", color = ShodTheme.extendedColors.terminalSuccess, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = entry.message + if (entry.count > 1) "  ×${entry.count}" else "",
                                    color = ShodTheme.extendedColors.terminalText,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        enabled = logs.isNotEmpty(),
                        onClick = {
                            val text = logs.joinToString("\n") { "${it.message} (x${it.count})" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Shod Logs", text))
                            context.showRaisedToast(copiedMessage, Toast.LENGTH_SHORT)
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать логи")
                    }
                    IconButton(onClick = TunnelManager::clearLogs) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Очистить логи")
                    }
                }
            }
        }
    }
}
