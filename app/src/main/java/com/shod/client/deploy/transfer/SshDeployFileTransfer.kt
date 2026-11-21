package com.shod.client.deploy.transfer

import android.util.Base64
import com.shod.client.deploy.ssh.SshRemoteCommandResult
import java.io.File
import java.io.IOException
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.xfer.FileSystemFile

/**
 * Uploads deployment assets over an established SSH transport.
 *
 * The caller owns connection creation, remote command execution, logging and
 * deployment progress presentation. This service owns only SFTP/SCP/stream
 * transfer mechanics and the existing retry policy.
 */
internal class SshDeployFileTransfer(
    private val sshProvider: () -> SSHClient,
    private val reconnect: (() -> SSHClient?)? = null,
    private val executeRemote: (String, Long) -> SshRemoteCommandResult,
    private val rootCommand: (String) -> String,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onInfo: (String) -> Unit = {},
    private val onSuccess: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private var ssh: SSHClient = sshProvider()

    fun upload(localFile: File, remotePath: String) {
        upload(localFile, remotePath, UPLOAD_RECONNECT_ATTEMPTS)
    }

    private fun upload(localFile: File, remotePath: String, reconnectsLeft: Int) {
        if (!localFile.isFile || !localFile.canRead()) {
            throw IOException("Local deploy file is unavailable: ${localFile.name}")
        }
        if (!ssh.isConnected) {
            val replacement = reconnect?.invoke()
                ?: throw IOException("SSH client disconnected before upload: ${localFile.name}")
            ssh = replacement
        }

        val label = assetLabel(localFile.name)
        onInfo("Загрузка: $label (${readableFileSize(localFile.length())})")

        var lastError = "SFTP channel did not accept the file"
        var uploaded = false
        for (attempt in 1..SFTP_UPLOAD_ATTEMPTS) {
            try {
                ssh.newSFTPClient().use { sftp ->
                    sftp.put(FileSystemFile(localFile), remotePath)
                    val remoteSize = sftp.lstat(remotePath).size
                    if (isUploadSizeVerified(localFile.length(), remoteSize)) {
                        onSuccess("Загружено: $label")
                        uploaded = true
                    } else {
                        lastError = "remote size mismatch (local=${localFile.length()}, remote=$remoteSize)"
                    }
                }
            } catch (error: Exception) {
                lastError = "SFTP: ${error.message ?: error.javaClass.simpleName}"
                onError("SFTP upload attempt $attempt/$SFTP_UPLOAD_ATTEMPTS failed for ${localFile.name}: $lastError")
            }
            if (uploaded) return
            if (shouldRetrySftpUpload(attempt, ssh.isConnected)) {
                Thread.sleep(SFTP_RETRY_DELAY_MS)
            }
        }

        if (ssh.isConnected) {
            try {
                onInfo("SFTP не завершился успешно; пробуем SCP для $label")
                ssh.newSCPFileTransfer().upload(FileSystemFile(localFile), remotePath)
                onSuccess("Загружено (SCP): $label")
                return
            } catch (error: Exception) {
                lastError = "SCP: ${error.message ?: error.javaClass.simpleName}"
                onError("SCP upload failed for ${localFile.name}: $lastError")
            }
        }

        if (ssh.isConnected) {
            try {
                uploadByStream(localFile, remotePath, label)
                onSuccess("Загружено (Stream): $label")
                return
            } catch (error: Exception) {
                lastError = "stream: ${error.message ?: error.javaClass.simpleName}"
                onError("Stream upload failed for ${localFile.name}: $lastError")
            }
        }

        if (reconnectsLeft > 0) {
            val replacement = reconnect?.invoke()
            if (replacement != null) {
                ssh = replacement
                upload(localFile, remotePath, reconnectsLeft - 1)
                return
            }
        }

        throw IOException("SFTP/SCP upload failed for ${localFile.name}: $lastError")
    }

    private fun uploadByStream(localFile: File, remotePath: String, label: String) {
        val quotedTemporaryPath = shellQuote("$remotePath.tmp")
        val quotedRemotePath = shellQuote(remotePath)
        executeChecked(rootCommand("rm -f $quotedTemporaryPath"), 30_000L)

        localFile.inputStream().buffered(STREAM_UPLOAD_CHUNK_SIZE_BYTES).use { input ->
            val buffer = ByteArray(STREAM_UPLOAD_CHUNK_SIZE_BYTES)
            var totalUploaded = 0L
            var bytesRead: Int
            val totalLength = localFile.length().coerceAtLeast(1L)
            while (input.read(buffer).also { bytesRead = it } != -1) {
                val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                val encoded = Base64.encodeToString(chunk, Base64.NO_WRAP)
                val command = "printf '%s' '$encoded' | base64 -d >> $quotedTemporaryPath"
                executeChecked(rootCommand(command), 60_000L)
                totalUploaded += bytesRead
                val fraction = (totalUploaded.toFloat() / totalLength).coerceIn(0f, 1f)
                onProgress(fraction * 0.5f, "Загрузка: $label (${(fraction * 100).toInt()}%)")
            }
        }

        executeChecked(
            rootCommand("mv -f $quotedTemporaryPath $quotedRemotePath && chmod 0755 $quotedRemotePath"),
            30_000L,
        )
    }

    private fun executeChecked(command: String, timeoutMs: Long) {
        val result = executeRemote(command, timeoutMs)
        if (result.exitStatus != 0) {
            throw IOException("Remote transfer command failed (exit=${result.exitStatus}): ${result.output.take(240)}")
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun assetLabel(fileName: String): String = when (fileName) {
        "deploy.sh" -> "скрипт установки"
        "shod-linux-amd64" -> "сервер Shod (amd64)"
        "shod-linux-arm64" -> "сервер Shod (ARM64)"
        "shod-linux-armv7" -> "сервер Shod (ARM32)"
        "shod.env" -> "настройки веб-панели"
        "shod-deploy.json" -> "настройки доступа"
        "hev-socks5-tunnel" -> "C-движок туннеля"
        else -> fileName
    }

    private fun readableFileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f КБ", bytes / 1024.0)
        else -> "$bytes Б"
    }
}
