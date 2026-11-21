package com.shod.client.ui.screens

import com.shod.client.ui.components.appSectionCardColor
import com.shod.client.ui.components.appSectionCardBorderColor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shod.client.vpn.power.BatterySaver
import com.shod.client.SettingsStore
import com.shod.client.ShodConstants
import com.shod.client.integration.vk.transport.turn.TurnSubnetRegistry
import com.shod.client.ui.WifiAutoPauseContent
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenRouting: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var showConnectionScreen by rememberSaveable { mutableStateOf(false) }
    val isOwner by settingsStore.uiOwnerMode.collectAsState(initial = false)

    BackHandler(enabled = showConnectionScreen) { showConnectionScreen = false }

    Column(modifier = modifier.fillMaxSize()) {
        SettingsHeader(
            title = if (showConnectionScreen) "Соединение" else "Настройки",
            onBack = if (showConnectionScreen) ({ showConnectionScreen = false }) else onClose,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (showConnectionScreen) {
            ConnectionSettingsContent(settingsStore = settingsStore, modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsCard {
                    SettingsDropdown(
                        label = "Режим",
                        selected = if (isOwner) "Свой сервер" else "Клиент",
                        options = listOf("Клиент", "Свой сервер"),
                    ) { choice ->
                        scope.launch { settingsStore.saveUiOwnerMode(choice == "Свой сервер") }
                    }
                }
                SettingsCard {
                    SettingsDropdown(
                        label = "Оформление",
                        selected = when (themeMode) {
                            ShodConstants.Theme.MODE_LIGHT -> "Светлая"
                            ShodConstants.Theme.MODE_DARK -> "Тёмная"
                            else -> "Система"
                        },
                        options = listOf("Система", "Светлая", "Тёмная"),
                    ) { choice ->
                        onThemeModeChange(
                            when (choice) {
                                "Светлая" -> ShodConstants.Theme.MODE_LIGHT
                                "Тёмная" -> ShodConstants.Theme.MODE_DARK
                                else -> ShodConstants.Theme.MODE_SYSTEM
                            },
                        )
                    }
                }
                SettingsCard {
                    SettingsNavigationRow("Соединение") { showConnectionScreen = true }
                }
                SettingsCard {
                    SettingsNavigationRow("Маршрутизация", description = "Правила для приложений, доменов и IP-адресов", onClick = onOpenRouting)
                }
            }
        }
    }
}

@Composable
private fun ConnectionSettingsContent(settingsStore: SettingsStore, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val srtpMode by settingsStore.srtpMode.collectAsState(initial = ShodConstants.SrtpMode.DEFAULT)
    val transport by settingsStore.turnTransport.collectAsState(initial = ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT)
    val obfsMode by settingsStore.obfsMode.collectAsState(initial = ShodConstants.Tunnel.DEFAULT_OBFS_MODE)
    val autoPauseOnWifi by settingsStore.autoPauseOnWifi.collectAsState(initial = false)
    val batterySaverMode by settingsStore.batterySaverMode.collectAsState(initial = BatterySaver.DEFAULT_MODE)
    val batterySaverPercent by settingsStore.batterySaverPercent.collectAsState(initial = BatterySaver.DEFAULT_PERCENT)
    val fingerprint by settingsStore.selectedFingerprint.collectAsState(initial = "firefox")
    val selectedSubnet by settingsStore.selectedTurnSubnet.collectAsState(initial = "")
    val subnetIpLimit by settingsStore.turnSubnetIpLimit.collectAsState(initial = "ALL")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("Защита") {
            SettingsDropdown("Обфускация", if (obfsMode == "unified") "Унифицированная" else "Стандартная", listOf("Стандартная", "Унифицированная")) { choice ->
                scope.launch { settingsStore.saveObfsMode(if (choice == "Унифицированная") "unified" else ShodConstants.Tunnel.DEFAULT_OBFS_MODE) }
            }
            SettingsDivider()
            SettingsDropdown("SRTP", when (srtpMode) {
                ShodConstants.SrtpMode.WRAP -> "WRAP-SRTP"
                ShodConstants.SrtpMode.DTLS -> "DTLS-SRTP"
                else -> "Автоматический"
            }, listOf("Автоматический", "WRAP-SRTP", "DTLS-SRTP")) { choice ->
                scope.launch {
                    settingsStore.saveSrtpMode(when (choice) {
                        "WRAP-SRTP" -> ShodConstants.SrtpMode.WRAP
                        "DTLS-SRTP" -> ShodConstants.SrtpMode.DTLS
                        else -> ShodConstants.SrtpMode.DEFAULT
                    })
                }
            }
            SettingsDivider()
            val fingerprintOptions = listOf("chrome" to "Chrome", "safari" to "Safari", "firefox" to "Firefox", "edge" to "Edge", "opera" to "Opera")
            SettingsDropdown("Отпечаток клиента", fingerprintOptions.firstOrNull { it.first == fingerprint }?.second ?: "Firefox", fingerprintOptions.map { it.second }) { choice ->
                scope.launch { fingerprintOptions.firstOrNull { it.second == choice }?.let { settingsStore.saveFingerprint(it.first) } }
            }
        }

        SettingsSection("Транспорт") {
            SettingsDropdown("Транспорт", if (transport == ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS) "TCP / TLS" else "UDP", listOf("UDP", "TCP / TLS")) { choice ->
                scope.launch { settingsStore.saveTurnTransport(if (choice == "TCP / TLS") ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS else ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT) }
            }
        }

        SettingsSection("Сеть") {
            SettingsSwitch(
                title = "Приостанавливать подключение при Wi-Fi",
                checked = autoPauseOnWifi,
                onCheckedChange = { enabled -> scope.launch { settingsStore.saveAutoPauseOnWifi(enabled) } },
            )
            WifiAutoPauseContent(settingsStore)
            SettingsDivider()
            val subnetOptions = listOf("" to "По умолчанию VK") + TurnSubnetRegistry.subnets.map { it.id to "${it.title} · ${it.count} IP" }
            SettingsDropdown("TURN-подсеть", subnetOptions.firstOrNull { it.first == selectedSubnet }?.second ?: "По умолчанию VK", subnetOptions.map { it.second }) { choice ->
                scope.launch {
                    subnetOptions.firstOrNull { it.second == choice }?.let { (id, _) ->
                        settingsStore.saveSelectedTurnSubnet(id)
                        val available = TurnSubnetRegistry.getSubnet(id)?.count
                        val requested = subnetIpLimit.toIntOrNull()
                        if (available != null && requested != null && available < requested) settingsStore.saveTurnSubnetIpLimit(available.toString())
                    }
                }
            }
            val ipOptions = listOf("ALL" to "Все", "1" to "1", "2" to "2", "4" to "4", "9" to "9")
            SettingsDropdown("Количество IP", ipOptions.firstOrNull { it.first == subnetIpLimit }?.second ?: "Все", ipOptions.map { it.second }) { choice ->
                scope.launch {
                    ipOptions.firstOrNull { it.second == choice }?.let { (value, _) ->
                        val count = value.toIntOrNull()
                        val current = TurnSubnetRegistry.getSubnet(selectedSubnet)
                        if (count != null && current != null && current.count < count) {
                            TurnSubnetRegistry.subnets.firstOrNull { it.count >= count }?.let { settingsStore.saveSelectedTurnSubnet(it.id) }
                        }
                        settingsStore.saveTurnSubnetIpLimit(value)
                    }
                }
            }
        }

        SettingsSection("Энергосбережение") {
            SettingsDropdown(
                BatterySaver.TITLE,
                BatterySaver.MODES.firstOrNull { it.first == batterySaverMode }?.second ?: BatterySaver.MODES.first().second,
                BatterySaver.MODES.map { it.second },
            ) { choice ->
                scope.launch { BatterySaver.MODES.firstOrNull { it.second == choice }?.let { settingsStore.saveBatterySaverMode(it.first) } }
            }
            if (batterySaverMode == BatterySaver.MODE_LEVEL) {
                SettingsDivider()
                SettingsDropdown(BatterySaver.LEVEL_TITLE, "$batterySaverPercent%", (BatterySaver.PERCENT_CHOICES + batterySaverPercent).distinct().sorted().map { "$it%" }) { choice ->
                    scope.launch { choice.removeSuffix("%").toIntOrNull()?.let { settingsStore.saveBatterySaverPercent(it) } }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier.size(24.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = appSectionCardColor()),
        border = BorderStroke(1.dp, appSectionCardBorderColor()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        SettingsCard(content)
    }
}

@Composable
private fun SettingsNavigationRow(label: String, description: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 58.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDropdown(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected, maxLines = 1)
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Открыть список",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = ShodShapes.Card,
                containerColor = appSectionCardColor(),
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, appSectionCardBorderColor()),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { expanded = false; onSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
