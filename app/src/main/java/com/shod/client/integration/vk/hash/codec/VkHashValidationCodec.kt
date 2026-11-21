package com.shod.client.integration.vk.hash.codec

import org.json.JSONObject

enum class VkHashValidationStatus(val wireValue: String) {
    Valid("valid"),
    Invalid("invalid");

    companion object {
        fun fromWire(value: String): VkHashValidationStatus? = entries.firstOrNull {
            it.wireValue == value
        }
    }
}

object VkHashValidationCodec {
    fun decode(value: String): Map<String, VkHashValidationStatus> = runCatching {
        val json = JSONObject(value)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val hash = keys.next()
                VkHashValidationStatus.fromWire(json.optString(hash))?.let { put(hash, it) }
            }
        }
    }.getOrDefault(emptyMap())

    fun encode(results: Map<String, VkHashValidationStatus>): String {
        val json = JSONObject()
        results.toSortedMap().forEach { (hash, status) -> json.put(hash, status.wireValue) }
        return json.toString()
    }

    fun pending(
        hashes: List<String>,
        results: Map<String, VkHashValidationStatus>,
    ): List<String> = hashes
        .filter { it.length >= 16 }
        .distinct()
        .filterNot(results::containsKey)

    fun active(
        hashes: List<String>,
        results: Map<String, VkHashValidationStatus>,
    ): List<String> = hashes
        .map(String::trim)
        .filter { it.length >= 16 }
        .distinct()
        .filter { results[it] != VkHashValidationStatus.Invalid }

    fun invalidate(
        results: Map<String, VkHashValidationStatus>,
        previousHash: String,
    ): Map<String, VkHashValidationStatus> = results - previousHash
}
