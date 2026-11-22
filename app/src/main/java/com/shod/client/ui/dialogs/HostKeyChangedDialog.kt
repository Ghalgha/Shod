package com.shod.client.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.shod.client.platform.android.deploy.ssh.SshHostKeyStore

/** SSH host-key confirmation extracted from DeployController without changing its decisions. */
@Composable
internal fun HostKeyChangedDialog(
    change: SshHostKeyStore.KeyChange,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменился SSH-ключ сервера") },
        text = {
            Text(
                "Адрес: ${change.host}:${change.port}\n\n" +
                    "Сохранённый отпечаток:\n${change.pinned}\n\n" +
                    "Новый отпечаток:\n${change.presented}\n\n" +
                    "Такое бывает после переустановки VPS или если адрес теперь принадлежит другому серверу.\n\n" +
                        "Если вы не переустанавливали VPS и не ожидаете смену ключа — не продолжайте: соединение может перехватываться (атака посредника). " +
                        "Доверяйте новому ключу только после независимой проверки его отпечатка у администратора VPS."
            )
        },
        confirmButton = { Button(onClick = onTrust) { Text("Доверять новому ключу") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
