package com.shod.client.settings.model

import com.shod.client.ShodConstants
import com.shod.client.StoredSecretState

internal data class DeploySettingsSnapshot(
    val profile: Int = -1,
    val ip: String = "",
    val serverName: String = "",
    val sshLogin: String = "",
    val sshPassword: String = "",
    val webLogin: String = "",
    val webPassword: String = "",
    val mainPassword: String = "",
    val sshPort: String = ShodConstants.Network.DEFAULT_SSH_PORT.toString(),
    val manualPortsEnabled: Boolean = false,
    val serverPeerPort: Int = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
    val serverWebPort: Int = ShodConstants.Network.DEFAULT_SERVER_WEB_PORT,
    val sshKeysMode: Boolean = false,
    val dockerInstall: Boolean = false,
    val secretPathEnabled: Boolean = true,
    val secretPathUuid: String = "",
    val serverConfigured: Boolean = false,
    val lastInspectionAtMs: Long = 0L,
    val lastInspectionOk: Boolean = false,
    val lastInspectionArch: String = "",
    val lastInspectionServiceStatus: String = "",
    val lastInspectionVersion: String = "",
    val lastInspectionMessage: String = "",
    val lastPanelAvailable: Boolean = false,
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val sshCertificate: String = "",
) {
    val isLoaded: Boolean
        get() = profile >= 0
}

/** A shod:// link materialized into profile fields on import. */
internal data class ImportedConnectionSettings(
    val host: String,
    val port: Int,
    val password: String,
    val hashes: List<String> = emptyList(),
    val vkAccessToken: String = "",
    val power: Int? = null,
    val name: String? = null,
)

internal data class TunnelAuthSnapshot(
    val profile: Int = -1,
    val connectionPassword: String = "",
    val connectionPasswordState: StoredSecretState = StoredSecretState.Missing,
    val shodLink: String = "",
    val shodLinkMode: Boolean = false,
) {
    val isLoaded: Boolean
        get() = profile >= 0
}

/** Snapshot of the connection settings of a concrete profile slot (not necessarily active). */
internal data class ProfileConnectionSnapshot(
    val revision: Long = 0,
    val peer: String = "",
    val serverPeerPort: Int = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
    val connectionPassword: String = "",
    val connectionPasswordState: StoredSecretState = StoredSecretState.Missing,
    val workersPerHash: Int = 18,
    val extraWorkers: Boolean = false,
    val shodLink: String = "",
    val shodLinkMode: Boolean = false,
    val subscriptionUrl: String = "",
    val subscriptionTitle: String = "",
    val subscriptionPower: Int = 0,
)

internal data class ProfileSubscriptionInfo(
    val url: String,
    val title: String,
    val subUuid: String,
    val nodeName: String,
    val powerLimit: Int,
)

/** A profile slot that has a server configured on the deploy screen. */
internal data class DeployServerInfo(
    val profile: Int,
    val name: String,
    val ip: String,
    val configured: Boolean = false,
    val installedVersion: String = "",
    val serviceStatus: String = "",
    val panelAvailable: Boolean = false,
)

internal data class SubscriptionMeta(
    val url: String = "",
    val title: String = "",
    val autoUpdateMinutes: Int = 0,
    val lastUpdatedAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val allowHttp: Boolean = false,
) {
    fun needsUpdate(now: Long): Boolean {
        if (autoUpdateMinutes <= 0 || url.isBlank()) return false
        val last = maxOf(lastUpdatedAt, lastAttemptAt)
        return last == 0L || now - last >= autoUpdateMinutes * 60_000L
    }
}

internal fun defaultProfileName(index: Int): String = "Профиль ${index + 1}"
