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

/** SSH key editor extracted from DeployController; state and persistence remain controller-owned. */
@Composable
internal fun SshKeysDialog(
    privateKey: String,
    passphrase: String,
    certificate: String,
    onPrivateKeyChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onCertificateChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SSH private key") },
        text = {
            Column {
                OutlinedTextField(privateKey, onPrivateKeyChange, label = { Text("Приватный ключ") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                OutlinedTextField(passphrase, onPassphraseChange, label = { Text("Пароль ключа") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(certificate, onCertificateChange, label = { Text("Сертификат (если нужен)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
