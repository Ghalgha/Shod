package com.shod.client.ui.deploy

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.shod.client.BuildConfig
import com.shod.client.settings.model.DeploySettingsSnapshot
import com.shod.client.SettingsStore
import com.shod.client.TunnelService
import com.shod.client.ShodConstants
import com.shod.client.deploy.execution.DeployActionMode
import com.shod.client.deploy.plan.PlanAction
import com.shod.client.deploy.plan.PlanKind
import com.shod.client.deploy.plan.ServerPorts
import com.shod.client.deploy.plan.planDeploy
import com.shod.client.deploy.inventory.ServerPlace
import com.shod.client.deploy.inventory.FoundServer
import com.shod.client.deploy.inventory.removableServers
import com.shod.client.deploy.inventory.removalNotes
import com.shod.client.deploy.inventory.peerPortOrDefault
import com.shod.client.platform.android.deploy.AndroidDeployCoordinator
import com.shod.client.platform.android.deploy.DeployDiagnostics
import com.shod.client.platform.android.deploy.ssh.SshHostKeyStore
import com.shod.client.ui.screens.deploy.DeployAction
import com.shod.client.ui.screens.deploy.DeployCheckState
import com.shod.client.ui.screens.deploy.DeployScreen
import com.shod.client.ui.screens.deploy.DeployUiState
import com.shod.client.ui.screens.deploy.hasWebPanelCredentials
import com.shod.client.ui.screens.deploy.isDeployMainPasswordValid
import com.shod.client.ui.screens.deploy.resolveServerPorts
import com.shod.client.ui.screens.deploy.splitPastedHostPort
import com.shod.client.ui.screens.deploy.describeServer
import com.shod.client.ui.dialogs.DeployAuthorizationDialog
import com.shod.client.ui.dialogs.DockerInstallInfoDialog
import com.shod.client.ui.dialogs.UninstallConfirmDialog
import com.shod.client.ui.dialogs.ServerCheckDialog
import com.shod.client.ui.dialogs.CheckChoice
import com.shod.client.ui.dialogs.CheckChoiceStyle
import com.shod.client.ui.dialogs.WebPanelInfoDialog
import com.shod.client.ui.dialogs.ReinstallConfirmDialog
import com.shod.client.ui.dialogs.HostKeyChangedDialog
import com.shod.client.ui.dialogs.SshKeysDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Compose owner for deploy form state and user-triggered deployment operations.
 * Network and SSH work is delegated to AndroidDeployCoordinator.
 */
@Composable
internal fun DeployController(
    settingsStore: SettingsStore,
    savedSettings: DeploySettingsSnapshot,
    checkState: DeployCheckState = remember(savedSettings.profile) { DeployCheckState() },
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    SideEffect { DeployDiagnostics.initialize(context) }

    var host by remember(savedSettings.profile) { mutableStateOf(savedSettings.ip) }
    var login by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshLogin) }
    var sshPassword by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshPassword) }
    var sshPort by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshPort.ifBlank { "22" }) }
    var peerPort by remember(savedSettings.profile) { mutableStateOf(savedSettings.serverPeerPort.toString()) }
    var webPort by remember(savedSettings.profile) { mutableStateOf(savedSettings.serverWebPort.toString()) }
    var manualPorts by remember(savedSettings.profile) { mutableStateOf(savedSettings.manualPortsEnabled) }
    var keysMode by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshKeysMode) }
    var docker by remember(savedSettings.profile) { mutableStateOf(savedSettings.dockerInstall) }
    var progress by remember { mutableFloatStateOf(0f) }
    var step by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }
    var showDockerInfo by remember { mutableStateOf(false) }
    var showPanelInfo by remember { mutableStateOf(false) }
    var mainPassword by remember(savedSettings.profile) { mutableStateOf(savedSettings.mainPassword) }
    var webLogin by remember(savedSettings.profile) { mutableStateOf(savedSettings.webLogin) }
    var webPassword by remember(savedSettings.profile) { mutableStateOf(savedSettings.webPassword) }
    var privateKey by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshPrivateKey) }
    var keyPassphrase by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshKeyPassphrase) }
    var certificate by remember(savedSettings.profile) { mutableStateOf(savedSettings.sshCertificate) }
    var pendingAction by remember { mutableStateOf<PlanAction?>(null) }
    var removable by remember(savedSettings.profile) { mutableStateOf<List<FoundServer>?>(null) }
    var removeNotes by remember(savedSettings.profile) { mutableStateOf<List<String>>(emptyList()) }
    var pendingRemoval by remember(savedSettings.profile) { mutableStateOf<FoundServer?>(null) }
    var pendingHostKeyChange by remember { mutableStateOf<SshHostKeyStore.KeyChange?>(null) }
    var retryInspection by remember { mutableStateOf<((suspend (com.shod.client.deploy.inventory.ServerInventory) -> Unit))?>(null) }

    LaunchedEffect(savedSettings.profile, savedSettings.ip) {
        if (host != savedSettings.ip && host.isBlank()) host = savedSettings.ip
    }

    LaunchedEffect(savedSettings.profile, savedSettings.secretPathUuid) {
        if (savedSettings.secretPathUuid.isBlank()) {
            settingsStore.regenerateDeploySecretPathUuid()
        }
    }

    val state = DeployUiState(
        host = host,
        sshLogin = login,
        sshPassword = sshPassword,
        manualPorts = manualPorts,
        sshPort = sshPort,
        peerPort = peerPort,
        webPort = webPort,
        mainPasswordConfigured = isDeployMainPasswordValid(mainPassword),
        webPanelConfigured = hasWebPanelCredentials(webLogin, webPassword),
        sshKeysMode = keysMode,
        dockerInstall = docker,
        secretPathEnabled = savedSettings.secretPathEnabled,
        secretPathUuid = savedSettings.secretPathUuid,
        sshKeysFilled = if (privateKey.isNotBlank()) 1 else 0,
        isDeploying = busy,
        progress = progress,
        currentStep = step,
        showValidation = showValidation,
    )

    fun ports() = resolveServerPorts(manualPorts, sshPort, peerPort, webPort)
    fun report(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
    fun coordinator() = AndroidDeployCoordinator(
        context = context,
        onProgress = { value, message -> progress = value; step = message },
        onInfo = { step = it },
        onSuccess = { step = it },
        onError = { message ->
            step = message
            DeployDiagnostics.recordError(message)
        },
    )
    fun credentialsReady(): Boolean {
        showValidation = true
        if (host.isBlank() || login.isBlank() || (keysMode && privateKey.isBlank()) ||
            (!keysMode && sshPassword.isBlank()) || !state.portsValid) {
            report("Проверьте адрес, SSH-доступ и порты")
            return false
        }
        return true
    }

    fun runInspection(onInventory: suspend (com.shod.client.deploy.inventory.ServerInventory) -> Unit) {
        if (busy || !credentialsReady()) return
        retryInspection = onInventory
        busy = true
        progress = 0f
        scope.launch {
            try {
                val result = coordinator().inspectServer(
                    host = host.trim(),
                    user = login.trim(),
                    password = if (keysMode) "" else sshPassword,
                    sshPort = ports().ssh,
                    privateKey = if (keysMode) privateKey else "",
                    keyPassphrase = if (keysMode) keyPassphrase else "",
                    certificate = if (keysMode) certificate else "",
                )
                onInventory(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val changedKey = SshHostKeyStore.takeKeyChange(host.trim(), ports().ssh)
                if (changedKey != null) pendingHostKeyChange = changedKey
                else report(error.message ?: "Не удалось проверить VPS")
            } finally {
                busy = false
            }
        }
    }

    fun executeUninstall(server: FoundServer) {
        if (busy) return
        busy = true
        progress = 0f
        DeployDiagnostics.begin()
        scope.launch {
            try {
                context.startForegroundService(Intent(context, TunnelService::class.java).apply { action = "DEPLOY_START" })
                val result = withTimeout(30 * 60 * 1000L) {
                    coordinator().uninstall(
                        host = host.trim(),
                        user = login.trim(),
                        password = if (keysMode) "" else sshPassword,
                        sshPort = ports().ssh,
                        peerPort = server.peerPortOrDefault(),
                        privateKey = if (keysMode) privateKey else "",
                        keyPassphrase = if (keysMode) keyPassphrase else "",
                        certificate = if (keysMode) certificate else "",
                    )
                }
                report(result.failureMessage ?: if (result.successful) "Сервер удалён" else "Не удалось удалить сервер")
            } catch (timeout: TimeoutCancellationException) {
                DeployDiagnostics.forceReset("Автоматический сброс: удаление сервера превысило 30 минут")
                progress = 0f
                step = "Удаление принудительно остановлено по таймауту"
                report(step)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DeployDiagnostics.recordError("Необработанная ошибка удаления: ${error.message ?: error.javaClass.simpleName}", error)
                AndroidDeployCoordinator.cancelActiveOperation()
                report(error.message ?: "Ошибка удаления")
            } finally {
                runCatching {
                    context.startService(Intent(context, TunnelService::class.java).apply { action = "DEPLOY_STOP" })
                }
                busy = false
            }
        }
    }

    fun executeDeploy(action: PlanAction) {
        if (busy) return
        busy = true
        progress = 0f
        DeployDiagnostics.begin()
        val effective = ports()
        val deploymentDraft = savedSettings.copy(
            ip = host,
            sshLogin = login,
            sshPassword = sshPassword,
            sshPort = effective.ssh.toString(),
            serverPeerPort = action.peerPort,
            serverWebPort = action.webPort,
            mainPassword = mainPassword,
            webLogin = webLogin,
            webPassword = webPassword,
            dockerInstall = docker,
        )
        scope.launch {
            try {
                settingsStore.saveDeployProfileDraft(deploymentDraft, restoreConnection = true)
                context.startForegroundService(Intent(context, TunnelService::class.java).apply { action = "DEPLOY_START" })
                val mode = when (action.kind) {
                    PlanKind.INSTALL -> DeployActionMode.Install
                    PlanKind.UPDATE -> DeployActionMode.Update
                    PlanKind.REPLACE -> DeployActionMode.Reinstall
                }
                val result = withTimeout(30 * 60 * 1000L) { coordinator().deploy(
                    host = host.trim(),
                    user = login.trim(),
                    password = if (keysMode) "" else sshPassword,
                    sshPort = effective.ssh,
                    mainPassword = mainPassword,
                    webLogin = webLogin,
                    webPassword = webPassword,
                    peerPort = action.peerPort,
                    webPort = action.webPort,
                    privateKey = if (keysMode) privateKey else "",
                    keyPassphrase = if (keysMode) keyPassphrase else "",
                    certificate = if (keysMode) certificate else "",
                    installInDocker = docker,
                    secretPath = if (savedSettings.secretPathEnabled) savedSettings.secretPathUuid else "",
                    mode = mode,
                ) }
                if (result.successful) {
                    settingsStore.saveDeployedServer(savedSettings.profile, host.trim(), action.peerPort, action.webPort)
                    settingsStore.savePorts(action.peerPort, action.webPort, effective.ssh.toString())
                    peerPort = action.peerPort.toString()
                    webPort = action.webPort.toString()
                    settingsStore.saveConnectionPassword(mainPassword, settingsStore.linkedConnectionProfile(savedSettings.profile))
                    report(when (mode) {
                        DeployActionMode.Install -> "Сервер установлен"
                        DeployActionMode.Update -> "Сервер обновлён"
                        DeployActionMode.Reinstall -> "Сервер переустановлен"
                    })
                } else {
                    report(result.failureMessage ?: "Операция завершилась ошибкой")
                }
            } catch (timeout: TimeoutCancellationException) {
                DeployDiagnostics.forceReset("Автоматический сброс: операция установки превысила 30 минут")
                progress = 0f
                step = "Установка принудительно остановлена по таймауту"
                report(step)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DeployDiagnostics.recordError("Необработанная ошибка установки: ${error.message ?: error.javaClass.simpleName}", error)
                AndroidDeployCoordinator.cancelActiveOperation()
                report(error.message ?: "Ошибка операции")
            } finally {
                runCatching {
                    context.startService(Intent(context, TunnelService::class.java).apply { action = "DEPLOY_STOP" })
                }
                busy = false
            }
        }
    }

    DeployScreen(
        onBack = onBack,
        state = state,
        snackbarHostState = snackbar,
        onAction = { action ->
            when (action) {
                is DeployAction.HostChanged -> {
                    val value = action.value.filterNot(Char::isWhitespace)
                    val split = splitPastedHostPort(host, value)
                    host = split?.first ?: value
                    if (split != null) sshPort = split.second
                    scope.launch { settingsStore.saveDeploy(host) }
                }
                is DeployAction.LoginChanged -> {
                    login = action.value.filterNot(Char::isWhitespace)
                    scope.launch { settingsStore.saveDeploySecrets(mainPassword, login, sshPassword, webLogin, webPassword) }
                }
                is DeployAction.PasswordChanged -> {
                    sshPassword = action.value
                    scope.launch { settingsStore.saveDeploySecrets(mainPassword, login, sshPassword, webLogin, webPassword) }
                }
                is DeployAction.SshPortChanged -> sshPort = action.value.filter(Char::isDigit).take(5)
                is DeployAction.PeerPortChanged -> peerPort = action.value.filter(Char::isDigit).take(5)
                is DeployAction.WebPortChanged -> webPort = action.value.filter(Char::isDigit).take(5)
                is DeployAction.ManualPortsChanged -> {
                    manualPorts = action.enabled
                    scope.launch { settingsStore.saveManualPortsEnabled(action.enabled) }
                    if (!action.enabled) {
                        peerPort = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT.toString()
                        webPort = ShodConstants.Network.DEFAULT_SERVER_WEB_PORT.toString()
                    }
                }
                is DeployAction.SshKeysModeChanged -> {
                    keysMode = action.enabled
                    scope.launch { settingsStore.saveSshKeysMode(action.enabled) }
                }
                is DeployAction.DockerInstallChanged -> {
                    docker = action.enabled
                    scope.launch { settingsStore.saveDockerInstall(action.enabled) }
                }
                DeployAction.RegenerateSecretPath -> scope.launch {
                    settingsStore.regenerateDeploySecretPathUuid()
                    report("Секретный путь перегенерирован")
                }
                DeployAction.EditSshKeys -> showKeys = true
                DeployAction.EditAuthorization -> showAuth = true
                DeployAction.DockerInfo -> showDockerInfo = true
                DeployAction.PanelInfo -> showPanelInfo = true
                DeployAction.OpenControlPanel -> {
                    val port = ports().web
                    val path = savedSettings.secretPathUuid.trim('/').takeIf { savedSettings.secretPathEnabled && it.isNotBlank() }?.let { "/$it" } ?: ""
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://$host:$port$path".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { report("Не удалось открыть панель управления") }
                }
                DeployAction.Install -> {
                    showValidation = true
                    if (!state.canInstall) {
                        report(when {
                            !state.mainPasswordConfigured -> "Настройте корректный пароль туннеля в «Авторизации»"
                            !state.webPanelConfigured -> "Укажите логин и пароль web-панели"
                            else -> "Проверьте SSH-доступ, адрес и порты"
                        })
                    } else runInspection { inventory ->
                        val plan = planDeploy(
                            inventory = inventory,
                            manualPorts = if (manualPorts) ServerPorts(ports().peer, ports().web) else null,
                            profilePeerPort = ports().peer,
                            dockerInstall = docker,
                            appVersion = BuildConfig.VERSION_NAME.removePrefix("v"),
                        )
                        checkState.plan.value = plan
                        if (plan.actions.size == 1 && !plan.actions.single().destructive) {
                            executeDeploy(plan.actions.single())
                        } else if (plan.actions.isEmpty()) {
                            report(plan.notes.joinToString("\n").ifBlank { "Не найдено безопасного действия для этого сервера" })
                        }
                    }
                }
                DeployAction.Uninstall -> {
                    if (state.canUninstall) {
                        runInspection { inventory ->
                            removable = removableServers(inventory)
                            removeNotes = removalNotes(inventory)
                        }
                    } else {
                        credentialsReady()
                    }
                }
            }
        },
    )

    if (showAuth) {
        DeployAuthorizationDialog(
            mainPassword = mainPassword,
            webLogin = webLogin,
            webPassword = webPassword,
            onMainPasswordChange = { mainPassword = it },
            onWebLoginChange = { webLogin = it },
            onWebPasswordChange = { webPassword = it },
            onSave = {
                showAuth = false
                scope.launch { settingsStore.saveDeploySecrets(mainPassword, login, sshPassword, webLogin, webPassword) }
            },
            onDismiss = { showAuth = false },
        )
    }

    if (showKeys) {
        SshKeysDialog(
            privateKey = privateKey,
            passphrase = keyPassphrase,
            certificate = certificate,
            onPrivateKeyChange = { privateKey = it },
            onPassphraseChange = { keyPassphrase = it },
            onCertificateChange = { certificate = it },
            onSave = {
                showKeys = false
                scope.launch { settingsStore.saveSshKeys(privateKey, keyPassphrase, certificate) }
            },
            onDismiss = { showKeys = false },
        )
    }

    if (showDockerInfo) {
        DockerInstallInfoDialog(onDismiss = { showDockerInfo = false })
    }

    removable?.let { servers ->
        ServerCheckDialog(
            title = "Удаление с сервера",
            servers = emptyList(),
            emptyText = if (servers.isEmpty() && removeNotes.isEmpty()) {
                "Серверов, установленных этим приложением, для безопасного удаления не найдено"
            } else {
                ""
            },
            notes = removeNotes,
            choices = servers.map { server ->
                val (name, detail) = describeServer(server, ports().peer)
                CheckChoice("Удалить: $name", detail, CheckChoiceStyle.DESTRUCTIVE)
            },
            onChoose = { index ->
                pendingRemoval = servers[index]
                removable = null
            },
            onDismiss = { removable = null },
        )
    }

    pendingRemoval?.let { server ->
        val (name, _) = describeServer(server, ports().peer)
        UninstallConfirmDialog(
            title = "Удаление: $name",
            text = "Будут удалены бинарник, служба или контейнер Shod, конфигурация и только помеченные правила firewall/NAT. База пользователей остаётся в /etc/shod. Это действие необратимо.",
            onDismiss = { pendingRemoval = null },
            onConfirm = {
                pendingRemoval = null
                executeUninstall(server)
            },
        )
    }

    pendingHostKeyChange?.let { change ->
        HostKeyChangedDialog(
            change = change,
            onDismiss = { pendingHostKeyChange = null },
            onTrust = {
                SshHostKeyStore.trust(context, change)
                pendingHostKeyChange = null
                retryInspection?.let { retry -> runInspection(retry) }
            },
        )
    }

    if (showPanelInfo) {
        WebPanelInfoDialog(onDismiss = { showPanelInfo = false })
    }

    checkState.plan.value?.let { plan ->
        if (plan.actions.size > 1 || plan.actions.singleOrNull()?.destructive == true) {
            ServerCheckDialog(
                title = "Результат проверки VPS",
                servers = emptyList(),
                emptyText = "",
                notes = plan.notes,
                choices = plan.actions.map { action ->
                    CheckChoice(
                        title = action.title,
                        detail = action.detail,
                        style = if (action.destructive) CheckChoiceStyle.DESTRUCTIVE else CheckChoiceStyle.OTHER,
                    )
                },
                onChoose = { index ->
                    val action = plan.actions[index]
                    checkState.plan.value = null
                    if (action.destructive) pendingAction = action else executeDeploy(action)
                },
                onDismiss = { checkState.plan.value = null },
            )
        }
    }

    pendingAction?.let { action ->
        ReinstallConfirmDialog(
            detail = action.detail,
            onDismiss = { pendingAction = null },
            onConfirm = { pendingAction = null; executeDeploy(action) },
        )
    }
}
