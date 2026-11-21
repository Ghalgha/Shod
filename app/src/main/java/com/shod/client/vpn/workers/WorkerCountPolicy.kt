package com.shod.client.vpn.workers

import com.shod.client.ShodConstants

internal object WorkerCountPolicy {
    fun normalize(
        requested: Int,
        maximum: Int = ShodConstants.Tunnel.MAX_WORKERS,
    ): Int {
        val group = ShodConstants.Tunnel.WORKERS_PER_GROUP
        val normalizedMaximum = (maximum.coerceAtLeast(group) / group) * group
        val clamped = requested.coerceIn(group, normalizedMaximum)
        val rounded = (clamped / group) * group
        return rounded.coerceAtLeast(group)
    }

    fun maximumForHashes(hashCount: Int): Int {
        val count = hashCount.coerceIn(1, ShodConstants.Tunnel.MAX_VK_HASHES)
        return (count * ShodConstants.Tunnel.WORKERS_PER_GROUP).coerceIn(
            ShodConstants.Tunnel.WORKERS_PER_GROUP,
            ShodConstants.Tunnel.MAX_WORKERS,
        )
    }

    fun maximumForSources(
        linkMode: Boolean,
        linkHashCount: Int,
        autoHashMode: Boolean,
        manualHashCount: Int,
    ): Int = ShodConstants.Tunnel.MAX_WORKERS

    fun defaultMaximum(extraWorkers: Boolean = false): Int =
        if (extraWorkers) ShodConstants.Tunnel.MAX_WORKERS else ShodConstants.Tunnel.DEFAULT_MAX_WORKERS

    fun normalizeForHashes(requested: Int, hashCount: Int): Int =
        normalize(requested, maximumForHashes(hashCount))

    fun normalizeForHashValues(
        requested: Int,
        hashes: Iterable<String>,
        allowRedistribution: Boolean = false,
    ): Int {
        return normalize(requested, ShodConstants.Tunnel.MAX_WORKERS)
    }
}
