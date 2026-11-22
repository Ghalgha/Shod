package com.shod.client.ui.controllers

import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shod.client.vpn.power.BatterySaver
import com.shod.client.ConnectionSource
import com.shod.client.connection.policy.ConnectionStartBlocker
import com.shod.client.connection.policy.ConnectionStartPolicy
import com.shod.client.ShodConstants
import com.shod.client.R
import com.shod.client.SettingsStore
import com.shod.client.settings.secret.StoredSecretState
import com.shod.client.settings.model.TunnelAuthSnapshot
import com.shod.client.TunnelManager
import com.shod.client.TunnelService
import com.shod.client.platform.android.services.TunnelServiceIntentFactory
import com.shod.client.platform.android.services.TunnelStartOptions
import com.shod.client.vpn.session.newTunnelSessionId
import com.shod.client.resolveConnectionSource
import com.shod.client.platform.android.ui.showRaisedToast
import com.shod.client.links.parseShodLink
import com.shod.client.network.wifi.policy.tunnelPowerIsOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

import com.shod.client.integration.vk.hash.codec.VkHashValidationCodec
@Composable
internal fun ConnectionController(
    settingsStore: SettingsStore,
    tunnelAuthSettings: TunnelAuthSnapshot,
    onInvalidConfiguration: () -> Unit,
    onConnectionActionReady: ((() -> Unit) -> Unit) = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val connectionPassword = tunnelAuthSettings.connectionPassword

    val shodLinkMode = tunnelAuthSettings.shodLinkMode
    val shodLink = tunnelAuthSettings.shodLink
    val peer by settingsStore.peer.collectAsStateWithLifecycle(initialValue = "")
    val vkHashes by settingsStore.vkHashes.collectAsStateWithLifecycle(initialValue = "")
    val vkHashCheckResultsJson by settingsStore.vkHashCheckResults.collectAsStateWithLifecycle(initialValue = "{}")
    val workersPerHash by settingsStore.workersPerHash.collectAsStateWithLifecycle(initialValue = 18)
    val sni by settingsStore.sni.collectAsStateWithLifecycle(initialValue = "")
    val turnTransport by settingsStore.turnTransport.collectAsStateWithLifecycle(initialValue = ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT)
    val obfsMode by settingsStore.obfsMode.collectAsStateWithLifecycle(initialValue = ShodConstants.Tunnel.DEFAULT_OBFS_MODE)
    val srtpMode by settingsStore.srtpMode.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedSrtpMode ?: ShodConstants.SrtpMode.DEFAULT)
    val customTurnIps by settingsStore.customTurnIps.collectAsStateWithLifecycle(initialValue = "")
    val manualPortsEnabled by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val serverPeerPort by settingsStore.serverPeerPort.collectAsStateWithLifecycle(
        initialValue = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
    )
    val vkAuthMode by settingsStore.vkAuthMode.collectAsStateWithLifecycle(initialValue = ShodConstants.VkAuth.MODE_CALLS)
    val captchaMode by settingsStore.captchaMode.collectAsStateWithLifecycle(initialValue = "auto")
    val captchaSolveMethod by settingsStore.captchaSolveMethod.collectAsStateWithLifecycle(initialValue = "auto")
    val activeFingerprint by settingsStore.selectedFingerprint.collectAsStateWithLifecycle(initialValue = ShodConstants.Tunnel.DEFAULT_FINGERPRINT)
    val activeClientIds by settingsStore.activeClientIds.collectAsStateWithLifecycle(initialValue = ShodConstants.Tunnel.DEFAULT_CLIENT_IDS)
    val savedHashMode by settingsStore.vkHashMode.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedVkHashMode)
    val savedVkAccessToken by settingsStore.vkAccessToken.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedVkAccessToken)

    var pendingStartAfterVpnPermission by remember { mutableStateOf(false) }

    val parsedLink = remember(shodLink) { parseShodLink(shodLink) }
    val linkHashesRemoved by settingsStore.linkHashesRemoved.collectAsStateWithLifecycle(initialValue = emptySet())
    // the owner's hashes still in use: the ones the participant took out do not count
    val linkHashes = parsedLink?.hashes.orEmpty().filterNot { it in linkHashesRemoved }
    val linkTokenActive = shodLinkMode && parsedLink?.vkAccessToken?.isNotEmpty() == true
    val rawManualHashes = remember(vkHashes) {
        vkHashes.split(Regex("[,\\s\\n]+"))
            .map(String::trim)
            .filter { it.length >= 16 }
            .distinct()
    }
    val activeManualHashes = remember(vkHashes, vkHashCheckResultsJson) {
        VkHashValidationCodec.active(
            rawManualHashes,
            VkHashValidationCodec.decode(vkHashCheckResultsJson),
        )
    }
    val manualHashes = if (activeManualHashes.isNotEmpty()) activeManualHashes else rawManualHashes
    val accountAutoJsMode = vkAuthMode == ShodConstants.VkAuth.MODE_AUTO_JS
    val hashSettingsLoaded = savedHashMode != null && savedVkAccessToken != null
    val autoHashMode = accountAutoJsMode || (
        savedHashMode != null && savedHashMode != ShodConstants.VkAutoHash.MODE_MANUAL
    )
    val vkTokenActive = savedVkAccessToken?.isNotBlank() == true
    val hashesReady = hashSettingsLoaded && ConnectionStartPolicy.hasReadyVkCredentials(
        linkMode = shodLinkMode,
        linkHashCount = linkHashes.size,
        linkTokenPresent = linkTokenActive,
        autoHashMode = autoHashMode,
        storedTokenPresent = vkTokenActive,
        manualHashCount = manualHashes.size,
    )
    val peerPortValid = !manualPortsEnabled || serverPeerPort in 1..65535
    val trimmedPeer = peer.trim()
    val isLocalOrUnspecifiedPeer = trimmedPeer == "0.0.0.0" || trimmedPeer == "127.0.0.1" || trimmedPeer.equals("localhost", ignoreCase = true) || trimmedPeer == "::1"
    val startBlocker = ConnectionStartPolicy.blocker(
        linkMode = shodLinkMode,
        linkValid = parsedLink != null,
        peerValid = trimmedPeer.isNotBlank() && !trimmedPeer.contains(":") && !isLocalOrUnspecifiedPeer,
        peerPortValid = peerPortValid,
        connectionPasswordSet = connectionPassword.isNotBlank(),
        connectionPasswordReadable = tunnelAuthSettings.connectionPasswordState != StoredSecretState.Unreadable,
        hashesReady = hashesReady,
    )
    val isValid = startBlocker == null

    fun startTunnelService() {
        scope.launch {
            val source = resolveConnectionSource(settingsStore) ?: run {
                context.showRaisedToast("Заполните настройки подключения", Toast.LENGTH_SHORT)
                return@launch
            }
            TunnelManager.isLoggingEnabled = settingsStore.loggingEnabled.first()
            val nextGen = System.currentTimeMillis() / 1000L
            val salt = newTunnelSessionId()
            val intent = TunnelServiceIntentFactory.create(
                context = context,
                source = source,
                options = TunnelStartOptions(
                    workersPerHash = workersPerHash,
                    sni = sni,
                    vkAuthMode = vkAuthMode,
                    captchaMode = captchaMode,
                    captchaSolveMethod = captchaSolveMethod,
                    fingerprint = activeFingerprint,
                    clientIds = activeClientIds,
                    obfsMode = obfsMode,
                    turnTransport = turnTransport,
                    srtpMode = srtpMode,
                    customTurnIps = customTurnIps,
                ),
                generationId = nextGen,
                sessionSalt = salt,
            )
            runCatching { context.startForegroundService(intent) }
                .onFailure { error ->
                    TunnelManager.updateLog(
                        "foreground_request_error",
                        "Android заблокировал запуск VPN: ${error.message ?: error.javaClass.simpleName}",
                        99,
                        true,
                    )
                    Toast.makeText(
                        context,
                        "Android заблокировал запуск VPN. Проверьте ограничения батареи приложения.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingStartAfterVpnPermission) {
            pendingStartAfterVpnPermission = false
            if (VpnService.prepare(context) == null) {
                startTunnelService()
            } else {
                context.showRaisedToast("VPN-разрешение не выдано", Toast.LENGTH_SHORT)
            }
        }
    }

    fun toggleTunnel() {
        if (TunnelManager.stopping.value) return
        if (tunnelPowerIsOn(TunnelManager.running.value, TunnelManager.starting.value, TunnelManager.autoPausedForWifi.value)) {
            pendingStartAfterVpnPermission = false
            context.startService(Intent(context, TunnelService::class.java).apply { action = "STOP" })
            return
        }
        if (!isValid) {
            val blocker = requireNotNull(startBlocker)
            TunnelManager.updateLog(
                "connection_configuration_${blocker.name}",
                "[ПОДКЛЮЧЕНИЕ] Запуск не начат: ${blocker.message}",
                99,
                true,
            )
            if (blocker == ConnectionStartBlocker.StoredSecret) {
                scope.launch {
                    val reset = settingsStore.resetUnreadableConnectionPassword()
                    if (reset) {
                        context.showRaisedToast(
                            "Сохранённый пароль сброшен. Введите его заново в разделе «Авторизация».",
                            Toast.LENGTH_LONG,
                        )
                    }
                    onInvalidConfiguration()
                }
                return
            }
            onInvalidConfiguration()
            return
        }
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            pendingStartAfterVpnPermission = true
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startTunnelService()
        }
    }

    SideEffect { onConnectionActionReady(::toggleTunnel) }
}
