package com.shod.client.settings.subscription

import com.shod.client.settings.model.SubscriptionMeta
import org.json.JSONObject

/**
 * Encodes subscription metadata without changing the persisted JSON schema.
 * Invalid entries are ignored to preserve the store's tolerant read behavior.
 */
internal object SubscriptionMetaCodec {
    fun decode(raw: String?): Map<String, SubscriptionMeta> {
        if (raw.isNullOrBlank()) return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = linkedMapOf<String, SubscriptionMeta>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = json.optJSONObject(key) ?: continue
            val url = entry.optString("url").trim().ifEmpty { key.trim() }
            if (url.isEmpty()) continue
            result[url] = SubscriptionMeta(
                url = url,
                title = entry.optString("title").trim(),
                autoUpdateMinutes = entry.optInt("autoUpdateMinutes", 0).coerceAtLeast(0),
                lastUpdatedAt = entry.optLong("lastUpdatedAt", 0L).coerceAtLeast(0L),
                lastAttemptAt = entry.optLong("lastAttemptAt", 0L).coerceAtLeast(0L),
                allowHttp = entry.optBoolean("allowHttp", false),
            )
        }
        return result
    }

    fun encode(meta: Map<String, SubscriptionMeta>): String {
        val json = JSONObject()
        for ((key, value) in meta) {
            if (key.isBlank()) continue
            json.put(
                key,
                JSONObject().apply {
                    put("url", value.url)
                    put("title", value.title)
                    put("autoUpdateMinutes", value.autoUpdateMinutes)
                    put("lastUpdatedAt", value.lastUpdatedAt)
                    put("lastAttemptAt", value.lastAttemptAt)
                    put("allowHttp", value.allowHttp)
                },
            )
        }
        return json.toString()
    }
}
