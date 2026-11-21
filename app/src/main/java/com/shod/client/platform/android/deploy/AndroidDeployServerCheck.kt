package com.shod.client.platform.android.deploy

import android.content.Context
import com.shod.client.deploy.execution.DeployProgressEvent
import com.shod.client.deploy.execution.rootCommand
import com.shod.client.deploy.inventory.ServerInventory
import com.shod.client.deploy.inventory.parseServerInventory
import com.shod.client.deploy.script.writeDeployScript
import com.shod.client.platform.android.deploy.ssh.AndroidDeploySshSession
import com.shod.client.platform.android.deploy.ssh.AndroidSshClientFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Performs the read-only inventory operation using the shared deployment SSH
 * session. UI state and presentation remain the caller's responsibility.
 */
internal class AndroidDeployServerCheck(
    context: Context,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onInfo: (String) -> Unit = {},
    private val onSuccess: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext

    suspend fun check(
        host: String,
        user: String,
        password: String,
        port: Int,
        privateKey: String = "",
        keyPassphrase: String = "",
        certificate: String = "",
    ): ServerInventory = withContext(Dispatchers.IO) {
        var script: File? = null
        var session: AndroidDeploySshSession? = null
        try {
            onProgress(0.3f, "Проверка сервера…")
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
                onProgress = { event: DeployProgressEvent ->
                    onProgress(event.fraction, event.message)
                },
                onInfo = onInfo,
                onSuccess = onSuccess,
                onError = onError,
            )
            onSuccess("SSH-соединение установлено")

            script = File(appContext.cacheDir, "shod-check-${System.nanoTime()}.sh")
            writeDeployScript(appContext, script)
            if (!script.isFile || script.length() == 0L) {
                throw IOException("Не удалось подготовить скрипт проверки сервера")
            }

            session.upload(script, "/tmp/deploy.sh")
            onProgress(0.6f, "Проверка сервера…")
            val result = session.execute(
                rootCommand("bash /tmp/deploy.sh inventory", password),
                SERVER_CHECK_TIMEOUT_MS,
            )
            val inventory = parseServerInventory(result.output)
            if (result.exitStatus != 0 || inventory == null) {
                throw IOException(
                    "Проверка сервера не удалась (код ${result.exitStatus}); " +
                        "ответ сервера неполон или не прошёл проверку",
                )
            }

            onProgress(1f, "Проверка завершена")
            onSuccess("Проверка сервера завершена")
            inventory
        } catch (error: Exception) {
            onError("Server check failed: ${error.message ?: error.javaClass.simpleName}")
            throw error
        } finally {
            runCatching { session?.close() }
            runCatching { script?.delete() }
        }
    }

    private companion object {
        const val SERVER_CHECK_TIMEOUT_MS = 90_000L
    }
}
