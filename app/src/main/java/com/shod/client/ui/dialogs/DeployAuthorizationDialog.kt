package com.shod.client.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shod.client.ui.components.appSectionCardBorderColor
import com.shod.client.ui.components.appSectionCardColor
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.design.ShodSizes
import com.shod.client.ui.utils.TUNNEL_PASSWORD_RULE
import com.shod.client.ui.utils.generate16CharPassword
import com.shod.client.ui.utils.isValidTunnelPassword

/** UI-only credential editor; DeployController owns persistence and profile updates. */
@Composable
internal fun DeployAuthorizationDialog(
    mainPassword: String,
    webLogin: String,
    webPassword: String,
    onMainPasswordChange: (String) -> Unit,
    onWebLoginChange: (String) -> Unit,
    onWebPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var passwordsVisible by rememberSaveable { mutableStateOf(false) }
    val allowedChars = Regex("^[a-zA-Z0-9]+$")
    val mainValid = isValidTunnelPassword(mainPassword)
    val loginValid = webLogin.isNotEmpty() && webLogin.matches(allowedChars)
    val webPasswordValid = webPassword.isNotEmpty() && webPassword.matches(allowedChars)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = ShodShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Авторизация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Придумайте логин и пароль для web панели (латиница и цифры) и пароль туннеля.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
                Text("Пароль подключения к туннелю", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = mainPassword,
                    onValueChange = { onMainPasswordChange(it.filterNot(Char::isWhitespace)) },
                    label = { Text("Пароль туннеля", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShodShapes.Control,
                    isError = !mainValid,
                    supportingText = if (mainPassword.isNotEmpty() && !mainValid) {
                        { Text(TUNNEL_PASSWORD_RULE, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = if (passwordsVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { onMainPasswordChange(generate16CharPassword()) }) { Text("Сгенерировать") }
                    },
                )
                Text("Данные доступа к web панели", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = webLogin,
                    onValueChange = { onWebLoginChange(it.filterNot(Char::isWhitespace)) },
                    label = { Text("Логин WEB (придумайте)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = ShodShapes.Control,
                    isError = !loginValid,
                    supportingText = if (webLogin.isNotEmpty() && !loginValid) {
                        { Text("Разрешена «Латиница» и «Цифры»", color = MaterialTheme.colorScheme.error) }
                    } else null,
                )
                OutlinedTextField(
                    value = webPassword,
                    onValueChange = { onWebPasswordChange(it.filterNot(Char::isWhitespace)) },
                    label = { Text("Пароль WEB (придумайте)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = ShodShapes.Control,
                    isError = !webPasswordValid,
                    supportingText = if (webPassword.isNotEmpty() && !webPasswordValid) {
                        { Text("Разрешена «Латиница» и «Цифры»", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = if (passwordsVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                TextButton(onClick = { passwordsVisible = !passwordsVisible }) {
                    Text(if (passwordsVisible) "Скрыть пароли" else "Показать пароли")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(ShodSizes.ControlHeight),
                    shape = ShodShapes.Pill,
                    enabled = mainValid && loginValid && webPasswordValid,
                ) { Text("Сохранить", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            }
        }
    }
}
