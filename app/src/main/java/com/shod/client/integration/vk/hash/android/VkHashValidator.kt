package com.shod.client.integration.vk.hash.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import com.shod.client.ShodConstants
import com.shod.client.integration.vk.hash.codec.VkHashValidationCodec
import com.shod.client.integration.vk.hash.codec.VkHashValidationStatus

object VkHashValidator {
    suspend fun check(
        context: Context,
        hashes: List<String>,
        fingerprint: String,
        clientIds: String,
    ): Map<String, VkHashValidationStatus> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) return@withContext emptyMap()
        val binary = File(
            context.applicationInfo.nativeLibraryDir,
            ShodConstants.Tunnel.BINARY_NAME,
        )
        if (!binary.isFile) return@withContext emptyMap()
        val command = VkHashValidationCodec.buildProcessCommand(binary.absolutePath, hashes, fingerprint, clientIds)
        val process = runCatching {
            ProcessBuilder(command)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
                }
                .start()
        }.getOrNull() ?: return@withContext emptyMap()
        val completed = runCatching { process.waitFor(75, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!completed) {
            process.destroyForcibly()
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
            return@withContext emptyMap()
        }
        val requested = hashes.toSet()
        runCatching {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull(VkHashValidationCodec::parseProcessOutputLine)
                    .filter { it.first in requested }
                    .toMap()
            }
        }.getOrDefault(emptyMap())
    }

}
