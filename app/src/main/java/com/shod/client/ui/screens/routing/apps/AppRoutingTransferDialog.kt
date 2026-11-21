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
                            val appJson = json?.optJSONObject("apps")
                            if (appJson != null) {
                                val black = readPackages(appJson.optJSONArray("blacklist"))
                                val white = readPackages(appJson.optJSONArray("whitelist"))
                                val mode = if (appJson.has("active_mode")) {
                                    appJson.optString("active_mode") == "whitelist"
                                } else appJson.optBoolean("is_whitelist", isWhitelist)
                                onImport(AppRoutingLists(black, white), mode)
                                onDismiss()
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

private fun readPackages(array: JSONArray?): Set<String> =
    buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (AppRoutingTextParser.isValidPackage(value)) add(value)
        }
    }
