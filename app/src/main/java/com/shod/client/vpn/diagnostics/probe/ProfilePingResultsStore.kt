package com.shod.client.vpn.diagnostics.probe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Session-scoped measurements, independent of UI lifecycle and screen recreation. */
internal class ProfilePingResultsStore {
    private val profiles = MutableStateFlow<Map<Int, ProfilePingResult>>(emptyMap())
    private val turnSubnets = MutableStateFlow<Map<String, Int>>(emptyMap())

    val profileResults = profiles.asStateFlow()
    val turnResults = turnSubnets.asStateFlow()

    fun saveProfile(profile: Int, result: ProfilePingResult) {
        profiles.update { it + (profile to result) }
    }

    fun removeProfile(profile: Int) {
        profiles.update { it - profile }
    }

    fun saveTurn(subnet: String, milliseconds: Int) {
        require(subnet.isNotBlank())
        require(milliseconds > 0)
        turnSubnets.update { it + (subnet to milliseconds) }
    }

    fun clearTurn() {
        turnSubnets.value = emptyMap()
    }
}
