package com.shod.client.ui.screens.deploy

import com.shod.client.deploy.inventory.FoundServer
import com.shod.client.deploy.inventory.ServerPlace

private fun runtimeLabel(runtime: String): String = when (runtime) {
    "systemd" -> "служба systemd"
    "docker" -> "Docker"
    "process" -> "процесс"
    else -> ""
}

/** One line for the "На сервере" list: what it is, then how it runs. */
internal fun describeServer(server: FoundServer, profilePeerPort: Int?): Pair<String, String> {
    if (server.place == ServerPlace.LEFTOVER || !server.installed) {
        val what = if (server.place == ServerPlace.LEFTOVER) "Остановленная служба shod" else "Остатки прежней установки shod"
        return what to server.name
    }
    val title = "shod " + (server.version ?: "(версия неизвестна)")
    val detail = listOfNotNull(
        if (server.place == ServerPlace.OTHER) "установлен не этим приложением: ${server.name}" else null,
        runtimeLabel(server.runtime).ifBlank { null },
        server.peerPort?.let { "порт $it" },
        when (server.state) {
            "running" -> "работает"
            "stopped" -> "остановлен"
            else -> null
        },
        if (profilePeerPort != null && server.peerPort == profilePeerPort &&
            server.place == ServerPlace.STANDARD) "подключение профиля" else null,
    ).joinToString(", ")
    return title to detail
}
