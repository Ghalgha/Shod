package com.shod.client.platform.android.deploy.ssh

import com.shod.client.deploy.execution.DeployProgressEvent
import com.shod.client.deploy.ssh.SshRemoteCommandExecutor
import com.shod.client.deploy.ssh.SshRemoteCommandResult
import com.shod.client.deploy.transfer.SshDeployFileTransfer
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import net.schmizz.sshj.SSHClient

/**
 * Owns one authenticated deployment SSH session and composes the transport
 * adapters used by the deployment workflow.
 *
 * Reconnection replaces the session atomically from the workflow's point of
 * view; command execution and file transfer always use the current client.
 */
internal class AndroidDeploySshSession(
    private val connect: () -> SSHClient,
    private val sudoPassword: String,
    private val rootCommand: (String) -> String,
    private val onProgress: (DeployProgressEvent) -> Unit = {},
    private val onStdout: (String) -> Unit = {},
    private val onStderr: (String) -> Unit = {},
    private val onInfo: (String) -> Unit = {},
    private val onSuccess: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) : AutoCloseable {
    @Volatile
    private var ssh: SSHClient = connect()

    init {
        activeSessions.add(this)
    }

    fun execute(command: String, timeoutMs: Long): SshRemoteCommandResult {
        ensureConnected()
        return SshRemoteCommandExecutor(
            ssh = ssh,
            sudoPassword = sudoPassword,
            onProgress = onProgress,
            onStdout = onStdout,
            onStderr = onStderr,
            onError = onError,
        ).execute(command, timeoutMs)
    }

    fun upload(file: File, remotePath: String, onTransferProgress: (Float, String) -> Unit = { _, _ -> }) {
        ensureConnected()
        val transfer = SshDeployFileTransfer(
            sshProvider = { ssh },
            reconnect = { reconnect() },
            executeRemote = ::execute,
            rootCommand = rootCommand,
            onProgress = onTransferProgress,
            onInfo = onInfo,
            onSuccess = onSuccess,
            onError = onError,
        )
        transfer.upload(file, remotePath)
    }

    fun isConnected(): Boolean = ssh.isConnected

    private fun ensureConnected() {
        if (!ssh.isConnected && reconnect() == null) {
            throw IOException("SSH session is disconnected and reconnection failed")
        }
    }

    private fun reconnect(): SSHClient? = try {
        val replacement = connect()
        val previous = ssh
        ssh = replacement
        runCatching { previous.disconnect() }
        onSuccess("SSH-соединение восстановлено")
        replacement
    } catch (error: Exception) {
        onError("SSH reconnect failed: ${error.message ?: error.javaClass.simpleName}")
        null
    }

    override fun close() {
        activeSessions.remove(this)
        runCatching { ssh.disconnect() }
    }

    companion object {
        private val activeSessions = ConcurrentHashMap.newKeySet<AndroidDeploySshSession>()

        fun closeActiveSessions() {
            activeSessions.toList().forEach { it.close() }
        }
    }
}
