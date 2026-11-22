package com.shod.client.ui.screens

import com.shod.client.ui.components.appSectionCardColor
import com.shod.client.ui.components.appSectionCardBorderColor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import com.shod.client.settings.model.ImportedConnectionSettings
import com.shod.client.SettingsStore
import com.shod.client.TunnelManager
import com.shod.client.vpn.workers.WorkerCountPolicy
import com.shod.client.settings.model.defaultProfileName
import com.shod.client.links.parseShodLink
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.design.ShodSizes
import com.shod.client.ui.components.LocalHeaderActions
import kotlinx.coroutines.launch


@Composable
private fun WorkerProfileButton(
    title: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f).heightIn(min = 54.dp),
            shape = ShodShapes.Control,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(value, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onInfoClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = "Информация: $title",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun HomeScreen(
    settingsStore: SettingsStore,
    activeProfile: Int,
    connected: Boolean,
    connecting: Boolean,
    stopping: Boolean = false,
    autoPausedForWifi: Boolean = false,
    onToggleConnection: () -> Unit,
    onOpenCredentials: () -> Unit,
    onOpenDeploy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val headerActions = LocalHeaderActions.current
    val activeWorkers by TunnelManager.activeWorkers.collectAsState()
    val vpnReady by TunnelManager.vpnReady.collectAsState()
    val statsText by TunnelManager.stats.collectAsState()
    val dnsProfile by TunnelManager.dnsProfile.collectAsState()
    val userFacingError by TunnelManager.userFacingError.collectAsState()
    val ownerMode by settingsStore.uiOwnerMode.collectAsState(initial = false)
    val occupiedProfiles by settingsStore.occupiedProfiles.collectAsState(initial = emptySet())
    val profileNames by settingsStore.profileNames.collectAsState(initial = emptyMap())
    val workersPerHash by settingsStore.workersPerHash.collectAsState(initial = 18)
    val workerProfileMode by settingsStore.workerProfileMode.collectAsState(initial = "custom")
    val workersMaximum by settingsStore.workersMaximum.collectAsState(initial = 70)
    val scope = rememberCoroutineScope()
    var showServerDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBuyDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showWorkerDialog by remember { mutableStateOf(false) }
    var workerProfileInfoMode by remember { mutableStateOf<String?>(null) }
    var showCustomWorkers by remember { mutableStateOf(false) }
    var customWorkers by remember { mutableStateOf(workersPerHash.toFloat()) }

    Column(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SHOD", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            headerActions?.let { Row(content = it) }
        }

        HomeConnectionInfo(stats = statsText, dns = dnsProfile)

        Spacer(modifier = Modifier.padding(top = 18.dp))

        val activeServerName = profileNames[activeProfile]
            ?.takeIf { it.isNotBlank() }
            ?: if (activeProfile in occupiedProfiles) defaultProfileName(activeProfile) else "Сервер не выбран"

        Card(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { showServerDialog = true },
            shape = ShodShapes.Card,
            colors = CardDefaults.cardColors(containerColor = appSectionCardColor()),
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "СЕРВЕР",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        activeServerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeProfile in occupiedProfiles) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
            }
        }

        Button(
            onClick = onToggleConnection,
            enabled = !connecting && !stopping,
            modifier = Modifier.fillMaxWidth(),
            shape = ShodShapes.Control,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (connected) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (stopping) "ОСТАНОВКА…" else if (connecting) "ПОДКЛЮЧЕНИЕ…" else if (connected || autoPausedForWifi) "ОТКЛЮЧИТЬСЯ" else "ПОДКЛЮЧИТЬСЯ", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpenCredentials,
                modifier = Modifier.weight(1f),                shape = ShodShapes.Control,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Хеши и авторизация", fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
            Button(
                onClick = { showWorkerDialog = true },
                modifier = Modifier.weight(1f),
                shape = ShodShapes.Control,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Мощность · $workersPerHash", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }


    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Выбор сервера") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (occupiedProfiles.isEmpty()) {
                        Text(
                            "Добавленных серверов пока нет",
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        occupiedProfiles.sorted().forEach { profile ->
                            val name = profileNames[profile]?.takeIf { it.isNotBlank() } ?: defaultProfileName(profile)
                            TextButton(
                                onClick = {
                                    scope.launch { settingsStore.saveActiveProfile(profile) }
                                    showServerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (profile == activeProfile) "$name · выбран" else name,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (profile == activeProfile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (profile == activeProfile) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                    if (ownerMode) {
                        OutlinedButton(
                            onClick = { showServerDialog = false; onOpenDeploy() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Деплой", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(
                        onClick = {
                            showServerDialog = false
                            linkText = ""
                            errorText = null
                            showAddDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Добавить сервер")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerDialog = false }) { Text("Закрыть") }
            },
        )
    }

    if (showWorkerDialog) {
        Dialog(
            onDismissRequest = { showWorkerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
                shape = ShodShapes.Dialog,
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showWorkerDialog = false }, modifier = Modifier.size(48.dp)) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(24.dp))
                        }
                        Text("Мощность", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WorkerProfileButton(
                            title = "Энергосбережение",
                            value = "${WorkerCountPolicy.normalize(10, workersMaximum)} воркеров",
                            selected = workerProfileMode == "economy",
                            onClick = { scope.launch { settingsStore.saveWorkerProfileMode("economy") }; showWorkerDialog = false },
                            onInfoClick = { workerProfileInfoMode = "economy" },
                        )
                        WorkerProfileButton(
                            title = "Сбалансированный",
                            value = "${WorkerCountPolicy.normalize(20, workersMaximum)} воркеров",
                            selected = workerProfileMode == "balanced",
                            onClick = { scope.launch { settingsStore.saveWorkerProfileMode("balanced") }; showWorkerDialog = false },
                            onInfoClick = { workerProfileInfoMode = "balanced" },
                        )
                        WorkerProfileButton(
                            title = "Максимальный",
                            value = "${WorkerCountPolicy.normalize(workersMaximum, workersMaximum)} воркеров",
                            selected = workerProfileMode == "maximum",
                            onClick = { scope.launch { settingsStore.saveWorkerProfileMode("maximum") }; showWorkerDialog = false },
                            onInfoClick = { workerProfileInfoMode = "maximum" },
                        )
                        OutlinedButton(
                            onClick = { customWorkers = workersPerHash.toFloat(); showWorkerDialog = false; showCustomWorkers = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            shape = ShodShapes.Control,
                        ) {
                            Text("Выбрать вручную", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    workerProfileInfoMode?.let { mode ->
        val (title, description) = when (mode) {
            "economy" -> "Энергосбережение" to "Минимально необходимое количество ресурсов для повседневной работы. Приоритет — экономия энергии, а не максимальная скорость."
            "balanced" -> "Сбалансированный" to "Оптимальное сочетание скорости и экономичности для большинства повседневных задач."
            else -> "Максимальный" to "Приоритет пропускной способности соединения. Использует больше ресурсов ради максимально возможной скорости передачи данных."
        }
        AlertDialog(
            onDismissRequest = { workerProfileInfoMode = null },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(BorderStroke(1.dp, appSectionCardBorderColor()), ShodShapes.Dialog),
            shape = ShodShapes.Dialog,
            containerColor = appSectionCardColor(),
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { workerProfileInfoMode = null },
                    modifier = Modifier.fillMaxWidth().height(ShodSizes.ControlHeight),
                    shape = ShodShapes.Pill,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        "Понятно",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            tonalElevation = 0.dp,
        )
    }

    if (showCustomWorkers) {
        Dialog(
            onDismissRequest = { showCustomWorkers = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
                shape = ShodShapes.Dialog,
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showCustomWorkers = false; showWorkerDialog = true }, modifier = Modifier.size(48.dp)) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(24.dp))
                        }
                        Text("Количество воркеров", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Slider(value = customWorkers.coerceIn(10f, workersMaximum.toFloat()), onValueChange = { customWorkers = it }, valueRange = 10f..workersMaximum.toFloat().coerceAtLeast(10f))
                        Text("Допустимый максимум для выбранного сервера: $workersMaximum", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { scope.launch { settingsStore.saveWorkersPerHash(WorkerCountPolicy.normalize(customWorkers.toInt(), workersMaximum)) }; showCustomWorkers = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ShodShapes.Control,
                        ) { Text("Сохранить") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить сервер") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoxWithConstraints {
                        if (maxWidth < 340.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(linkText, { linkText = it; errorText = null }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Ссылка Shod") }, placeholder = { Text("shod://…") })
                                TextButton(onClick = { scope.launch { importServerLink(settingsStore, linkText, { errorText = it }, { showAddDialog = false }) } }, enabled = linkText.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("ОК") }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(linkText, { linkText = it; errorText = null }, modifier = Modifier.weight(1f).widthIn(min = 0.dp), singleLine = true, label = { Text("Ссылка Shod") }, placeholder = { Text("shod://…") })
                                TextButton(onClick = { scope.launch { importServerLink(settingsStore, linkText, { errorText = it }, { showAddDialog = false }) } }, enabled = linkText.isNotBlank()) { Text("ОК") }
                            }
                        }
                    }
                    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showBuyDialog = true }, modifier = Modifier.align(Alignment.Start)) { Text("Купить сервер") }
                }
            },
            confirmButton = { TextButton(onClick = { showAddDialog = false }) { Text("Закрыть") } },
        )
    }

    if (showBuyDialog) {
        AlertDialog(
            onDismissRequest = { showBuyDialog = false },
            title = { Text("Покупка сервера") },
            text = { Text("Покупка серверов пока недоступна.") },
            confirmButton = { TextButton(onClick = { showBuyDialog = false }) { Text("Понятно") } },
        )
    }

    userFacingError?.let { message ->
        AlertDialog(
            onDismissRequest = TunnelManager::dismissUserFacingError,
            title = { Text("Не удалось подключиться") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = TunnelManager::dismissUserFacingError) { Text("Понятно") }
            },
        )
    }
}

@Composable
private fun HomeConnectionInfo(stats: String, dns: String) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val cardHeight = (screenHeightDp * 0.17f).coerceIn(140f, 190f).dp
    Card(
        modifier = Modifier.fillMaxWidth().height(cardHeight),
        shape = ShodShapes.Card,
        colors = CardDefaults.cardColors(containerColor = appSectionCardColor()),
        border = BorderStroke(1.dp, appSectionCardBorderColor()),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stats,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "DNS: $dns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

private suspend fun importServerLink(
    settingsStore: SettingsStore,
    link: String,
    onError: (String) -> Unit,
    onImported: () -> Unit,
) {
    val parsed = parseShodLink(link)
    if (parsed == null) {
        onError("Некорректная ссылка Shod")
        return
    }
        val freeProfile = settingsStore.findFirstFreeProfile()
        if (freeProfile == null) {
            onError("Достигнут лимит в 32 сервера")
            return
        }
        val saved = settingsStore.saveImportedConnection(
            profile = freeProfile,
            imported = ImportedConnectionSettings(
                host = parsed.host,
                port = parsed.port,
                password = parsed.password,
                hashes = parsed.hashes,
                vkAccessToken = parsed.vkAccessToken,
                power = parsed.power,
                name = parsed.host,
            ),
            participantLink = link.trim().takeIf { parsed.hashes.isNotEmpty() || parsed.vkAccessToken.isNotEmpty() },
        )
        if (saved) {
            settingsStore.saveActiveProfile(freeProfile)
            onImported()
        } else {
            onError("Не удалось сохранить ссылку: в ней отсутствует пароль подключения")
        }
}