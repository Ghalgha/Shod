package com.shod.client.vpn.diagnostics.probe

/**
 * Prevents a profile probe from starting alongside the app's production VPN lifecycle.
 * Kept pure so the orchestration layer can enforce this invariant without owning TunnelManager.
 */
internal object ProfileProbeRuntimePolicy {
    fun mainTunnelBusy(
        running: Boolean,
        starting: Boolean,
        stopping: Boolean,
        autoPausedForWifi: Boolean,
    ): Boolean = running || starting || stopping || autoPausedForWifi
}
