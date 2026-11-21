package com.shod.client.ui.screens.deploy

import androidx.compose.runtime.Immutable
import com.shod.client.ShodConstants
import com.shod.client.ui.utils.isValidTunnelPassword

@Immutable
internal data class DeployUiState(
    val host: String = "",
    val sshLogin: String = "",
    val sshPassword: String = "",
    val manualPorts: Boolean = false,
    val sshPort: String = ShodConstants.Network.DEFAULT_SSH_PORT.toString(),
    val peerPort: String = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT.toString(),
    val webPort: String = ShodConstants.Network.DEFAULT_SERVER_WEB_PORT.toString(),
    val mainPasswordConfigured: Boolean = false,
    val webPanelConfigured: Boolean = false,
    val sshKeysMode: Boolean = false,
    val dockerInstall: Boolean = false,
    val secretPathEnabled: Boolean = false,
    val secretPathUuid: String = "",
    val sshKeysFilled: Int = 0,
    val isDeploying: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val showValidation: Boolean = false,
) {
    val portsValid: Boolean
        get() = isValidPort(sshPort) && (!manualPorts || listOf(peerPort, webPort).all(::isValidPort))

    val authorizationConfigured: Boolean
        get() = webPanelConfigured && mainPasswordConfigured

    val sshAuthReady: Boolean
        get() = sshLogin.isNotBlank() && if (sshKeysMode) sshKeysFilled == 1 else sshPassword.isNotBlank()

    val canInstall: Boolean
        get() = host.isNotBlank() && !hostHasPort(host) && sshAuthReady && authorizationConfigured && portsValid

    val canUninstall: Boolean
        get() = host.isNotBlank() && !hostHasPort(host) && sshAuthReady && portsValid

    val panelDisplayUrl: String
        get() {
            val h = host.ifBlank { "IP" }
            // the panel of the server this profile points at (a deploy stores the server's ports on success)
            val p = resolveServerPorts(manualPorts, sshPort, peerPort, webPort).web
            val u = if (secretPathUuid.isNotBlank()) {
                if (secretPathUuid.startsWith("/")) secretPathUuid else "/$secretPathUuid"
            } else {
                "/uuid"
            }
            return "https://$h:$p$u"
        }
}

internal sealed interface DeployAction {
    data class HostChanged(val value: String) : DeployAction
    data class LoginChanged(val value: String) : DeployAction
    data class PasswordChanged(val value: String) : DeployAction
    data class ManualPortsChanged(val enabled: Boolean) : DeployAction
    data class SshKeysModeChanged(val enabled: Boolean) : DeployAction
    data class DockerInstallChanged(val enabled: Boolean) : DeployAction
    data object RegenerateSecretPath : DeployAction
    data object EditSshKeys : DeployAction
    data object DockerInfo : DeployAction
    data class SshPortChanged(val value: String) : DeployAction
    data class PeerPortChanged(val value: String) : DeployAction
    data class WebPortChanged(val value: String) : DeployAction
    data object EditAuthorization : DeployAction
    data object Install : DeployAction
    data object Uninstall : DeployAction
    data object OpenControlPanel : DeployAction
    data object PanelInfo : DeployAction
}

internal fun passwordToSynchronizeAfterDeploy(deploySucceeded: Boolean, mainPassword: String): String? =
    mainPassword.takeIf { deploySucceeded && it.isNotBlank() }

internal fun isDeployMainPasswordValid(password: String): Boolean = isValidTunnelPassword(password)

internal fun isValidPort(value: String): Boolean = value.toIntOrNull() in 1..65535

// "1.2.3.4:2222" or "vps.example:2222" — an address with an SSH port after it (one ':' only: an IPv6 address has several)
private val HOST_WITH_PORT = Regex("""^([^:\[\]]+):(\d{1,5})$""")

/** The address field holds a port too ("1.2.3.4:2222"): the port belongs in «Порт SSH». */
internal fun hostHasPort(host: String): Boolean = HOST_WITH_PORT.matches(host)

/**
 * A pasted "host:port" split into the address and the SSH port; null for anything else, and for typing, which adds one
 * character at a time — splitting there would move the next typed digit into the address.
 */
internal fun splitPastedHostPort(previous: String, value: String): Pair<String, String>? {
    if (value.length - previous.length < 2) return null
    val match = HOST_WITH_PORT.matchEntire(value) ?: return null
    val (host, port) = match.destructured
    return if (isValidPort(port)) host to port else null
}

@Immutable
internal data class EffectiveServerPorts(
    val ssh: Int,
    val peer: Int,
    val web: Int,
)

internal fun resolveServerPorts(
    manualPorts: Boolean,
    sshPort: String,
    peerPort: String,
    webPort: String,
): EffectiveServerPorts {
    // the SSH port is the form's whatever «Ручные порты» says: that switch is about the shod server's ports. Tied to it,
    // a VPS with SSH on another port was always dialled on 22 ("соединение отклонено") with no visible field to change
    val ssh = sshPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: ShodConstants.Network.DEFAULT_SSH_PORT
    if (!manualPorts) {
        return EffectiveServerPorts(
            ssh = ssh,
            peer = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
            web = ShodConstants.Network.DEFAULT_SERVER_WEB_PORT,
        )
    }
    return EffectiveServerPorts(
        ssh = ssh,
        peer = peerPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
        web = webPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT,
    )
}

internal fun hasWebPanelCredentials(login: String, password: String): Boolean =
    login.isNotBlank() && password.isNotBlank()
