package com.shod.client.ui.screens.routing.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.shod.client.vpn.routing.importexport.AppRoutingLists
import com.shod.client.vpn.routing.importexport.AppRoutingTextParser
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun AppRoutingTransferDialog(
    blacklist: Set<String>,
    whitelist: Set<String>,
    isWhitelist: Boolean,
    onImport: (AppRoutingLists, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт и экспорт приложений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Формат совместим с резервными копиями Shod (JSON версии 2) и текстовыми списками.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val apps = JSONObject()
                                .put("is_whitelist", isWhitelist)
                                .put("active_mode", if (isWhitelist) "whitelist" else "blacklist")
                                .put("blacklist", JSONArray(blacklist.sorted()))
                                .put("whitelist", JSONArray(whitelist.sorted()))
                            val root = JSONObject().put("version", 2).put("apps", apps)
                            clipboard.setText(AnnotatedString(root.toString(2)))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Экспорт") }
                    OutlinedButton(
                        onClick = {
                            val raw = clipboard.getText()?.text.orEmpty()
                            val json = runCatching { JSONObject(raw) }.getOrNull()
                            if (json?.has("apps") == true) {
                                // A JSON backup must pass every check; never reinterpret a bad backup as text.
                                parseBackup(json)?.let { (lists, mode) ->
                                    onImport(lists, mode)
                                    onDismiss()
                                }
                            } else {
                                AppRoutingTextParser.parse(raw)?.let {
                                    onImport(it, isWhitelist)
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Импорт из буфера") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun parseBackup(root: JSONObject): Pair<AppRoutingLists, Boolean>? =
    runCatching {
        val version = root.get("version")
        require(version is Number && version.toDouble() == 2.0)

        val apps = root.get("apps") as? JSONObject ?: error("Invalid apps object")
        val isWhitelist = apps.get("is_whitelist") as? Boolean
            ?: error("Invalid routing mode")
        val activeMode = apps.get("active_mode") as? String
            ?: error("Invalid active mode")
        require(activeMode == "whitelist" || activeMode == "blacklist")
        require((activeMode == "whitelist") == isWhitelist)

        fun readPackages(key: String): Set<String> {
            val array = apps.get(key) as? JSONArray ?: error("Invalid $key list")
            return buildSet {
                for (index in 0 until array.length()) {
                    val value = array.get(index) as? String ?: error("Invalid package entry")
                    require(AppRoutingTextParser.isValidPackage(value))
                    add(value)
                }
            }
        }

        AppRoutingLists(
            blacklist = readPackages("blacklist"),
            whitelist = readPackages("whitelist"),
        ) to isWhitelist
    }.getOrNull()
