package com.shod.client

import com.shod.client.integration.vk.hash.codec.VkHashValidationCodec
import com.shod.client.links.parseShodLink
import com.shod.client.links.peerAddress
import kotlinx.coroutines.flow.first

data class ConnectionSource(
    val peer: String,
    val password: String,
    val hashes: String,
    val hashesFromLink: Boolean,
    val vkAccessToken: String,
    val tokenFromLink: Boolean,
)

/** Owner hashes from a participant link, minus removed entries, followed by participant hashes. */
internal fun participantHashes(
    linkHashes: List<String>,
    removed: Set<String>,
    ownHashes: String,
): String = (linkHashes.filterNot { it in removed } + ownHashes.split(Regex("[,\\s\\n]+")))
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .joinToString(",")

suspend fun resolveConnectionSource(
    store: SettingsStore,
    updateLogMask: Boolean = true,
): ConnectionSource? {
    val invalidHashes = VkHashValidationCodec.decode(store.vkHashCheckResults.first())
    fun activeHashes(raw: String): String {
        val rawList = raw.split(Regex("[,\\s\\n]+"))
            .map(String::trim)
            .filter { it.length >= 16 }
            .distinct()
        val filtered = VkHashValidationCodec.active(rawList, invalidHashes)
        return (if (filtered.isNotEmpty()) filtered else rawList).joinToString(",")
    }

    if (store.shodLinkMode.first()) {
        val link = parseShodLink(store.shodLink.first()) ?: return null
        if (updateLogMask) TunnelManager.ownerHashes = link.hashes
        val removed = store.linkHashesRemoved.first()
        val tokenFromLink = link.vkAccessToken.isNotEmpty()
        return ConnectionSource(
            peer = link.peerAddress(),
            password = link.password,
            hashes = if (tokenFromLink) "" else activeHashes(
                participantHashes(link.hashes, removed, store.vkHashes.first()),
            ),
            hashesFromLink = link.hashes.isNotEmpty(),
            vkAccessToken = link.vkAccessToken,
            tokenFromLink = tokenFromLink,
        )
    }

    if (updateLogMask) TunnelManager.ownerHashes = emptyList()
    val basePeer = store.peer.first()
    val password = store.connectionPasswordState.first().value
    if (basePeer.isBlank()) return null
    val serverPeerPort = store.serverPeerPort.first()
        .takeIf { it in 1..65535 }
        ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT
    val peer = if (basePeer.contains(':')) basePeer else "$basePeer:$serverPeerPort"
    return ConnectionSource(
        peer = peer,
        password = password,
        hashes = activeHashes(store.vkHashes.first()),
        hashesFromLink = false,
        vkAccessToken = "",
        tokenFromLink = false,
    )
}
