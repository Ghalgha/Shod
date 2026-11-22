package com.shod.client.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shod.client.platform.android.deploy.ssh.SshHostKeyStore
import com.shod.client.ui.components.appSectionCardBorderColor
import com.shod.client.ui.components.appSectionCardColor
import com.shod.client.ui.design.ShodShapes
import com.shod.client.ui.design.ShodSizes

/** Explicitly asks the user to verify a changed SSH host key before trusting it. */
@Composable
internal fun HostKeyChangedDialog(
    change: SshHostKeyStore.KeyChange,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = ShodShapes.Dialog,
            color = appSectionCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, appSectionCardBorderColor()),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("SSH-ключ сервера изменился", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(
                    "Сервер ${change.host}:${change.port} предъявил другой SSH-ключ, чем при первом подключении. " +
                        "Так бывает после переустановки VPS или если этот адрес теперь у другого сервера.\n\n" +
                        "Если вы VPS не переустанавливали — не продолжайте: соединение может перехватываться.",
                    style = MaterialTheme.typography.bodySmall, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Fingerprint("Был", change.pinned)
                Fingerprint("Сейчас", change.presented)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = ShodSizes.ControlHeight),
                        shape = ShodShapes.Pill,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text("Отмена", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = onTrust, modifier = Modifier.weight(1f).heightIn(min = ShodSizes.ControlHeight),
                        shape = ShodShapes.Pill, border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Доверять новому ключу", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fingerprint(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
    }
}
