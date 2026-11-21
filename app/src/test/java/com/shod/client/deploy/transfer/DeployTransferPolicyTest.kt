package com.shod.client.deploy.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployTransferPolicyTest {

    @Test
    fun preservesLegacyTransferLimits() {
        assertEquals(2, SFTP_UPLOAD_ATTEMPTS)
        assertEquals(1, UPLOAD_RECONNECT_ATTEMPTS)
        assertEquals(500L, SFTP_RETRY_DELAY_MS)
        assertEquals(64 * 1024, STREAM_UPLOAD_CHUNK_SIZE_BYTES)
    }

    @Test
    fun acceptsOnlyExactRemoteFileSize() {
        assertTrue(isUploadSizeVerified(1024L, 1024L))
        assertFalse(isUploadSizeVerified(1024L, 1023L))
        assertFalse(isUploadSizeVerified(1024L, 1025L))
        assertFalse(isUploadSizeVerified(-1L, -1L))
    }

    @Test
    fun retriesOnlyBeforeLastAttemptWhileConnected() {
        assertTrue(shouldRetrySftpUpload(completedAttempts = 1, connected = true))
        assertFalse(shouldRetrySftpUpload(completedAttempts = 2, connected = true))
        assertFalse(shouldRetrySftpUpload(completedAttempts = 1, connected = false))
        assertFalse(shouldRetrySftpUpload(completedAttempts = 0, connected = true))
    }
}
