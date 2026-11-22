package com.shod.client.platform.android.activity

import com.shod.client.platform.android.ui.showRaisedToast

import com.shod.client.platform.android.activity.UpdateLaunchGuard

import com.shod.client.subscriptions.SubscriptionSync

import com.shod.client.*
import com.shod.client.integration.vk.captcha.android.ManlCaptchaWebViewManager
import com.shod.client.platform.android.process.AppForegroundState
import com.shod.client.platform.android.network.wifi.WifiDiscovery
import com.shod.client.platform.android.network.wifi.WifiDiscoveryNotification
import com.shod.client.platform.android.power.shouldRequestBatteryOptimizationExemption
import com.shod.client.ui.navigation.MainScreen
import com.shod.client.ui.screens.credentials.CredentialsScreen
import com.shod.client.ui.screens.deploy.DeployCheckState
import com.shod.client.ui.theme.ShodTheme

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.shod.client.ui.dialogs.AppUpdateDialog
import com.shod.client.ui.controllers.ConnectionController
import com.shod.client.ui.background.AppBackdrop
import com.shod.client.ui.screens.HomeScreen
import com.shod.client.ui.screens.SettingsScreen
import com.shod.client.ui.screens.LogsScreen
import com.shod.client.ui.screens.RoutingScreen
import com.shod.client.ui.components.HeaderActions
import com.shod.client.ui.screens.credentials.CredentialsScreen
import com.shod.client.ShodConstants
import com.shod.client.ui.deploy.DeployController
import com.shod.client.ui.components.LocalHeaderActions
import com.shod.client.ui.design.ShodMotion
import com.shod.client.ui.design.ShodShapes
import com.shod.client.integration.vk.auth.ui.VkAuthDialog
import com.shod.client.links.parseShodLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlin.math.abs

import com.shod.client.integration.vk.auth.model.VkAuthPayload
class MainActivity : ComponentActivity() {
    private val appSettingsStore by lazy { SettingsStore(applicationContext) }
    private var renderUi by mutableStateOf(false)
    private var showVkAuthDialog by mutableStateOf(false)
    private var chooseVkAccount by mutableStateOf(false)
    private var pendingDeepLink by mutableStateOf<String?>(null)
    private var batteryPromptInFlight = false

    private val batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryPromptInFlight = false
        lifecycleScope.launch {
            appSettingsStore.markBatteryOptimizationPromptHandled()
        }
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        checkAndRequestBattery()
    }

    companion object {
        val activeActivities: Int
            get() = AppForegroundState.activeActivities
        val isForeground: Boolean
            get() = AppForegroundState.isForeground
    }

    override fun onStart() {
        super.onStart()
        AppForegroundState.onActivityStarted()
        WifiDiscovery.setForeground(true)
        WifiDiscovery.acquire(this, this)
        renderUi = true
        ManlCaptchaWebViewManager.checkAndShowPendingCaptcha(this)
    }

    override fun onStop() {
        renderUi = false
        AppForegroundState.onActivityStopped()
        WifiDiscovery.release(this)
        WifiDiscovery.setForeground(isForeground)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        WifiDiscovery.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val discardStaleUiState = UpdateLaunchGuard.consumeStaleSavedState(this)
        super.onCreate(if (discardStaleUiState) null else savedInstanceState)

        importShodLink(intent)
        if (intent?.action == WifiDiscoveryNotification.OPEN) WifiDiscovery.openHome.value = true

        TunnelManager.initObservers(this)

        enableEdgeToEdge()

        checkAndRequestNotifications()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) { SubscriptionSync.refreshDue(appSettingsStore); delay(30_000L) }
            }
        }

        setContent {
            val authContext = LocalContext.current.applicationContext
            val authScope = rememberCoroutineScope()
            val uiStateHolder = rememberSaveableStateHolder()
            val chosenTheme by appSettingsStore.themeMode.collectAsStateWithLifecycle("dark")
            if (renderUi) {
                uiStateHolder.SaveableStateProvider("main_ui_classic") {
            val settingsStore = remember { appSettingsStore }
            val themeMode by settingsStore.themeMode.collectAsStateWithLifecycle(initialValue = ShodConstants.Theme.MODE_DARK)
            val scope = rememberCoroutineScope()

            ShodTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(
                        settingsStore = settingsStore,
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            scope.launch {
                                settingsStore.saveThemeMode(mode)
                            }
                        },
                        onVkAuthRequested = { chooseVkAccount = true; showVkAuthDialog = true },
                    )
                    // A shod:// link, the classic way: confirm, then it replaces the current configuration. It stays
                    // pending until the user chooses, so the dialog survives a rotation instead of losing the link.
                    pendingDeepLink?.let { raw ->
                        val link = remember(raw) { parseShodLink(raw) } ?: return@let
                        AlertDialog(
                            onDismissRequest = { pendingDeepLink = null },
                            title = { Text("Импорт конфигурации Shod") },
                            text = { Text("Хост: ${link.host}\nPeer: ${link.port}\n\nЗаменить текущую конфигурацию?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingDeepLink = null
                                    scope.launch {
                                        settingsStore.saveShodParticipantLink(raw)
                                        authContext.showRaisedToast("Конфигурация Shod успешно подключена", Toast.LENGTH_SHORT)
                                    }
                                }) { Text("Импортировать") }
                            },
                            dismissButton = { TextButton(onClick = { pendingDeepLink = null }) { Text("Отмена") } },
                        )
                    }
                }
            }
                }
            }
            if (showVkAuthDialog) {
                val onToken: (VkAuthPayload) -> Unit = onToken@{ payload ->
                        if (!showVkAuthDialog) return@onToken
                        showVkAuthDialog = false
                        authScope.launch {
                            if (TunnelManager.running.value || TunnelManager.starting.value || TunnelManager.stopping.value) {
                                authContext.showRaisedToast("Остановите туннель перед сменой аккаунта", Toast.LENGTH_LONG)
                                return@launch
                            }
                            try {
                                appSettingsStore.saveVkAccessToken(payload.token, payload.userId)
                                authContext.showRaisedToast("Аккаунт VK подключён", Toast.LENGTH_SHORT)
                            } catch (_: Exception) {
                                authContext.showRaisedToast("Не удалось сохранить аккаунт VK. Остановите туннель и повторите вход", Toast.LENGTH_LONG)
                            }
                        }
                    }
                ShodTheme(themeMode = chosenTheme) {
                    VkAuthDialog(onToken, onDismiss = { showVkAuthDialog = false }, chooseAccount = chooseVkAccount)
                }
            }

        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importShodLink(intent)
        if (intent.action == WifiDiscoveryNotification.OPEN) WifiDiscovery.openHome.value = true
    }

    private fun importShodLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val raw = intent.dataString ?: return
        val link = parseShodLink(raw)
        if (link == null) {
            showRaisedToast("Неверная ссылка Shod", Toast.LENGTH_SHORT)
            return
        }
        pendingDeepLink = raw
    }

    private fun checkAndRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestBattery()
            }
        } else {
            checkAndRequestBattery()
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkAndRequestBattery() {
        if (batteryPromptInFlight) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        lifecycleScope.launch {
            val shouldRequest = shouldRequestBatteryOptimizationExemption(
                promptHandled = appSettingsStore.isBatteryOptimizationPromptHandled(),
                alreadyExempt = powerManager.isIgnoringBatteryOptimizations(packageName),
            )
            if (!shouldRequest) {
                if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    appSettingsStore.markBatteryOptimizationPromptHandled()
                }
                return@launch
            }

            batteryPromptInFlight = true
            val launched = runCatching {
                batteryLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        "package:$packageName".toUri(),
                    ),
                )
                true
            }.getOrDefault(false)
            if (!launched) {
                batteryPromptInFlight = false
                appSettingsStore.markBatteryOptimizationPromptHandled()
            }
        }
    }
}
