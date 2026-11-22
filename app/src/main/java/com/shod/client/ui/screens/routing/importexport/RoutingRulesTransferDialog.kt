package com.shod.client.ui.screens.routing.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.shod.client.vpn.routing.importexport.AppRoutingTextParser
import com.shod.client.vpn.routing.DomainRoutingRules
import com.shod.client.vpn.routing.IpRoutingRules
import org.json.JSONArray
import org.json.JSONObject

data class RoutingRulesBackup(
    val appBlacklist: Set<String>,
    val appWhitelist: Set<String>,
    val appWhitelistMode: Boolean,
    val domainBlacklist: List<String>,
    val domainWhitelist: List<String>,
    val domainWhitelistMode: Boolean,
    val templateDomainsEnabled: Boolean,
    val ipBlacklist: List<String>,
    val ipWhitelist: List<String>,
    val ipWhitelistMode: Boolean,
    val includeApps: Boolean = false,
    val includeDomains: Boolean = false,
    val includeIps: Boolean = false,
)

@Composable
fun RoutingRulesTransferDialog(
    current: RoutingRulesBackup,
    onImport: (RoutingRulesBackup) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var apps by remember { mutableStateOf(true) }
    var domains by remember { mutableStateOf(true) }
    var ips by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт и экспорт правил") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выберите категории для переноса через буфер обмена.")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = apps, onCheckedChange = { apps = it })
                    Text("Приложения")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = domains, onCheckedChange = { domains = it })
                    Text("Домены")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = ips, onCheckedChange = { ips = it })
                    Text("IP-адреса")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = apps || domains || ips,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val root = JSONObject().put("version", 2)
                            if (apps) root.put("apps", JSONObject()
                                .put("is_whitelist", current.appWhitelistMode)
                                .put("active_mode", if (current.appWhitelistMode) "whitelist" else "blacklist")
                                .put("blacklist", JSONArray(current.appBlacklist.sorted()))
                                .put("whitelist", JSONArray(current.appWhitelist.sorted())))
                            if (domains) root.put("domains", JSONObject()
                                .put("is_whitelist", current.domainWhitelistMode)
                                .put("active_mode", if (current.domainWhitelistMode) "whitelist" else "blacklist")
                                .put("template_domains", current.templateDomainsEnabled)
                                .put("blacklist", JSONArray(current.domainBlacklist))
                                .put("whitelist", JSONArray(current.domainWhitelist))
                                .put("direct_domains", JSONArray(if (current.domainWhitelistMode) current.domainWhitelist else current.domainBlacklist)))
                            if (ips) root.put("ips", JSONObject()
                                .put("is_whitelist", current.ipWhitelistMode)
                                .put("active_mode", if (current.ipWhitelistMode) "whitelist" else "blacklist")
                                .put("blacklist", JSONArray(current.ipBlacklist))
                                .put("whitelist", JSONArray(current.ipWhitelist))
                                .put("direct_ips", JSONArray(if (current.ipWhitelistMode) current.ipWhitelist else current.ipBlacklist)))
                            clipboard.setText(AnnotatedString(root.toString(2)))
                        },
                    ) { Text("Экспорт") }
                    OutlinedButton(
                        enabled = apps || domains || ips,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val raw = clipboard.getText()?.text.orEmpty()
                            val root = runCatching { JSONObject(raw) }.getOrNull()
                            if (root != null) {
                                val result = current.copy(
                                    includeApps = apps && root.has("apps"),
                                    includeDomains = domains && root.has("domains"),
                                    includeIps = ips && root.has("ips"),
                                    appBlacklist = if (apps && root.has("apps")) packages(root.optJSONObject("apps")?.optJSONArray("blacklist")) else current.appBlacklist,
                                    appWhitelist = if (apps && root.has("apps")) packages(root.optJSONObject("apps")?.optJSONArray("whitelist")) else current.appWhitelist,
                                    appWhitelistMode = if (apps && root.has("apps")) mode(root.optJSONObject("apps")!!, current.appWhitelistMode) else current.appWhitelistMode,
                                    domainBlacklist = if (domains && root.has("domains")) domains(root.optJSONObject("domains")!!, "blacklist", "direct_domains", mode(root.optJSONObject("domains")!!, current.domainWhitelistMode)) else current.domainBlacklist,
                                    domainWhitelist = if (domains && root.has("domains")) domains(root.optJSONObject("domains")!!, "whitelist", "direct_domains", mode(root.optJSONObject("domains")!!, current.domainWhitelistMode), true) else current.domainWhitelist,
                                    domainWhitelistMode = if (domains && root.has("domains")) mode(root.optJSONObject("domains")!!, current.domainWhitelistMode) else current.domainWhitelistMode,
                                    templateDomainsEnabled = if (domains && root.has("domains")) root.optJSONObject("domains")!!.optBoolean("template_domains", false) else current.templateDomainsEnabled,
                                    ipBlacklist = if (ips && root.has("ips")) ips(root.optJSONObject("ips")!!, "blacklist", "direct_ips", mode(root.optJSONObject("ips")!!, current.ipWhitelistMode)) else current.ipBlacklist,
                                    ipWhitelist = if (ips && root.has("ips")) ips(root.optJSONObject("ips")!!, "whitelist", "direct_ips", mode(root.optJSONObject("ips")!!, current.ipWhitelistMode), true) else current.ipWhitelist,
                                    ipWhitelistMode = if (ips && root.has("ips")) mode(root.optJSONObject("ips")!!, current.ipWhitelistMode) else current.ipWhitelistMode,
                                )
                                if ((apps && root.has("apps")) || (domains && root.has("domains")) || (ips && root.has("ips"))) {
                                    onImport(result)
                                    onDismiss()
                                }
                            } else {
                                val parsedApps = if (apps) AppRoutingTextParser.parse(raw) else null
                                val parsedDomains = if (domains) DomainRoutingRules.parse(raw) else emptyList()
                                val parsedIps = if (ips) IpRoutingRules.parse(raw) else emptyList()
                                if (parsedApps != null || (domains && parsedDomains.isNotEmpty()) || (ips && parsedIps.isNotEmpty())) {
                                    onImport(current.copy(
                                        includeApps = parsedApps != null,
                                        includeDomains = domains && parsedDomains.isNotEmpty(),
                                        includeIps = ips && parsedIps.isNotEmpty(),
                                        appBlacklist = parsedApps?.let { current.appBlacklist + it.blacklist } ?: current.appBlacklist,
                                        appWhitelist = parsedApps?.let { current.appWhitelist + it.whitelist } ?: current.appWhitelist,
                                        domainBlacklist = if (domains && !current.domainWhitelistMode) (current.domainBlacklist + parsedDomains).distinct().take(1024) else current.domainBlacklist,
                                        domainWhitelist = if (domains && current.domainWhitelistMode) (current.domainWhitelist + parsedDomains).distinct().take(1024) else current.domainWhitelist,
                                        ipBlacklist = if (ips && !current.ipWhitelistMode) (current.ipBlacklist + parsedIps).distinct().take(1024) else current.ipBlacklist,
                                        ipWhitelist = if (ips && current.ipWhitelistMode) (current.ipWhitelist + parsedIps).distinct().take(1024) else current.ipWhitelist,
                                    ))
                                    onDismiss()
                                }
                            }
                        },
                    ) { Text("Импорт") }
                }
                Text("JSON сохраняет режимы и оба списка. Текстовые списки доменов и IP добавляются к активному списку.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun mode(obj: JSONObject, fallback: Boolean): Boolean =
    if (obj.has("active_mode")) obj.optString("active_mode") == "whitelist"
    else obj.optBoolean("is_whitelist", fallback)

private fun packages(array: JSONArray?): Set<String> = buildSet {
    if (array != null) for (i in 0 until array.length()) {
        val value = array.optString(i)
        if (com.shod.client.vpn.routing.importexport.AppRoutingTextParser.isValidPackage(value)) add(value)
    }
}

private fun domains(obj: JSONObject, key: String, flatKey: String, isWhite: Boolean, forceFlat: Boolean = false): List<String> {
    val listed = obj.optJSONArray(key)
    val bothListsEmpty = (obj.optJSONArray("blacklist")?.length() ?: 0) == 0 &&
        (obj.optJSONArray("whitelist")?.length() ?: 0) == 0
    val source = listed?.takeIf { it.length() > 0 } ?: if (bothListsEmpty) obj.optJSONArray(flatKey) else null
    return source?.let { array -> (0 until array.length()).map { DomainRoutingRules.clean(array.optString(it)) }.filter(DomainRoutingRules::isValid).distinct().take(1024) }.orEmpty()
}

private fun ips(obj: JSONObject, key: String, flatKey: String, isWhite: Boolean, forceFlat: Boolean = false): List<String> {
    val listed = obj.optJSONArray(key)
    val bothListsEmpty = (obj.optJSONArray("blacklist")?.length() ?: 0) == 0 &&
        (obj.optJSONArray("whitelist")?.length() ?: 0) == 0
    val source = listed?.takeIf { it.length() > 0 } ?: if (bothListsEmpty) obj.optJSONArray(flatKey) else null
    return source?.let { array -> (0 until array.length()).map { IpRoutingRules.clean(it) }.filter(IpRoutingRules::isValid).distinct().take(1024) }.orEmpty()
}
