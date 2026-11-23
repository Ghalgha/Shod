package com.shod.client.vpn.workers

import com.shod.client.ShodConstants
import com.shod.client.integration.vk.calls.VkAutoCallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerCountPolicyTest {
    private val autoCallsManager = VkAutoCallsManager(
        scope = CoroutineScope(Dispatchers.Unconfined),
        isLoggingEnabled = { false },
        updateLog = { _, _, _, _ -> },
        clearStoredToken = { },
    )

    @Test
    fun everySupportedTotalRemainsAnExactMultipleOfTen() {
        val supported = (1..ShodConstants.Tunnel.MAX_WORKERS / ShodConstants.Tunnel.WORKERS_PER_GROUP)
            .map { it * ShodConstants.Tunnel.WORKERS_PER_GROUP }
        for ((groups, workers) in supported.withIndex()) {
            assertEquals(workers, WorkerCountPolicy.normalize(workers))
            assertEquals(groups + 1, workers / ShodConstants.Tunnel.WORKERS_PER_GROUP)
        }
        assertEquals(100, supported.last())
        assertEquals(100, WorkerCountPolicy.normalize(ShodConstants.Tunnel.MAX_WORKERS))
    }

    @Test
    fun extraWorkersRaiseTheDefaultMaximum() {
        assertEquals(
            ShodConstants.Tunnel.DEFAULT_MAX_WORKERS,
            WorkerCountPolicy.defaultMaximum(extraWorkers = false),
        )
        assertEquals(
            ShodConstants.Tunnel.MAX_WORKERS,
            WorkerCountPolicy.defaultMaximum(extraWorkers = true),
        )
        assertEquals(
            ShodConstants.Tunnel.DEFAULT_MAX_WORKERS,
            WorkerCountPolicy.normalize(
                Int.MAX_VALUE,
                maximum = WorkerCountPolicy.defaultMaximum(extraWorkers = false),
            ),
        )
    }

    @Test
    fun hashCapacityHasNoHiddenExtraWorkers() {
        assertEquals(10, WorkerCountPolicy.maximumForHashes(1))
        assertEquals(20, WorkerCountPolicy.maximumForHashes(2))
        assertEquals(30, WorkerCountPolicy.maximumForHashes(3))
        assertEquals(40, WorkerCountPolicy.maximumForHashes(4))
        assertEquals(50, WorkerCountPolicy.maximumForHashes(5))
        assertEquals(60, WorkerCountPolicy.maximumForHashes(6))
        assertEquals(70, WorkerCountPolicy.maximumForHashes(7))
    }

    @Test
    fun participantCapacityComesFromLinkBeforeLocalProfileState() {
        assertEquals(
            100,
            WorkerCountPolicy.maximumForSources(
                linkMode = true,
                linkHashCount = 4,
                autoHashMode = false,
                manualHashCount = 0,
            ),
        )
        assertEquals(
            100,
            WorkerCountPolicy.maximumForSources(
                linkMode = false,
                linkHashCount = 4,
                autoHashMode = false,
                manualHashCount = 1,
            ),
        )
        assertEquals(
            100,
            WorkerCountPolicy.maximumForSources(
                linkMode = false,
                linkHashCount = 0,
                autoHashMode = true,
                manualHashCount = 1,
            ),
        )
    }

    @Test
    fun invalidPersistedCountsAreBoundedAndNeverCreatePartialCredentialGroup() {
        for (requested in -1_000..1_000) {
            val normalized = WorkerCountPolicy.normalize(requested)
            assertTrue(normalized in 10..ShodConstants.Tunnel.MAX_WORKERS)
            assertEquals(0, normalized % 10)
            assertTrue(normalized <= requested.coerceAtLeast(10) || normalized == 100)
        }
    }

    @Test
    fun stalePersistedMaximumIsRecappedByCurrentHashCountAtRuntime() {
        assertEquals(10, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 1))
        assertEquals(20, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 2))
        assertEquals(30, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 3))
        assertEquals(40, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 4))
        assertEquals(50, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 5))
        assertEquals(60, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 6))
        assertEquals(70, WorkerCountPolicy.normalizeForHashes(Int.MAX_VALUE, 7))
    }

    @Test
    fun automaticHashModeAllowsTheFullWorkerRange() {
        for (requested in -100..300) {
            val admitted = WorkerCountPolicy.normalize(requested)
            assertTrue(admitted in 10..ShodConstants.Tunnel.MAX_WORKERS)
            assertEquals(0, admitted % 10)
        }
        assertEquals(100, WorkerCountPolicy.normalize(Int.MAX_VALUE))
    }

    @Test
    fun craftedIntentCountsCannotBypassHashAwareRuntimeAdmission() {
        for (requested in listOf(Int.MIN_VALUE, -1, 0, 4, 28, 109, Int.MAX_VALUE)) {
            for (hashCount in 1..ShodConstants.Tunnel.MAX_VK_HASHES) {
                val admitted = WorkerCountPolicy.normalizeForHashes(requested, hashCount)
                assertTrue(admitted in 10..WorkerCountPolicy.maximumForHashes(hashCount))
                assertTrue(admitted % ShodConstants.Tunnel.WORKERS_PER_GROUP == 0)
            }
        }
        assertEquals(
            100,
            WorkerCountPolicy.normalizeForHashValues(
                Int.MAX_VALUE,
                listOf("same", " same ", "same", ""),
            ),
        )
        assertEquals(
            100,
            WorkerCountPolicy.normalizeForHashValues(
                Int.MAX_VALUE,
                listOf("one", "two", "three", "four", "five", "six", "ignored-seventh"),
            ),
        )
    }

    @Test
    fun automaticCallCountScalesToSevenHashes() {
        assertEquals(1, autoCallsManager.callCountForWorkers(10))
        assertEquals(2, VkAutoCallsManager.callCountForWorkers(20))
        assertEquals(3, VkAutoCallsManager.callCountForWorkers(30))
        assertEquals(5, VkAutoCallsManager.callCountForWorkers(45))
        assertEquals(6, VkAutoCallsManager.callCountForWorkers(60))
        assertEquals(7, VkAutoCallsManager.callCountForWorkers(100))
        assertEquals(ShodConstants.VkAutoHash.AUTO_CALL_SMALL_DELAY_MS, autoCallsManager.requestDelayForCallCount(1))
        assertEquals(ShodConstants.VkAutoHash.AUTO_CALL_SMALL_DELAY_MS, VkAutoCallsManager.requestDelayForCallCount(4))
        assertEquals(ShodConstants.VkAutoHash.AUTO_CALL_LARGE_DELAY_MS, VkAutoCallsManager.requestDelayForCallCount(5))
        assertEquals(ShodConstants.VkAutoHash.AUTO_CALL_LARGE_DELAY_MS, VkAutoCallsManager.requestDelayForCallCount(6))
    }

    @Test
    fun automaticCallFailureCanRedistributeWholeGroupsAcrossCreatedHashes() {
        assertEquals(
            100,
            WorkerCountPolicy.normalizeForHashValues(
                Int.MAX_VALUE,
                listOf("one", "two", "three", "four", "five"),
                allowRedistribution = true,
            ),
        )
    }

    @Test
    fun manualHashesSupportFullWorkerRangeUpToMaxWorkers() {
        assertEquals(
            30,
            WorkerCountPolicy.normalizeForHashValues(
                30,
                listOf("hash1_at_least_16_chars", "hash2_at_least_16_chars"),
                allowRedistribution = false,
            ),
        )
        assertEquals(
            100,
            WorkerCountPolicy.normalizeForHashValues(
                100,
                listOf("hash1_at_least_16_chars", "hash2_at_least_16_chars"),
                allowRedistribution = false,
            ),
        )
        assertEquals(
            50,
            WorkerCountPolicy.normalizeForHashValues(
                50,
                listOf("single_hash_at_least_16_chars"),
                allowRedistribution = false,
            ),
        )
    }
}
