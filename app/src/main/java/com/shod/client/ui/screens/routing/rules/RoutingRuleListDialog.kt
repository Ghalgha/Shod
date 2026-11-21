package com.shod.client.ui.screens.routing.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.design.ShodSizes
import com.shod.client.vpn.routing.DomainRoutingRules
import com.shod.client.vpn.routing.IpRoutingRules

internal enum class RoutingRuleKind { Domains, Ips }

@Composable
internal fun RoutingRuleListDialog(
    kind: RoutingRuleKind,
    initialBlacklist: String,
    initialWhitelist: String,
    initialWhitelistMode: Boolean,
    templateDomains: List<String> = emptyList(),
    templateDomainsEnabled: Boolean = false,
    onSave: (blacklist: String, whitelist: String, whitelistMode: Boolean, templateEnabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDomain = kind == RoutingRuleKind.Domains
    val parseRules: (String) -> List<String> = if (isDomain) DomainRoutingRules::parse else IpRoutingRules::parse
    var whitelistMode by rememberSaveable(kind) { mutableStateOf(initialWhitelistMode) }
    var blacklistText by rememberSaveable(kind) { mutableStateOf(parseRules(initialBlacklist).joinToString("\n")) }
    var whitelistText by rememberSaveable(kind) { mutableStateOf(parseRules(initialWhitelist).joinToString("\n")) }
    var templateEnabled by rememberSaveable(kind) { mutableStateOf(templateDomainsEnabled) }

    val activeText = if (whitelistMode) whitelistText else blacklistText
    val parsedCount = remember(activeText, kind) { parseRules(activeText).size }
    val title = if (isDomain) "Маршрутизация доменов" else "Маршрутизация адресов"
    val listLabel = if (isDomain) "Список доменов" else "Список адресов"
    val placeholder = if (isDomain) {
        "yandex.ru, mail.ru\nkinopoisk.ru"
    } else {
        "1.2.3.4/32\n1.2.3.4\n1.2.3.4-1.2.3.255"
    }

    val save = {
        onSave(
            parseRules(blacklistText).joinToString(","),
            parseRules(whitelistText).joinToString(","),
            whitelistMode,
            templateEnabled,
        )
        onDismiss()
    }

    Dialog(
        onDismissRequest = save,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = ShodShapes.Dialog,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = save) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeButton("Чёрный список", selected = !whitelistMode, onClick = { whitelistMode = false })
                    ModeButton("Белый список", selected = whitelistMode, onClick = { whitelistMode = true })
                }

                if (isDomain && !whitelistMode && templateDomains.isNotEmpty()) {
                    Surface(
                        shape = ShodShapes.Control,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Готовый список RU доменов", style = MaterialTheme.typography.bodyMedium)
                                Text("Исключить домены из готового набора", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = templateEnabled,
                                onCheckedChange = { enabled ->
                                    val current = DomainRoutingRules.parse(blacklistText)
                                    val templateSet = templateDomains.toSet()
                                    val updated = if (enabled) (current + templateDomains).distinct()
                                        else current.filterNot(templateSet::contains)
                                    blacklistText = updated.joinToString("\n")
                                    templateEnabled = enabled
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                OutlinedTextField(
                    value = activeText,
                    onValueChange = { if (whitelistMode) whitelistText = it else blacklistText = it },
                    label = { Text(listLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text(placeholder) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Распознано: $parsedCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeText.isNotBlank()) {
                            OutlinedButton(
                                onClick = { if (whitelistMode) whitelistText = "" else blacklistText = "" },
                                modifier = Modifier.height(32.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = ShodShapes.Pill,
                            ) { Text("Очистить", style = MaterialTheme.typography.labelSmall) }
                        }
                        Button(
                            onClick = save,
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            shape = ShodShapes.Pill,
                        ) { Text("Готово", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f).height(ShodSizes.ControlHeight),
            shape = ShodShapes.Pill,
            colors = ButtonDefaults.buttonColors(),
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f).height(ShodSizes.ControlHeight),
            shape = ShodShapes.Pill,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
