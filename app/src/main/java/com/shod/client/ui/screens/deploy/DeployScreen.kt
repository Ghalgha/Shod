package com.shod.client.ui.screens.deploy

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shod.client.ShodConstants
import com.shod.client.ui.components.AppSectionCard
import com.shod.client.ui.components.SettingRow
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.design.ShodSpacing

@Composable
internal fun DeployScreen(
    onBack: () -> Unit = {},
    state: DeployUiState,
    onAction: (DeployAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showRegenConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(ShodSpacing.Md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onBack,
                    enabled = !state.isDeploying,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("Готово", maxLines = 1)
                }
            }
            AppSectionCard {
                    Text("Деплой и SSH", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    if (state.sshKeysMode) {
                        DeployFieldPair(
                            first = {
                                HostAndSshPortFields(state = state, onAction = onAction)
                            },
                            second = {
                                DeployInput(
                                    value = state.sshLogin,
                                    onValueChange = { onAction(DeployAction.LoginChanged(it)) },
                                    label = "Логин SSH",
                                    enabled = !state.isDeploying,
                                    isError = state.sshLogin.isBlank(),
                                    imeAction = ImeAction.Next,
                                )
                            },
                        )
                        AnimatedVisibility(
                            visible = state.manualPorts,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            ManualPortFields(state = state, onAction = onAction)
                        }
                        OutlinedButton(
                            onClick = { onAction(DeployAction.EditSshKeys) },
                            enabled = !state.isDeploying,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = ShodShapes.Pill,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (state.sshKeysFilled < 1) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            ),
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ключи SSH ${state.sshKeysFilled}/1", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
                        }
                    } else {
                        HostAndSshPortFields(state = state, onAction = onAction)
                        AnimatedVisibility(
                            visible = state.manualPorts,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            ManualPortFields(state = state, onAction = onAction)
                        }
                        DeployFieldPair(
                            first = {
                                DeployInput(
                                    value = state.sshLogin,
                                    onValueChange = { onAction(DeployAction.LoginChanged(it)) },
                                    label = "Логин SSH",
                                    enabled = !state.isDeploying,
                                    isError = state.sshLogin.isBlank(),
                                    imeAction = ImeAction.Next,
                                )
                            },
                            second = {
                                DeployInput(
                                    value = state.sshPassword,
                                    onValueChange = { onAction(DeployAction.PasswordChanged(it)) },
                                    label = "Пароль SSH",
                                    enabled = !state.isDeploying,
                                    isError = state.sshPassword.isBlank(),
                                    keyboardType = KeyboardType.Password,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingContent = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                             Icon(
                                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                    Column {
                        SettingRow(
                            title = "Ключи SSH",
                            checked = state.sshKeysMode,
                            enabled = !state.isDeploying,
                            onCheckedChange = { onAction(DeployAction.SshKeysModeChanged(it)) },
                        )
                        SettingRow(
                            title = "Ручные порты",
                            checked = state.manualPorts,
                            enabled = !state.isDeploying,
                            onCheckedChange = { onAction(DeployAction.ManualPortsChanged(it)) },
                        )
                        SettingRow(
                            title = "Установить в Docker",
                            checked = state.dockerInstall,
                            enabled = !state.isDeploying,
                            onCheckedChange = { onAction(DeployAction.DockerInstallChanged(it)) },
                            onInfoClick = { onAction(DeployAction.DockerInfo) },
                            infoContentDescription = "О Docker-установке",
                        )
                        Text(
                            text = "Секретный путь и ссылка панели",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (!state.isDeploying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            DeployInput(
                                value = state.panelDisplayUrl,
                                onValueChange = {},
                                placeholder = "https://1.2.3.4:46002/uuid",
                                enabled = !state.isDeploying,
                                readOnly = true,
                                singleLine = false,
                                minLines = 2,
                                maxLines = 2,
                                textColor = if (!state.isDeploying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.fillMaxWidth(),
                                trailingContent = {
                                    IconButton(
                                        onClick = { showRegenConfirmDialog = true },
                                        enabled = !state.isDeploying,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Перегенерировать UUID",
                                        )
                                    }
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(end = 52.dp)
                                    .clickable(enabled = !state.isDeploying) {
                                        onAction(DeployAction.OpenControlPanel)
                                    }
                            )
                        }
                    }
                }

                AppSectionCard {
                    Text(
                        "Авторизация",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Пароль подключения к туннелю и учётные данные web-панели",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onAction(DeployAction.EditAuthorization) },
                        enabled = !state.isDeploying,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = ShodShapes.Pill,
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Настроить авторизацию", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (showRegenConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showRegenConfirmDialog = false },
                        title = { Text("Перегенерация пути") },
                        text = { Text("После перегенерации старый путь будет утерян, и доступ к панели будет утерян, если вы потеряете новый UUID. Перегенерировать?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showRegenConfirmDialog = false
                                    onAction(DeployAction.RegenerateSecretPath)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            ) {
                                Text("Перегенерировать")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRegenConfirmDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }

                if (state.isDeploying) {
                    DeployProgressCard(
                        progress = state.progress,
                        currentStep = state.currentStep,
                    )
                }

                DeployActionBar(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.fillMaxWidth(),
                )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        )
    }
}

@Composable
private fun DeployFieldPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShodSpacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeployInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    textColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        placeholder = placeholder?.let { { Text(it, maxLines = if (singleLine) 1 else maxLines) } },
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        visualTransformation = visualTransformation,
        trailingIcon = trailingContent,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = textColor ?: MaterialTheme.colorScheme.onSurface,
        ),
        shape = ShodShapes.Control,
    )
}

@Composable
private fun DeployActionBar(
    state: DeployUiState,
    onAction: (DeployAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShodSpacing.Sm),
    ) {
        OutlinedButton(
            onClick = { onAction(DeployAction.Uninstall) },
            enabled = !state.isDeploying && state.canUninstall,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = ShodShapes.Pill,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Удалить", maxLines = 1)
        }
        Button(
            onClick = { onAction(DeployAction.Install) },
            enabled = !state.isDeploying,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = ShodShapes.Pill,
        ) {
            if (state.isDeploying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    !state.isDeploying -> "Установить"
                    state.currentStep.startsWith("Проверка") -> "Проверка…"
                    else -> "Установка…"
                },
                maxLines = 1,
            )
        }
    }
}

// The address and the SSH port side by side, always: the SSH port is the VPS's, not one of the shod server's ports
// «Ручные порты» chooses (it used to sit there, so a VPS with SSH off port 22 could not be reached with the switch off).
@Composable
private fun HostAndSshPortFields(
    state: DeployUiState,
    onAction: (DeployAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShodSpacing.Sm),
    ) {
        DeployInput(
            value = state.host,
            onValueChange = { onAction(DeployAction.HostChanged(it)) },
            label = "IP сервера или домен",
            placeholder = "1.2.3.4",
            enabled = !state.isDeploying,
            isError = state.host.isBlank() || hostHasPort(state.host),
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.weight(1f),
        )
        DeployInput(
            value = state.sshPort,
            onValueChange = { onAction(DeployAction.SshPortChanged(it)) },
            label = "Порт SSH",
            placeholder = "22",
            enabled = !state.isDeploying,
            isError = !isValidPort(state.sshPort),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(104.dp),
        )
    }
}

@Composable
private fun ManualPortFields(
    state: DeployUiState,
    onAction: (DeployAction) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ShodSpacing.Sm),
        ) {
            DeployInput(
                value = state.peerPort,
                onValueChange = { onAction(DeployAction.PeerPortChanged(it)) },
                label = "Порт туннеля",
                placeholder = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT.toString(),
                enabled = !state.isDeploying,
                isError = state.showValidation && !isValidPort(state.peerPort),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            DeployInput(
                value = state.webPort,
                onValueChange = { onAction(DeployAction.WebPortChanged(it)) },
                label = "Порт WEB-панели",
                placeholder = ShodConstants.Network.DEFAULT_SERVER_WEB_PORT.toString(),
                enabled = !state.isDeploying,
                isError = state.showValidation && !isValidPort(state.webPort),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DeployProgressCard(
    progress: Float,
    currentStep: String,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (progress <= 0.01f) snap() else tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "deploy_progress",
    )

    AppSectionCard(verticalArrangement = Arrangement.spacedBy(ShodSpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ShodSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentStep.ifBlank { "Подготовка…" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Deploy compact", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Deploy dark", widthDp = 720, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeployScreenPreview() {
    com.shod.client.ShodTheme(themeMode = ShodConstants.Theme.MODE_DARK) {
        DeployScreen(
            state = DeployUiState(
                host = "vpn.example.com",
                sshLogin = "root",
                sshPassword = "password",
                mainPasswordConfigured = true,
                webPanelConfigured = true,
            ),
            onAction = {},
        )
    }
}
