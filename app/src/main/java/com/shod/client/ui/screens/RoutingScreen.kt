package com.shod.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shod.client.SettingsStore
import com.shod.client.TunnelManager
import com.shod.client.ui.screens.routing.rules.RoutingRuleKind
import com.shod.client.ui.screens.routing.rules.RoutingRuleListDialog
import com.shod.client.vpn.routing.DomainRoutingRules
import com.shod.client.vpn.routing.IpRoutingRules
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun RoutingScreen(
    settingsStore: SettingsStore,
    savedExcluded: String,
    savedWhitelist: String,
    showSystemApps: Boolean,
    isWhitelist: Boolean,
    serverRoutingActive: Boolean = TunnelManager.serverRoutingActive.value,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val serverActive by TunnelManager.serverRoutingActive.collectAsState(initial = serverRoutingActive)
    val domainEnabled by settingsStore.domainRoutingEnabled.collectAsState(initial = false)
    val domainMode by settingsStore.domainIsWhitelist.collectAsState(initial = false)
    val domainBlack by settingsStore.directDomainsBlack.collectAsState(initial = "")
    val domainWhite by settingsStore.directDomainsWhite.collectAsState(initial = "")
    val templateEnabled by settingsStore.templateDomainsEnabled.collectAsState(initial = false)
    val ipEnabled by settingsStore.ipRoutingEnabled.collectAsState(initial = false)
    val ipMode by settingsStore.ipIsWhitelist.collectAsState(initial = false)
    val ipBlack by settingsStore.directIpsBlack.collectAsState(initial = "")
    val ipWhite by settingsStore.directIpsWhite.collectAsState(initial = "")

    val templateDomains = remember {
        runCatching {
            context.assets.open("domains.txt").bufferedReader().use { DomainRoutingRules.parse(it.readText()) }
        }.getOrDefault(emptyList())
    }
    var showDomainEditor by remember { mutableStateOf(false) }
    var showIpEditor by remember { mutableStateOf(false) }

    LaunchedEffect(serverActive) {
        if (serverActive) {
            showDomainEditor = false
            showIpEditor = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Маршрутизация", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (serverActive) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "Маршрутизация управляется сервером. Локальные правила временно недоступны.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        RoutingSummaryCard(
            title = "Маршрутизация доменов",
            summary = if (domainMode) "Белый список: ${DomainRoutingRules.parse(domainWhite).size} доменов"
                else "Чёрный список: ${DomainRoutingRules.parse(domainBlack).size} доменов",
            enabled = domainEnabled,
            actionLabel = "Редактировать домены",
            blocked = serverActive,
            onClick = { showDomainEditor = true },
        )
        RoutingSummaryCard(
            title = "Маршрутизация IP-адресов",
            summary = if (ipMode) "Белый список: ${IpRoutingRules.parse(ipWhite).size} правил"
                else "Чёрный список: ${IpRoutingRules.parse(ipBlack).size} правил",
            enabled = ipEnabled,
            actionLabel = "Редактировать адреса",
            blocked = serverActive,
            onClick = { showIpEditor = true },
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Маршрутизация приложений", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Редактор приложений переносится следующим этапом. Сохранённые правила не изменяются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDomainEditor && !serverActive) {
        RoutingRuleListDialog(
            kind = RoutingRuleKind.Domains,
            initialBlacklist = if (domainBlack.isBlank() && templateEnabled) templateDomains.joinToString(",") else domainBlack,
            initialWhitelist = domainWhite,
            initialWhitelistMode = domainMode,
            templateDomains = templateDomains,
            templateDomainsEnabled = templateEnabled,
            onSave = { black, white, whitelistMode, useTemplate ->
                scope.launch {
                    settingsStore.saveDomainIsWhitelist(whitelistMode)
                    settingsStore.saveDomainRoutingEnabled(true)
                    settingsStore.saveDirectDomainsBlack(black)
                    settingsStore.saveDirectDomainsWhite(white)
                    settingsStore.saveTemplateDomainsEnabled(useTemplate)
                    TunnelManager.pushLocalRouting(context)
                }
            },
            onDismiss = { showDomainEditor = false },
        )
    }

    if (showIpEditor && !serverActive) {
        RoutingRuleListDialog(
            kind = RoutingRuleKind.Ips,
            initialBlacklist = ipBlack,
            initialWhitelist = ipWhite,
            initialWhitelistMode = ipMode,
            onSave = { black, white, whitelistMode, _ ->
                scope.launch {
                    settingsStore.saveIpIsWhitelist(whitelistMode)
                    settingsStore.saveIpRoutingEnabled(true)
                    settingsStore.saveDirectIpsBlack(black)
                    settingsStore.saveDirectIpsWhite(white)
                    TunnelManager.pushLocalRouting(context)
                }
            },
            onDismiss = { showIpEditor = false },
        )
    }
}

@Composable
private fun RoutingSummaryCard(
    title: String,
    summary: String,
    enabled: Boolean,
    actionLabel: String,
    blocked: Boolean,
    onClick: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (enabled) summary else "Отключено · $summary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onClick, enabled = !blocked, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}
