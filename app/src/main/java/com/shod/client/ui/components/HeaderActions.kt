package com.shod.client.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shod.client.R
import com.shod.client.SettingsStore
import com.shod.client.ui.dialogs.ImportExportDialog
import com.shod.client.ui.dialogs.QrScannerDialog
import com.shod.client.links.parseShodLink
import kotlinx.coroutines.launch

@Composable
fun HeaderActions(
    settingsStore: SettingsStore,
    shodLinkMode: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    var showImportExportDialog by rememberSaveable { mutableStateOf(false) }
    val iconTint = MaterialTheme.colorScheme.onSurface

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showQrScanner = true
        else Toast.makeText(context, "Требуется разрешение на камеру для сканирования QR-кода", Toast.LENGTH_SHORT).show()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        if (!shodLinkMode) {
            IconButton(onClick = { showImportExportDialog = true }, modifier = Modifier.size(38.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_import_export),
                    contentDescription = "Импорт и экспорт конфигурации",
                    modifier = Modifier.size(24.dp),
                    tint = iconTint,
                )
            }
        }
        IconButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    showQrScanner = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_qr_scan),
                contentDescription = "Сканировать QR-код",
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(38.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_gear),
                contentDescription = stringResource(R.string.quick_settings),
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
        }
    }

    if (showImportExportDialog) {
        ImportExportDialog(settingsStore = settingsStore, onDismissRequest = { showImportExportDialog = false })
    }
    if (showQrScanner) {
        QrScannerDialog(
            onDismissRequest = { showQrScanner = false },
            onQrCodeScanned = { rawLink ->
                if (parseShodLink(rawLink) != null) {
                    showQrScanner = false
                    scope.launch { settingsStore.saveShodParticipantLink(rawLink.trim()) }
                    Toast.makeText(context, "Ссылка подключена из QR-кода ✓ Режим участника включён", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "QR-код не содержит ссылку Shod", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
