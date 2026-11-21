package com.shod.client.platform.android.deploy

import android.content.Context
import com.shod.client.deploy.execution.DeployActionMode
import com.shod.client.deploy.inventory.ServerInventory
import com.shod.client.platform.android.deploy.ssh.AndroidDeploySshSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Application-facing entry point for server deployment operations.
 * The active coroutine is registered so the foreground-service notification
 * can cancel the actual SSH operation, not only update UI state.
 */
internal class AndroidDeployCoordinator(
    context: Context,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onInfo: (String) -> Unit = {},
    private val onSuccess: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private val appContext = context.applicationContext

    suspend fun inspectServer(
        host: String,
        user: String,
        password: String,
        sshPort: Int,
        privateKey: String = "",
        keyPassphrase: String = "",
        certificate: String = "",
    ): ServerInventory = trackOperation {
        AndroidDeployServerCheck(
            context = appContext,
            onProgress = onProgress,
            onInfo = onInfo,
            onSuccess = onSuccess,
            onError = onError,
        ).check(
            host = host,
            user = user,
            password = password,
            port = sshPort,
            privateKey = privateKey,
            keyPassphrase = keyPassphrase,
            certificate = certificate,
        )
    }

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
    ): AndroidDeployWorkflowResult = trackOperation {
        AndroidDeployWorkflow(
            context = appContext,
            onProgress = onProgress,
            onInfo = onInfo,
            onSuccess = onSuccess,
            onError = onError,
        ).deploy(
            host = host,
            user = user,
            password = password,
            sshPort = sshPort,
            mainPassword = mainPassword,
            webLogin = webLogin,
            webPassword = webPassword,
            peerPort = peerPort,
            webPort = webPort,
            privateKey = privateKey,
            keyPassphrase = keyPassphrase,
            certificate = certificate,
            installInDocker = installInDocker,
            secretPath = secretPath,
            mode = mode,
        )
    }

    suspend fun uninstall(
        host: String,
        user: String,
        password: String,
        sshPort: Int,
        peerPort: Int,
        privateKey: String = "",
        keyPassphrase: String = "",
        certificate: String = "",
    ): AndroidDeployUninstallResult = trackOperation {
        AndroidDeployUninstaller(
            context = appContext,
            onProgress = onProgress,
            onInfo = onInfo,
            onSuccess = onSuccess,
            onError = onError,
        ).uninstall(
            host = host,
            user = user,
            password = password,
            port = sshPort,
            peerPort = peerPort,
            privateKey = privateKey,
            keyPassphrase = keyPassphrase,
            certificate = certificate,
        )
    }

    private suspend fun <T> trackOperation(block: suspend () -> T): T {
        val job = currentCoroutineContext()[Job]
            ?: error("Deploy operation has no coroutine job")
        activeOperation.set(job)
        return try {
            block()
        } finally {
            activeOperation.compareAndSet(job, null)
        }
    }

    companion object {
        private val activeOperation = AtomicReference<Job?>(null)

        fun hasActiveOperation(): Boolean = activeOperation.get()?.isActive == true

        fun cancelActiveOperation() {
            AndroidDeploySshSession.closeActiveSessions()
            activeOperation.get()?.cancel()
        }
    }
}
