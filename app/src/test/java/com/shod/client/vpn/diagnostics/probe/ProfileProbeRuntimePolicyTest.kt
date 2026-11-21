package com.shod.client.vpn.diagnostics.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProbeRuntimePolicyTest {
    @Test
    fun idleProductionTunnelAllowsProbe() {
        assertFalse(
            ProfileProbeRuntimePolicy.mainTunnelBusy(
                running = false,
                starting = false,
                stopping = false,
                autoPausedForWifi = false,
            ),
        )
    }

    @Test
    fun everyActiveLifecycleStateBlocksProbe() {
        val states = listOf(
            listOf(true, false, false, false),
            listOf(false, true, false, false),
            listOf(false, false, true, false),
            listOf(false, false, false, true),
        )

        states.forEach { state ->
            assertTrue(
                ProfileProbeRuntimePolicy.mainTunnelBusy(
                    running = state[0],
                    starting = state[1],
                    stopping = state[2],
                    autoPausedForWifi = state[3],
                ),
            )
        }
    }
}
