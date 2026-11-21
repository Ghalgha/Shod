package com.shod.client.integration.vk.hash.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import com.shod.client.ShodConstants
import com.shod.client.integration.vk.hash.codec.VkHashValidationStatus

object VkHashValidator {
    private const val OUTPUT_PREFIX = "HASH_CHECK:"

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
        val command = buildCommand(binary.absolutePath, hashes, fingerprint, clientIds)
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
                lines.mapNotNull(::parseOutputLine)
                    .filter { it.first in requested }
                    .toMap()
            }
        }.getOrDefault(emptyMap())
    }

    internal fun buildCommand(
        binary: String,
        hashes: List<String>,
        fingerprint: String,
        clientIds: String,
    ): List<String> = buildList {
        add(binary)
        add("--validate-vk-hashes")
        add("--vk")
        add(hashes.joinToString(","))
        add("--fingerprint")
        add(fingerprint)
        add("--client-ids")
        add(clientIds)
    }

    internal fun parseOutputLine(line: String): Pair<String, VkHashValidationStatus>? {
        if (!line.startsWith(OUTPUT_PREFIX)) return null
        return runCatching {
            val json = JSONObject(line.removePrefix(OUTPUT_PREFIX))
            val hash = json.getString("hash")
            val status = VkHashValidationStatus.fromWire(json.getString("status")) ?: return null
            hash to status
        }.getOrNull()
    }
}
