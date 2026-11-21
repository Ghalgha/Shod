package com.shod.client.ui.navigation

import com.shod.client.*

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
import com.shod.client.ui.screens.CredentialsScreen
import com.shod.client.ShodConstants
import com.shod.client.ui.deploy.DeployController
import com.shod.client.ui.deploy.DeployCheckState
import com.shod.client.ui.components.LocalHeaderActions
import com.shod.client.ui.design.ShodMotion
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.dialogs.VkAuthDialog
import com.shod.client.links.parseShodLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private data class NavItem(
    val id: Int,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val navItems = listOf(
    NavItem(0, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
)

internal fun navigationSwipeProgress(totalDrag: Float, containerWidth: Int): Float =
    if (containerWidth <= 0) 0f else (abs(totalDrag) / (containerWidth.toFloat() * 0.25f)).coerceIn(0f, 1f)

internal fun shouldCommitNavigationSwipe(progress: Float): Boolean = progress > 0.4f

internal fun resolveActiveNavIds(shodLinkMode: Boolean, participantShowLogs: Boolean): List<Int> = listOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsStore: SettingsStore,
    themeMode: String = ShodConstants.Theme.MODE_SYSTEM,
    onThemeChange: (String) -> Unit = {},
    onVkAuthRequested: () -> Unit = {},
) {
    val unreadErrors by TunnelManager.unreadErrorCount.collectAsStateWithLifecycle()
    val loggingEnabled by settingsStore.loggingEnabled.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(loggingEnabled) { TunnelManager.isLoggingEnabled = loggingEnabled }
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val tunnelStarting by TunnelManager.starting.collectAsStateWithLifecycle()
    val tunnelStopping by TunnelManager.stopping.collectAsStateWithLifecycle()
    val tunnelAutoPausedForWifi by TunnelManager.autoPausedForWifi.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val activeProfile by settingsStore.activeProfile.collectAsStateWithLifecycle(initialValue = 0)
    val deployStore = remember(context.applicationContext) { SettingsStore(context.applicationContext, deployScope = true) }
    val deploySettings by deployStore.deploySettingsSnapshot.collectAsStateWithLifecycle(
        initialValue = DeploySettingsSnapshot()
    )
    val deployCheckState = remember(deploySettings.profile) { DeployCheckState() }
    val tunnelAuthSettings by settingsStore.tunnelAuthSnapshot.collectAsStateWithLifecycle(
        initialValue = TunnelAuthSnapshot()
    )
    val ownerMode by settingsStore.uiOwnerMode.collectAsStateWithLifecycle(false)
    val shodLinkMode = !ownerMode // Navigation role is independent of a profile's link authentication.
    val excludedApps by settingsStore.excludedApps.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedExcludedApps ?: "")
    val whitelistApps by settingsStore.whitelistApps.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedWhitelistApps ?: "")
    val showSystemApps by settingsStore.showSystemApps.collectAsStateWithLifecycle(initialValue = false)
    val isWhitelist by settingsStore.isWhitelist.collectAsStateWithLifecycle(initialValue = SettingsStore.cachedIsWhitelist ?: false)
    val serverRoutingActive by TunnelManager.serverRoutingActive.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCredentialsDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var showRoutingScreen by rememberSaveable { mutableStateOf(false) }
    var showDeployScreen by rememberSaveable { mutableStateOf(false) }
    var toggleTunnelAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val openWifiHome by WifiDiscovery.openHome.collectAsStateWithLifecycle()
    LaunchedEffect(openWifiHome) {
        if (openWifiHome) { selectedTab = 0; WifiDiscovery.openHome.value = false }
    }
    var tunnelValidationRequest by rememberSaveable { mutableIntStateOf(0) }
    val dragTargetIndex = remember { mutableIntStateOf(-1) }
    val dragProgress = remember { mutableFloatStateOf(0f) }
    var wasRunning by remember { mutableStateOf(tunnelRunning) }
    val updateCheckIntervalHours by settingsStore.updateCheckIntervalHours.collectAsStateWithLifecycle(
        initialValue = DEFAULT_UPDATE_CHECK_INTERVAL_HOURS
    )
    var pendingRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v").substringBefore('-')}" }
    val safeBottomInset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
    val navToolbarHeight = 66.dp
    val navToolbarBottomMargin = 10.dp
    val navContentGap = 5.dp
    val navOverlayReserve = 0.dp
    val navigationDragSlop = with(density) { 6.dp.toPx() }

    val activeNavItems = remember(shodLinkMode) {
        resolveActiveNavIds(shodLinkMode, participantShowLogs = true).mapNotNull { id ->
            navItems.firstOrNull { it.id == id }
        }
    }
    val tabStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(activeNavItems) {
        if (activeNavItems.none { it.id == selectedTab }) {
            selectedTab = 0
        }
    }

    LaunchedEffect(tunnelRunning, tunnelAutoPausedForWifi) {
        if (wasRunning && !tunnelRunning && !tunnelAutoPausedForWifi) {
            TunnelManager.startCooldown(ShodConstants.Timeouts.VPN_PERMISSION_COOLDOWN_MS)
        }
        wasRunning = tunnelRunning
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) TunnelManager.clearUnreadErrors()
    }

    LaunchedEffect(updateCheckIntervalHours) {
        if (updateCheckIntervalHours == UPDATE_CHECK_NEVER) return@LaunchedEffect

        val intervalMillis = updateIntervalHoursToMillis(updateCheckIntervalHours)
            ?: updateIntervalHoursToMillis(DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
            ?: 12L * 60L * 60L * 1000L

        suspend fun runUpdateCheck(reason: String) {
            val checkedAt = System.currentTimeMillis()
            val release = fetchLatestReleaseInfo(currentVersion)
            settingsStore.saveUpdateState(
                lastCheckAt = checkedAt,
                latestVersion = release?.versionTag ?: "",
                error = if (release == null) "Не удалось проверить" else ""
            )

            if (release == null) {
                Log.w("Shod", "[WARN] Update check: no release info, local=$currentVersion reason=$reason")
                return
            }

            val hasUpdate = isNewerVersion(currentVersion, release.versionTag)
            val postponeVer = settingsStore.updatePostponeVersion.first()
            val postponeUntil = settingsStore.updatePostponeUntil.first()
            val isPostponed = postponeVer == release.versionTag && checkedAt < postponeUntil
            Log.i(
                "Shod",
                "Update check: local=$currentVersion remote=${release.versionTag} newer=$hasUpdate postponed=$isPostponed reason=$reason"
            )

            if (hasUpdate && !isPostponed) {
                settingsStore.saveUpdateDialogShown(release.versionTag, checkedAt)
                pendingRelease = release
            }
        }

        runUpdateCheck("startup")

        while (isActive) {
            val now = System.currentTimeMillis()
            val lastCheck = settingsStore.updateLastCheckAt.first()
            val nextCheckAt = lastCheck + intervalMillis
            val waitMs = (nextCheckAt - now).coerceAtLeast(intervalMillis)
            delay(waitMs)
            if (isActive) {
                runUpdateCheck("periodic")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackdrop(modifier = Modifier.matchParentSize())
        if (tunnelAuthSettings.isLoaded) {
            ConnectionController(
                settingsStore = settingsStore,
                tunnelAuthSettings = tunnelAuthSettings,
                onInvalidConfiguration = { showCredentialsDialog = true; tunnelValidationRequest += 1 },
                onConnectionActionReady = { action -> toggleTunnelAction = action },
            )
        }

        CompositionLocalProvider(
            LocalHeaderActions provides {
                HeaderActions(
                    settingsStore = settingsStore,
                    shodLinkMode = shodLinkMode,
                    onOpenSettings = { showSettingsScreen = true },
                )
            },
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                containerColor = Color.Transparent,
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .pointerInput(selectedTab, shodLinkMode, showSettingsScreen, showRoutingScreen) {
                        // Settings and routing are full-screen destinations; horizontal swipes must not
                        // change the underlying tab while either destination is open.
                        if (showSettingsScreen || showRoutingScreen) return@pointerInput
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                                dragTargetIndex.intValue = -1
                                dragProgress.floatValue = 0f
                            },
                            onDragCancel = {
                                dragTargetIndex.intValue = -1
                                dragProgress.floatValue = 0f
                            },
                            onDragEnd = {
                                if (dragTargetIndex.intValue in activeNavItems.indices && shouldCommitNavigationSwipe(dragProgress.floatValue)) {
                                    selectedTab = activeNavItems[dragTargetIndex.intValue].id
                                    if (selectedTab == 3) TunnelManager.clearUnreadErrors()
                                }
                                dragTargetIndex.intValue = -1
                                dragProgress.floatValue = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            if (abs(totalDrag) < navigationDragSlop) {
                                dragTargetIndex.intValue = -1
                                dragProgress.floatValue = 0f
                                return@detectHorizontalDragGestures
                            }

                            val currentActiveIndex = activeNavItems.indexOfFirst { it.id == selectedTab }
                            val candidate = if (totalDrag < 0f) currentActiveIndex + 1 else currentActiveIndex - 1
                            if (candidate !in activeNavItems.indices) {
                                dragTargetIndex.intValue = -1
                                dragProgress.floatValue = 0f
                                return@detectHorizontalDragGestures
                            }

                            dragTargetIndex.intValue = candidate
                            dragProgress.floatValue = navigationSwipeProgress(totalDrag, size.width)
                        }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (showSettingsScreen || showRoutingScreen) 0.dp else navOverlayReserve),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                        key(selectedTab) {
                            val tab = selectedTab
                            val tabStateKey: Any = when (tab) {
                                0 -> "home:${ownerMode}:${tunnelAuthSettings.profile}:${deploySettings.profile}"
                                else -> tab
                            }
                            tabStateHolder.SaveableStateProvider(tabStateKey) {
                                when (tab) {
                                0 -> if (showRoutingScreen) {
                                    ShodTheme(themeMode = themeMode) {
                                        RoutingScreen(
                                            settingsStore = settingsStore,
                                            savedExcluded = excludedApps,
                                            savedWhitelist = whitelistApps,
                                            showSystemApps = showSystemApps,
                                            isWhitelist = isWhitelist,
                                            serverRoutingActive = serverRoutingActive,
                                            onClose = { showRoutingScreen = false },
                                        )
                                    }
                                } else if (showSettingsScreen) {
                                    ShodTheme(themeMode = themeMode) {
                                        SettingsScreen(
                                            settingsStore = settingsStore,
                                            themeMode = themeMode,
                                            onThemeModeChange = onThemeChange,
                                            onClose = { showSettingsScreen = false },
                                            onOpenRouting = { showRoutingScreen = true },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                } else if (tunnelAuthSettings.isLoaded) {
                                    ShodTheme(themeMode = themeMode) {
                                        if (showDeployScreen) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                DeployController(
                                                    settingsStore = deployStore,
                                                    savedSettings = deploySettings,
                                                    checkState = deployCheckState,
                                                    onBack = { showDeployScreen = false },
                                                )
                                            }
                                        } else HomeScreen(
                                            settingsStore = settingsStore,
                                            activeProfile = activeProfile,
                                            connected = tunnelRunning,
                                            connecting = tunnelStarting,
                                            stopping = tunnelStopping,
                                            autoPausedForWifi = tunnelAutoPausedForWifi,
                                            onToggleConnection = { toggleTunnelAction?.invoke() },
                                            onOpenCredentials = { showCredentialsDialog = true },
                                            onOpenDeploy = { showDeployScreen = true },
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.fillMaxSize())
                                }
                                3 -> LogsScreen(settingsStore = settingsStore)
                                else -> Spacer(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        }
                    }

                }
            }
        }
    }

    BackHandler(enabled = showDeployScreen) { showDeployScreen = false }
    BackHandler(enabled = showRoutingScreen) { showRoutingScreen = false }
    BackHandler(enabled = showSettingsScreen && !showRoutingScreen) { showSettingsScreen = false }

    if (showCredentialsDialog) {
        Dialog(
            onDismissRequest = { showCredentialsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f),
                shape = ShodShapes.Dialog,
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { showCredentialsDialog = false },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            "Хеши и авторизация",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(modifier = Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalHeaderActions provides null) {
                            CredentialsScreen(
                                settingsStore = settingsStore,
                                tunnelAuthSettings = tunnelAuthSettings,
                                validationRequest = tunnelValidationRequest,
                                onVkAuthRequested = onVkAuthRequested,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRelease?.let { release ->
        AppUpdateDialog(
            release = release,
            onPostpone = {
                pendingRelease = null
                context.showRaisedToast("Обновление отложено на 24 часа.", Toast.LENGTH_SHORT)
                scope.launch {
                    val now = System.currentTimeMillis()
                    settingsStore.saveUpdatePostpone(
                        version = release.versionTag,
                        until = now + 24L * 60L * 60L * 1000L
                    )
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_POSTPONED,
                        actedAt = now
                    )
                }
            },
            onUpdate = {
                pendingRelease = null
                scope.launch {
                    settingsStore.saveUpdateDialogAction(
                        version = release.versionTag,
                        action = UPDATE_DIALOG_ACTION_UPDATE,
                        actedAt = System.currentTimeMillis()
                    )
                    openReleaseUrl(context, release.releaseUrl)
                }
            }
        )
    }
}

@Composable
private fun ProxyNavigationBar(
    navItems: List<NavItem>,
    selectedTab: Int,
    dragTargetIndex: IntState,
    dragProgress: FloatState,
    unreadErrors: Int,
    tunnelRunning: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    val selectedColor = colors.onPrimary
    val unselectedColor = colors.onSurfaceVariant.copy(alpha = 0.55f)
    val indicatorShape = ShodShapes.Control
    val shellColor = if (isDark) {
        lerp(colors.background, colors.surface, 0.86f)
    } else {
        lerp(colors.surface, colors.surfaceVariant, 0.48f).copy(alpha = 0.95f)
    }
    val shellBorder = if (isDark) {
        colors.outlineVariant.copy(alpha = 0.42f)
    } else {
        colors.outline.copy(alpha = 0.16f)
    }
    val indicatorColor = colors.primary
    val selectedVisualIndex = remember(selectedTab, navItems) {
        navItems.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0)
    }
    val indicatorIndex = remember { Animatable(selectedVisualIndex.toFloat()) }
    val isDragging = dragTargetIndex.intValue in navItems.indices
    val requestedIndicatorIndex = if (isDragging) {
        selectedVisualIndex.toFloat() +
            (dragTargetIndex.intValue - selectedVisualIndex) * dragProgress.floatValue
    } else {
        selectedVisualIndex.toFloat()
    }

    LaunchedEffect(isDragging, requestedIndicatorIndex) {
        if (isDragging) {
            indicatorIndex.snapTo(requestedIndicatorIndex)
        } else {
                indicatorIndex.animateTo(
                    targetValue = requestedIndicatorIndex,
                    animationSpec = tween(
                    durationMillis = ShodMotion.Fast,
                    easing = ShodMotion.StandardEasing,
                )
            )
        }
    }
    val dragVisualIndex = indicatorIndex.value

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        val trackPadding = 6.dp
        val itemWidth = (maxWidth - trackPadding * 2) / navItems.size
        val indicatorOffset = trackPadding + itemWidth * dragVisualIndex

        Surface(
            shape = ShodShapes.Card,
            color = shellColor,
            border = BorderStroke(1.dp, shellBorder),
            tonalElevation = if (isDark) 0.dp else 1.dp,
            shadowElevation = if (isDark) 1.dp else 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
            ) {
                Surface(
                    shape = indicatorShape,
                    color = indicatorColor,
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .padding(vertical = 7.dp)
                        .width(itemWidth)
                        .fillMaxHeight()
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = trackPadding, vertical = 7.dp)
                        .selectableGroup()
                ) {
                    navItems.forEachIndexed { index, item ->
                        val emphasis = (1f - abs(index - dragVisualIndex)).coerceIn(0f, 1f)
                        val iconColor = lerp(unselectedColor, selectedColor, emphasis)
                        val label = stringResource(item.labelRes)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(ShodShapes.Control)
                                .selectable(
                                    selected = item.id == selectedTab,
                                    role = Role.Tab,
                                    onClick = { onTabSelected(item.id) },
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = if (emphasis > 0.55f) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = iconColor
                                )
                                if (item.id == 3 && unreadErrors > 0) {
                                    Badge(
                                        containerColor = if (tunnelRunning) colors.primary else com.shod.client.ui.design.ShodTheme.extendedColors.warning,
                                        contentColor = colors.onPrimary,
                                        modifier = Modifier.offset(x = 12.dp, y = (-8).dp)
                                    ) {
                                        Text("$unreadErrors")
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (emphasis > 0.55f) FontWeight.SemiBold else FontWeight.Medium,
                                color = iconColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openReleaseUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        context.showRaisedToast("Не удалось открыть ссылку", Toast.LENGTH_SHORT)
    }
}
