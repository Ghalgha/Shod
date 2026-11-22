package com.shod.client.platform.android.deploy

import android.content.Context
import com.shod.client.ShodConstants
import com.shod.client.deploy.artifact.DeployArtifactStager
import com.shod.client.deploy.artifact.DeployConfigurationStager
import com.shod.client.deploy.execution.DeployActionMode
import com.shod.client.deploy.execution.DeployOutputLevel
import com.shod.client.deploy.execution.parseDeployOutputLine
import com.shod.client.deploy.execution.deployMode
import com.shod.client.deploy.execution.deployFailureMessage
import com.shod.client.deploy.execution.friendlyDeployError
import com.shod.client.deploy.execution.isSuccessfulDeployResult
import com.shod.client.deploy.execution.rootCommand
import com.shod.client.deploy.execution.serverArchitectureForMachine
import com.shod.client.deploy.script.writeDeployScript
import com.shod.client.platform.android.deploy.ssh.AndroidDeploySshSession
import com.shod.client.platform.android.deploy.ssh.AndroidSshClientFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Outcome of one install, update or reinstall transaction. */
internal data class AndroidDeployWorkflowResult(
    val successful: Boolean,
    val exitStatus: Int? = null,
    val output: String = "",
    val failureMessage: String? = null,
)

/**
 * Executes the deploy.sh install/update flow using the extracted Android SSH,
 * artifact and configuration adapters. UI state and application-wide managers
 * remain outside this workflow.
 */
internal class AndroidDeployWorkflow(
    context: Context,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onInfo: (String) -> Unit = {},
    private val onSuccess: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext

    suspend fun deploy(
        host: String,
        user: String,
        password: String,
        sshPort: Int,
        mainPassword: String,
        webLogin: String,
        webPassword: String,
        peerPort: Int,
        webPort: Int,
        privateKey: String = "",
        keyPassphrase: String = "",
        certificate: String = "",
        installInDocker: Boolean = false,
        secretPath: String = "",
        mode: DeployActionMode = DeployActionMode.Install,
    ): AndroidDeployWorkflowResult = withContext(Dispatchers.IO) {
        var session: AndroidDeploySshSession? = null
        var workingDirectory: File? = null
        try {
            onProgress(0.02f, "Подключение…")
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
                        port = sshPort,
                        privateKey = privateKey,
                        keyPassphrase = keyPassphrase,
                        certificate = certificate,
                    )
                },
                sudoPassword = password,
                rootCommand = { command -> rootCommand(command, password) },
                onProgress = { event -> onProgress(event.fraction, event.message) },
                onStdout = { rawLine ->
                    parseDeployOutputLine(rawLine)?.let { line ->
                        when (line.level) {
                            DeployOutputLevel.LOG -> onInfo(line.message)
                            DeployOutputLevel.OK -> onSuccess(line.message)
                            DeployOutputLevel.ERR -> onError("Сервер: ${line.message}")
                        }
                    }
                },
                onStderr = { rawLine ->
                    onError("Сервер: ${parseDeployOutputLine(rawLine)?.message ?: rawLine.trim().take(500)}")
                },
                onInfo = onInfo,
                onSuccess = onSuccess,
                onError = onError,
            )
            onSuccess("SSH-соединение установлено")

            val architectureResult = session.execute("uname -m", 10_000L)
            if (architectureResult.exitStatus != 0) {
                throw IOException(
                    "Не удалось определить архитектуру VPS: " +
                        architectureResult.output.trim().take(160)
                )
            }
            val architecture = serverArchitectureForMachine(architectureResult.output)
            onInfo("Архитектура VPS: ${architecture.displayName}")

            onProgress(0.05f, "Подготовка файлов…")
            val directory = File(appContext.cacheDir, "deploy-${System.nanoTime()}")
            workingDirectory = directory
            if (!directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Не удалось создать временный каталог установки")
            }

            val scriptFile = File(directory, "deploy.sh").also {
                writeDeployScript(appContext, it)
            }
            if (!scriptFile.isFile || scriptFile.length() == 0L) {
                throw IOException("Не удалось подготовить скрипт установки")
            }

            val jobContext = currentCoroutineContext()
            val serverFile = DeployArtifactStager(appContext).stageServerCore(
                assetName = architecture.assetName,
                target = File(directory, architecture.assetName),
                checkCancelled = { jobContext.ensureActive() },
                onProgress = onProgress,
                onVerified = { onSuccess("Серверное ядро проверено") },
            )
            val configuration = DeployConfigurationStager().stage(
                workingDirectory = directory,
                mode = mode,
                installInDocker = installInDocker,
                webLogin = webLogin,
                webPassword = webPassword,
                publicHost = host,
                secretPath = secretPath,
                mainPassword = mainPassword,
            )
            onSuccess("Файлы установки подготовлены")

            onProgress(0.06f, "Подготовка сервера…")
            session.upload(scriptFile, "/tmp/deploy.sh")

            if (mode == DeployActionMode.Reinstall) {
                onProgress(0.07f, "Очистка сервера…")
                onInfo("Очистка Shod, SQLite-базы и конфигурации на VPS")
                val purge = session.execute(
                    rootCommand(
                        "env Shod_PEER_PORT=$peerPort Shod_SSH_PORT=$sshPort " +
                            "Shod_WEB_PORT=$webPort bash /tmp/deploy.sh purge",
                        password,
                    ),
                    DEPLOY_COMMAND_TIMEOUT_MS,
                )
                if (purge.exitStatus != 0) {
                    throw IOException(
                        "Очистка сервера завершилась ошибкой (код ${purge.exitStatus}): " +
                            purge.output.takeLast(240)
                    )
                }
                onSuccess("Сервер очищен, начинается чистая установка")
            }

            val secretEnvironment = if (secretPath.isNotBlank()) {
                "Shod_WEB_SECRET_PATH=" +
                    com.shod.client.deploy.execution.deployEnvironmentValue(secretPath.trim(), false) + " "
            } else {
                ""
            }
            val deployEnvironment =
                "env ${secretEnvironment}Shod_PEER_PORT=$peerPort Shod_SSH_PORT=$sshPort " +
                    "Shod_WEB_PORT=$webPort Shod_PUBLIC_IP=${host.trim()} " +
                    "Shod_DEPLOY_MODE=${deployMode(installInDocker)}"

            onProgress(0.10f, "Загрузка нового сервера…")
            session.upload(serverFile, "/tmp/.shod-upload-server")
            configuration.environment?.let {
                session.upload(it, "/tmp/.shod-upload-web.env")
            }
            configuration.overrides?.let {
                session.upload(it, "/tmp/.shod-upload-overrides.json")
            }

            onProgress(
                0.18f,
                if (mode == DeployActionMode.Update) "Установка обновления…" else "Установка нового сервера…",
            )
            onInfo(
                when (mode) {
                    DeployActionMode.Update -> "Запуск обновления Shod на VPS (конфигурация сохраняется)"
                    DeployActionMode.Reinstall -> "Запуск чистой установки Shod на VPS"
                    DeployActionMode.Install -> "Запуск установщика на VPS"
                }
            )
            val result = session.execute(
                rootCommand("$deployEnvironment bash /tmp/deploy.sh ${mode.scriptAction}", password),
                DEPLOY_COMMAND_TIMEOUT_MS,
            )
            if (isSuccessfulDeployResult(result.exitStatus, result.output)) {
                val label = when (mode) {
                    DeployActionMode.Update -> "Обновление"
                    DeployActionMode.Reinstall -> "Переустановка"
                    DeployActionMode.Install -> "Установка"
                }
                onSuccess(
                    if (installInDocker) "$label завершена · Docker-контейнер Shod активен"
                    else "$label завершена · служба Shod активна"
                )
                AndroidDeployWorkflowResult(
                    successful = true,
                    exitStatus = result.exitStatus,
                    output = result.output,
                )
            } else {
                val failure = deployFailureMessage(result.exitStatus, result.output)
                onError(
                    "Deploy failed: exit=${result.exitStatus}\n" +
                        result.output.takeLast(1200)
                )
                onError(failure)
                AndroidDeployWorkflowResult(
                    successful = false,
                    exitStatus = result.exitStatus,
                    output = result.output,
                    failureMessage = failure,
                )
            }
        } catch (cancelled: CancellationException) {
            onInfo("Установка отменена пользователем")
            throw cancelled
        } catch (error: Exception) {
            val failure = friendlyDeployError(error.message)
            onError(
                "Deploy error (${error.javaClass.simpleName}): ${error.message}\n" +
                    error.stackTraceToString().take(1200)
            )
            onError(failure)
            AndroidDeployWorkflowResult(
                successful = false,
                failureMessage = failure,
            )
        } finally {
            runCatching { session?.close() }
            runCatching { workingDirectory?.deleteRecursively() }
        }
    }

    private companion object {
        const val DEPLOY_COMMAND_TIMEOUT_MS = ShodConstants.Timeouts.DEPLOY_CMD_TIMEOUT_MS
    }
}
