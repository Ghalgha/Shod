package com.shod.client.platform.android.deploy

import android.content.Context
import com.shod.client.TunnelManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android-side diagnostics and lifecycle support for deployment operations.
 * UI state stays in DeployController; durable diagnostics stay outside Compose.
 */
internal object DeployDiagnostics {
    private const val MAX_LOG_BYTES = 500_000L
    private const val RETAIN_LOG_BYTES = 200_000

    @Volatile
    private var errorsFile: File? = null

    fun initialize(context: Context) {
        val directory = context.applicationContext.getExternalFilesDir(null)
            ?: context.applicationContext.filesDir
        errorsFile = File(directory, "errors.log")
    }

    @Synchronized
    fun recordError(message: String, throwable: Throwable? = null) {
        val detail = buildString {
            append(message)
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString().take(1200))
            }
        }
        TunnelManager.addDeployErrorLog(message)
        val file = errorsFile ?: return
        runCatching {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())
            file.appendText("[$timestamp] $detail\n")
            if (file.length() > MAX_LOG_BYTES) {
                file.writeText(file.readText().takeLast(RETAIN_LOG_BYTES))
            }
        }
    }

    fun begin() {
        TunnelManager.clearLogs()
    }

    fun forceReset(reason: String) {
        recordError(reason)
        AndroidDeployCoordinator.cancelActiveOperation()
    }
}
