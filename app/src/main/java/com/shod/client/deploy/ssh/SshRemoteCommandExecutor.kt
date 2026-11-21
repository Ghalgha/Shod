package com.shod.client.deploy.ssh

import com.shod.client.deploy.execution.DeployProgressEvent
import com.shod.client.deploy.execution.isBenignDeployStderr
import com.shod.client.deploy.execution.parseDeployProgressEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient

internal data class SshRemoteCommandResult(
    val output: String,
    val exitStatus: Int,
)

/**
 * Executes one remote command and collects both SSH streams.
 *
 * UI state, logging, reconnect policy and deployment orchestration stay outside
 * this transport adapter and are supplied through callbacks.
 */
internal class SshRemoteCommandExecutor(
    private val ssh: SSHClient,
    private val sudoPassword: String,
    private val onProgress: (DeployProgressEvent) -> Unit = {},
    private val onStdout: (String) -> Unit = {},
    private val onStderr: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    fun execute(
        command: String,
        timeoutMs: Long,
    ): SshRemoteCommandResult {
        if (!ssh.isConnected) {
            onError("SSH exec: client disconnected before command: ${command.take(80)}")
            return SshRemoteCommandResult("error: session is down", -1)
        }

        val adjustedCommand = if (command.contains("sudo") && !command.contains("sudo -S")) {
            command.replace("sudo ", "sudo -S ")
        } else {
            command
        }

        val session = try {
            ssh.startSession()
        } catch (error: Exception) {
            onError("SSH session creation failed: ${error.message} | cmd: ${command.take(80)}")
            return SshRemoteCommandResult("error: ${error.message}", -1)
        }

        return try {
            val remoteCommand = session.exec(adjustedCommand)
            if (adjustedCommand.contains("sudo -S")) {
                runCatching {
                    remoteCommand.outputStream.write("$sudoPassword\n".toByteArray())
                    remoteCommand.outputStream.flush()
                }
            }

            val output = StringBuilder()
            val stdoutDone = CountDownLatch(1)
            val stderrDone = CountDownLatch(1)

            Thread({
                try {
                    remoteCommand.inputStream.bufferedReader().forEachLine { rawLine ->
                        val line = rawLine.replace(ANSI_ESCAPE, "")
                        val progress = parseDeployProgressEvent(line)
                        if (progress != null) {
                            onProgress(progress)
                        } else if (!line.contains("Shod_PROGRESS")) {
                            synchronized(output) { output.appendLine(line) }
                            onStdout(line)
                        }
                    }
                } catch (error: Exception) {
                    onError("SSH stdout read failed: ${error.message}")
                } finally {
                    stdoutDone.countDown()
                }
            }, "deploy-stdout").apply {
                isDaemon = true
                start()
            }

            Thread({
                try {
                    remoteCommand.errorStream.bufferedReader().forEachLine { rawLine ->
                        if (!rawLine.contains("password for")) {
                            val line = rawLine.replace(ANSI_ESCAPE, "")
                            if (line.isNotBlank() && !isBenignDeployStderr(line)) {
                                synchronized(output) { output.appendLine(line) }
                                onStderr(line)
                            }
                        }
                    }
                } catch (error: Exception) {
                    onError("SSH stderr read failed: ${error.message}")
                } finally {
                    stderrDone.countDown()
                }
            }, "deploy-stderr").apply {
                isDaemon = true
                start()
            }

            val stdoutFinished = stdoutDone.await(timeoutMs, TimeUnit.MILLISECONDS)
            stderrDone.await(2, TimeUnit.SECONDS)

            if (!stdoutFinished) {
                onError("SSH timeout (${timeoutMs / 1000}s): ${command.take(80)}")
                return SshRemoteCommandResult("error: timeout", -1)
            }

            remoteCommand.join(3, TimeUnit.SECONDS)
            SshRemoteCommandResult(output.toString(), remoteCommand.exitStatus ?: -1)
        } catch (error: Exception) {
            onError("SSH exec error: ${error.message} | cmd: ${command.take(80)}")
            SshRemoteCommandResult("error: ${error.message}", -1)
        } finally {
            runCatching { session.close() }
        }
    }

    private companion object {
        val ANSI_ESCAPE = Regex("\u001B\\[[;\\d]*m")
    }
}
