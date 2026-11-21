package com.shod.client.deploy.inventory

import com.shod.client.ShodConstants

internal enum class ServerPlace { STANDARD, OTHER, LEFTOVER }

internal data class FoundServer(
    val place: ServerPlace,
    val runtime: String,
    val state: String,
    val version: String?,
    val peerPort: Int?,
    val webPort: Int?,
    val name: String,
) {
    /** Something runs or can be started there; "leftover" is only a binary, a store or a stopped foreign unit. */
    val installed: Boolean get() = state != "leftover" && place != ServerPlace.LEFTOVER
}

internal data class BusyPort(val proto: String, val port: Int, val holder: String)

internal data class ServerInventory(val servers: List<FoundServer>, val busy: List<BusyPort>)

/** null unless the check finished (its last line is Shod_INV|end): a half-read server is never planned on. */
internal fun parseServerInventory(output: String): ServerInventory? {
    val servers = mutableListOf<FoundServer>()
    val busy = mutableListOf<BusyPort>()
    var finished = false
    var invalid = false
    for (raw in output.lineSequence()) {
        val line = raw.trim()
        if (!line.startsWith("Shod_INV|")) continue
        if (finished) {
            invalid = true
            break
        }
        val parts = line.split('|')
        when (parts.getOrNull(1)) {
            "end" -> {
                if (parts.size != 2) invalid = true
                finished = true
            }
            "server" -> {
                val fields = parts.drop(2).mapNotNull { part ->
                    part.indexOf('=').takeIf { it > 0 }?.let { part.substring(0, it) to part.substring(it + 1) }
                }.toMap()
                val place = when (fields["place"]) {
                    "standard" -> ServerPlace.STANDARD
                    "other" -> ServerPlace.OTHER
                    "leftover" -> ServerPlace.LEFTOVER
                    else -> null
                }
                val runtime = fields["runtime"]
                val state = fields["state"]
                val name = fields["name"]
                val peer = fields["peer"]?.toIntOrNull()
                val web = fields["web"]?.toIntOrNull()
                if (place == null || runtime.isNullOrBlank() || state.isNullOrBlank() || name.isNullOrBlank() ||
                    peer !in 1..65535 || web !in 1..65535
                ) {
                    invalid = true
                    break
                }
                servers += FoundServer(
                    place = place,
                    runtime = runtime,
                    state = state,
                    version = fields["version"]?.takeIf { it.isNotBlank() && it != "?" },
                    peerPort = peer,
                    webPort = web,
                    name = name,
                )
            }
            "busy" -> {
                val fields = parts.drop(2).mapNotNull { part ->
                    part.indexOf('=').takeIf { it > 0 }?.let { part.substring(0, it) to part.substring(it + 1) }
                }.toMap()
                val port = fields["port"]?.toIntOrNull()
                val proto = fields["proto"]
                val holder = fields["by"]
                if (port !in 1..65535 || proto.isNullOrBlank() || holder.isNullOrBlank()) {
                    invalid = true
                    break
                }
                busy += BusyPort(proto, port, holder)
            }
            else -> {
                invalid = true
                break
            }
        }
    }
    return if (finished && !invalid) ServerInventory(servers, busy) else null
}

/** 2.5 and later speak the 2.5 protocol; 2.1 and older, or a binary without a version label, do not. */
internal fun isLine25(version: String?): Boolean {
    val parts = version?.split('.') ?: return false
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return major > 2 || (major == 2 && minor >= 5)
}

/**
 * The «Удалить» action is offered for the standard installation only when no foreign Shod server is running.
 * Removing the standard place runs the stock cleanup, so it is withheld while a server this app did not install runs.
 */
internal fun removableServers(inventory: ServerInventory): List<FoundServer> {
    val foreign = inventory.servers.any { it.place == ServerPlace.OTHER }
    return inventory.servers.filter {
        it.installed && it.place == ServerPlace.STANDARD && !foreign
    }
}

/** What the removal list says beside the servers: why the standard one is withheld, what else its removal deletes. */
internal fun removalNotes(inventory: ServerInventory): List<String> {
    val notes = mutableListOf<String>()
    val standard = inventory.servers.any { it.place == ServerPlace.STANDARD && it.installed }
    val foreign = inventory.servers.filter { it.place == ServerPlace.OTHER }
    if (standard && foreign.isNotEmpty()) {
        notes += "Сервер на стандартном месте не удалить, пока работает ${foreign.joinToString { it.name }}: удаление " +
            "остановило бы и его"
    }
    val leftovers = inventory.servers.filter { it.place == ServerPlace.LEFTOVER }
    if (standard && foreign.isEmpty() && leftovers.isNotEmpty()) {
        notes += "Вместе с сервером на стандартном месте будет удалено: ${leftovers.joinToString { it.name }}"
    }
    return notes
}

internal fun FoundServer.peerPortOrDefault(): Int =
    peerPort ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT
