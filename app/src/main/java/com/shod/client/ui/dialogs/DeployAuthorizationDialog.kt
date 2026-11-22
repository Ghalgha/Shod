package com.shod.client.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Авторизация Shod") },
        text = {
            Column {
                OutlinedTextField(mainPassword, onMainPasswordChange, label = { Text("Пароль туннеля") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(webLogin, onWebLoginChange, label = { Text("Логин web-панели") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(webPassword, onWebPasswordChange, label = { Text("Пароль web-панели") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
