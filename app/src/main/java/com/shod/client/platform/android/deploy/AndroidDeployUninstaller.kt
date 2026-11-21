package com.shod.client.platform.android.deploy

import android.content.Context
import com.shod.client.deploy.execution.isSuccessfulUninstallResult
import com.shod.client.deploy.execution.rootCommand
import com.shod.client.deploy.execution.uninstallFailureMessage
import com.shod.client.deploy.script.writeDeployScript
import com.shod.client.platform.android.deploy.ssh.AndroidDeploySshSession
import com.shod.client.platform.android.deploy.ssh.AndroidSshClientFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of the server-side uninstall; UI state remains the caller's responsibility. */
internal data class AndroidDeployUninstallResult(
    val successful: Boolean,
    val exitStatus: Int,
    val output: String,
    val failureMessage: String?,
)

/**
 * Executes the archive's supported deploy.sh uninstall action over the
 * refactored SSH session. No application-wide manager is accessed here.
 */
internal class AndroidDeployUninstaller(
    context: Context,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onInfo: (String) -> Unit = {},
    private val onSuccess: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext

    suspend fun uninstall(
        host: String,
        user: String,
        password: String,
        port: Int,
        peerPort: Int,
        privateKey: String = "",
        keyPassphrase: String = "",
        certificate: String = "",
    ): AndroidDeployUninstallResult = withContext(Dispatchers.IO) {
        var script: File? = null
        var session: AndroidDeploySshSession? = null
        try {
            onProgress(0.05f, "Подключение…")
            onInfo("SSH/SFTP используют текущий системный маршрут")
            onInfo("Подключение к VPS по SSH")

            val factory = AndroidSshClientFactory(
                context = appContext,
                onAudit = onError,
                onRetry = onInfo,
            )
            session = AndroidDeploySshSession(
                connect = {
                    factory.create(
                        host = host,
                        user = user,
                        password = password,
                        port = port,
                        privateKey = privateKey,
                        keyPassphrase = keyPassphrase,
                        certificate = certificate,
                    )
                },
                sudoPassword = password,
                rootCommand = { command -> rootCommand(command, password) },
                onInfo = onInfo,
                onSuccess = onSuccess,
                onError = onError,
            )
            onSuccess("SSH-соединение установлено")

            onProgress(0.15f, "Подготовка штатного удаления…")
            onInfo("Загрузка штатного установщика Shod")
            script = File(appContext.cacheDir, "shod-uninstall-${System.nanoTime()}.sh")
            writeDeployScript(appContext, script)
            if (!script.isFile || script.length() == 0L) {
                throw IOException("Не удалось подготовить штатный установщик Shod")
            }
            session.upload(script, "/tmp/deploy.sh")

            onProgress(0.30f, "Удаление через deploy.sh…")
            onInfo("Запуск серверного удаления")
            val result = session.execute(
                rootCommand(
                    "env Shod_PEER_PORT=$peerPort Shod_SSH_PORT=$port bash /tmp/deploy.sh uninstall",
                    password,
                ),
                UNINSTALL_TIMEOUT_MS,
            )
            if (isSuccessfulUninstallResult(result.exitStatus, result.output)) {
                onProgress(1f, "Готово!")
                onSuccess("Удаление Shod завершено")
                AndroidDeployUninstallResult(
                    successful = true,
                    exitStatus = result.exitStatus,
                    output = result.output,
                    failureMessage = null,
                )
            } else {
                val failure = uninstallFailureMessage(result.output)
                onError(
                    "Uninstall failed: exit=${result.exitStatus}\n" +
                        result.output.takeLast(1200),
                )
                AndroidDeployUninstallResult(
                    successful = false,
                    exitStatus = result.exitStatus,
                    output = result.output,
                    failureMessage = failure,
                )
            }
        } finally {
            runCatching { session?.close() }
            runCatching { script?.delete() }
        }
    }

    private companion object {
        const val UNINSTALL_TIMEOUT_MS = 120_000L
    }
}
