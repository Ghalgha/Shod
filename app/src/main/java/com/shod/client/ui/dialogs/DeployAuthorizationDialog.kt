package com.shod.client.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.shod.client.ui.utils.TUNNEL_PASSWORD_RULE
import com.shod.client.ui.utils.generate16CharPassword
import com.shod.client.ui.utils.isValidTunnelPassword

/** UI-only editor for deployment credentials; persistence remains owned by DeployController. */
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
    var webPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val allowedWebCredential = Regex("^[a-zA-Z0-9]+$")
    val mainPasswordValid = isValidTunnelPassword(mainPassword)
    val webLoginValid = webLogin.isNotEmpty() && webLogin.matches(allowedWebCredential)
    val webPasswordValid = webPassword.isNotEmpty() && webPassword.matches(allowedWebCredential)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Авторизация Shod") },
        text = {
            Column {
                OutlinedTextField(
                    value = mainPassword,
                    onValueChange = { value -> onMainPasswordChange(value.filterNot(Char::isWhitespace)) },
                    label = { Text("Пароль туннеля") },
                    isError = !mainPasswordValid,
                    supportingText = if (mainPassword.isNotEmpty() && !mainPasswordValid) {
                        { Text(TUNNEL_PASSWORD_RULE) }
                    } else null,
                    trailingIcon = {
                        TextButton(onClick = { onMainPasswordChange(generate16CharPassword()) }) {
                            Text("Сгенерировать")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = webLogin,
                    onValueChange = { value -> onWebLoginChange(value.filterNot(Char::isWhitespace)) },
                    label = { Text("Логин web-панели") },
                    isError = !webLoginValid,
                    supportingText = if (webLogin.isNotEmpty() && !webLoginValid) {
                        { Text("Разрешены только латинские буквы и цифры") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = webPassword,
                    onValueChange = { value -> onWebPasswordChange(value.filterNot(Char::isWhitespace)) },
                    label = { Text("Пароль web-панели") },
                    isError = !webPasswordValid,
                    supportingText = if (webPassword.isNotEmpty() && !webPasswordValid) {
                        { Text("Разрешены только латинские буквы и цифры") }
                    } else null,
                    visualTransformation = if (webPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { webPasswordVisible = !webPasswordVisible }) {
                            Text(if (webPasswordVisible) "Скрыть" else "Показать")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = mainPasswordValid && webLoginValid && webPasswordValid) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
