package com.shod.client.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shod.client.SettingsStore
import com.shod.client.network.wifi.policy.WifiNetworkListMode
import com.shod.client.network.wifi.policy.normalizeWifiSsid
import com.shod.client.platform.android.network.wifi.WifiDiscovery
import com.shod.client.platform.android.network.wifi.currentWifiSsid
import com.shod.client.platform.android.network.wifi.hasWifiSsidAccess
import com.shod.client.platform.android.network.wifi.hasWifiSsidPermission
import kotlinx.coroutines.launch

@Composable
internal fun WifiAutoPauseContent(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by store.wifiPreferences.collectAsState(initial = null)
    val observation by WifiDiscovery.current.collectAsState()
    val owner = remember { Any() }
    var showInput by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, owner) {
        WifiDiscovery.acquire(context, owner)
        WifiDiscovery.refresh()
        onDispose { WifiDiscovery.release(owner) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        WifiDiscovery.refresh()
    }

    val value = config ?: return
    val current = observation.ssid ?: currentWifiSsid(context)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Когда ставить VPN на паузу", style = MaterialTheme.typography.titleSmall)
        WifiNetworkListMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = value.mode == mode,
                    onClick = { scope.launch { store.saveWifiNetworkListMode(mode.storedValue) } },
                    enabled = value.enabled,
                )
                Text(mode.title, modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (!hasWifiSsidAccess(context)) {
            Text(
                if (!hasWifiSsidPermission(context)) {
                    "Чтобы определить имя Wi-Fi, разрешите доступ к точному местоположению."
                } else {
                    "Чтобы Android передавал имя Wi-Fi, включите геолокацию в настройках телефона."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                if (!hasWifiSsidPermission(context)) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                } else {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }) {
                Text(if (!hasWifiSsidPermission(context)) "Разрешить" else "Открыть настройки геолокации")
            }
        }

        Text("Текущая сеть", style = MaterialTheme.typography.titleSmall)
        if (current == null) {
            Text("Имя сети Wi-Fi недоступно", style = MaterialTheme.typography.bodySmall)
        } else {
            WifiNetworkRow(
                name = current,
                selected = current in value.selected,
                enabled = value.enabled && value.mode != WifiNetworkListMode.ALL,
                onSelect = { checked -> scope.launch { store.selectWifiNetwork(current, checked) } },
            )
            if (current !in value.saved) {
                TextButton(onClick = {
                    scope.launch {
                        store.rememberWifiNetwork(current, value.mode)
                        WifiDiscovery.acknowledge(current)
                    }
                }) {
                    Text(if (value.mode == WifiNetworkListMode.WHITELIST) "Запомнить и исключить" else "Запомнить сеть")
                }
            }
        }

        Text("Сохранённые сети", style = MaterialTheme.typography.titleSmall)
        if (value.saved.isEmpty()) {
            Text("Нет сохранённых сетей", style = MaterialTheme.typography.bodySmall)
        } else {
            value.saved.sorted().forEach { name ->
                WifiNetworkRow(
                    name = name,
                    selected = name in value.selected,
                    enabled = value.enabled && value.mode != WifiNetworkListMode.ALL,
                    onSelect = { checked -> scope.launch { store.selectWifiNetwork(name, checked) } },
                    onDelete = { deleting = name },
                )
            }
        }

        if (!showInput) {
            TextButton(onClick = { showInput = true }) { Text("Ввести название сети") }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; inputError = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Имя сети (SSID)") },
                isError = inputError != null,
                supportingText = { inputError?.let { Text(it) } },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    showInput = false
                    input = ""
                    inputError = null
                }) { Text("Отмена") }
                TextButton(onClick = {
                    val name = normalizeWifiSsid(input)
                    when {
                        name == null || name.toByteArray(Charsets.UTF_8).size > 32 ->
                            inputError = "Введите имя Wi-Fi длиной до 32 байт"
                        name in value.saved -> inputError = "Эта сеть уже сохранена"
                        else -> scope.launch {
                            if (store.rememberWifiNetwork(name, value.mode)) {
                                showInput = false
                                input = ""
                                inputError = null
                            } else {
                                inputError = "Настройки автопаузы изменились. Повторите попытку."
                            }
                        }
                    }
                }) { Text(if (value.mode == WifiNetworkListMode.WHITELIST) "Запомнить и исключить" else "Запомнить") }
            }
        }
    }

    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить сеть?") },
            text = { Text("$name будет удалена из сохранённых сетей и из выбранного списка автопаузы.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        WifiDiscovery.acknowledge(name)
                        store.forgetWifiNetwork(name)
                        deleting = null
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun WifiNetworkRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = onSelect, enabled = enabled)
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}
