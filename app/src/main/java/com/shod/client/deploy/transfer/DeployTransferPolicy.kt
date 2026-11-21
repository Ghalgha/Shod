package com.shod.client.deploy.transfer

/**
 * Transfer parameters preserved from the legacy DeploySSHClient implementation.
 *
 * Kept independent from SSHJ and Android so the transfer adapter can be migrated
 * without bringing UI or platform dependencies into the deploy domain.
 */
internal const val SFTP_UPLOAD_ATTEMPTS = 2
internal const val UPLOAD_RECONNECT_ATTEMPTS = 1
internal const val SFTP_RETRY_DELAY_MS = 500L
internal const val STREAM_UPLOAD_CHUNK_SIZE_BYTES = 64 * 1024

/**
 * SFTP success is accepted only when the remote file size exactly matches
 * the local file size. A successful put alone is not sufficient.
 */
internal fun isUploadSizeVerified(localSize: Long, remoteSize: Long): Boolean =
    localSize >= 0L && remoteSize == localSize

/**
 * Whether another SFTP attempt is permitted by the legacy retry policy.
 * [completedAttempts] is one-based, matching the attempt number in logs.
 */
internal fun shouldRetrySftpUpload(
    completedAttempts: Int,
    connected: Boolean,
): Boolean = connected && completedAttempts in 1 until SFTP_UPLOAD_ATTEMPTS
