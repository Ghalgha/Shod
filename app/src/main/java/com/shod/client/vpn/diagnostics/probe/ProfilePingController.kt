package com.shod.client.vpn.diagnostics.probe

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ProfilePingState(
    val running: Boolean = false,
    val profile: Int? = null,
    val stopping: Boolean = false
)

/** Coordinates sequential profile probes without coupling the batch to Android or Compose. */
internal class ProfilePingController(
    private val scope: CoroutineScope,
    private val probe: suspend (Int) -> ProfilePingResult,
    private val clear: (Int) -> Unit,
    private val save: (Int, ProfilePingResult) -> Unit,
) {
    private var job: Job? = null
    private val mutableState = MutableStateFlow(ProfilePingState())
    val state = mutableState.asStateFlow()

    fun start(profiles: List<Int>) {
        if (job != null || profiles.isEmpty()) return
        mutableState.value = ProfilePingState(running = true)
        val batch = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (id in profiles.distinct()) {
                    ensureActive()
                    mutableState.value = ProfilePingState(running = true, profile = id)
                    clear(id)
                    val result = try {
                        probe(id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        ProfilePingResult.Failure("Не удалось проверить туннель")
                    }
                    ensureActive()
                    save(id, result)
                }
            } finally {
                job = null
                mutableState.value = ProfilePingState()
            }
        }
        job = batch
        batch.invokeOnCompletion {
            if (job === batch) {
                job = null
                mutableState.value = ProfilePingState()
            }
        }
        batch.start()
    }

    fun cancel() {
        val active = job ?: return
        mutableState.value = mutableState.value.copy(stopping = true)
        active.cancel()
    }
}
