package com.shod.client.deploy.plan

import com.shod.client.ShodConstants
import com.shod.client.deploy.inventory.FoundServer
import com.shod.client.deploy.inventory.ServerInventory
import com.shod.client.deploy.inventory.isLine25

/** Server ports selected by the user for a new installation. */
internal data class ServerPorts(val peer: Int, val web: Int)

internal enum class PlanKind { INSTALL, UPDATE, REPLACE }

internal data class PlanAction(
    val kind: PlanKind,
    val peerPort: Int,
    val webPort: Int,
    val title: String,
    val detail: String,
) {
    val destructive: Boolean get() = kind == PlanKind.REPLACE
}

internal data class DeployPlan(
    val servers: List<FoundServer>,
    val actions: List<PlanAction>,
    val notes: List<String>,
)

private fun runtimeLabel(runtime: String): String = when (runtime) {
    "systemd" -> "служба systemd"
    "docker" -> "Docker"
    "process" -> "процесс"
    else -> ""
}

internal fun planDeploy(
    inventory: ServerInventory,
    manualPorts: ServerPorts?,
    profilePeerPort: Int?,
    dockerInstall: Boolean,
    appVersion: String,
): DeployPlan {
    val servers = inventory.servers
    val notes = mutableListOf<String>()
    val standard = servers.firstOrNull { it.place == ServerPlace.STANDARD }
    val liveStandard = standard?.takeIf { it.installed }
    val others = servers.filter { it.place == ServerPlace.OTHER }
    val leftovers = servers.filter { it.place == ServerPlace.LEFTOVER }
    for (other in others) {
        notes += "Ещё один сервер shod установлен не этим приложением (${other.name}" +
            (other.peerPort?.let { ", порт $it" } ?: "") + "). Приложение его не трогает" +
            // what the user can do: the standard place's cleanup would stop it, so its actions are withheld
            if (liveStandard != null) ". Пока он работает, обновить или заменить сервер на стандартном месте нельзя — " +
                "это остановило бы и его. Остановите его и проверьте сервер заново" else ""
    }

    val wanted = if (dockerInstall) "docker" else "systemd"
    fun runtimeChange(server: FoundServer): String =
        if (server.runtime in setOf("systemd", "docker") && server.runtime != wanted) {
            " Сейчас ${runtimeLabel(server.runtime)}, будет ${runtimeLabel(wanted)}."
        } else ""
    val removed = if (leftovers.isEmpty()) "" else " Будет удалено: ${leftovers.joinToString { it.name }}."

    // ports a new server cannot take: another installed shod server's (a leftover reports the defaults it would have used,
    // and goes), or a program's the check saw on a default port
    val taken = servers.filter { it.installed }.flatMap { listOfNotNull(it.peerPort, it.webPort) }.toSet()
    fun clash(peer: Int, web: Int): String? {
        inventory.busy.firstOrNull { it.port == peer || it.port == web }?.let {
            return "Порт ${it.port} занят: ${it.holder}. Укажите другие порты («Ручные порты»)"
        }
        if (peer in taken || web in taken) {
            return "Порт ${if (peer in taken) peer else web} занят другим сервером shod. Укажите другие порты («Ручные порты»)"
        }
        return null
    }

    val actions = mutableListOf<PlanAction>()
    fun update(server: FoundServer, title: String) {
        val peer = server.peerPort ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT
        val web = server.webPort ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT
        actions += PlanAction(
            PlanKind.UPDATE, peer, web,
            title,
            "shod ${server.version ?: "?"} → $appVersion на порту $peer. " +
                "Клиенты и настройки сохранятся; пароль туннеля и вход в web-панель — из «Авторизации» этого профиля." +
                runtimeChange(server) + removed,
        )
    }
    fun newServer(kind: PlanKind, title: String, detail: (Int, Int) -> String) {
        // a manual port that is already another server's (2.5.1 prefilled the stock ones) means "not chosen" next to it
        val peer = manualPorts?.peer ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT
        val web = manualPorts?.web ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT
        val problem = clash(peer, web)
        if (problem != null) notes += problem else actions += PlanAction(kind, peer, web, title, detail(peer, web))
    }

    liveStandard?.takeIf { isLine25(it.version) && others.isEmpty() }?.let { update(it, "Обновить") }

    when {
        liveStandard != null && !isLine25(liveStandard.version) && others.isEmpty() -> {
            val old = "shod ${liveStandard.version ?: "(версия неизвестна)"}"
            actions += PlanAction(
                PlanKind.REPLACE,
                liveStandard.peerPort ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
                liveStandard.webPort ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT,
                "Заменить",
                "$old заменяется на $appVersion на тех же портах. Клиенты и пароли сохранятся, но приложения 2.1 " +
                    "перестанут подключаться — им нужно приложение 2.5. Пароль туннеля и вход в web-панель — из " +
                    "«Авторизации» этого профиля." + runtimeChange(liveStandard) + removed,
            )
        }
        liveStandard == null && others.isEmpty() -> {
            val kept = if (standard != null) " Клиенты прежней установки сохранятся." else ""
            newServer(PlanKind.INSTALL, "Установить") { peer, web ->
                "shod $appVersion на порты $peer и $web.$kept$removed"
            }
        }
    }
    return DeployPlan(servers, actions, notes)
}
