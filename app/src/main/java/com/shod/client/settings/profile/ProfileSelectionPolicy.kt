package com.shod.client.settings.profile

/**
 * Pure selection rules shared by profile storage operations.
 *
 * The caller supplies the configured slot bounds so this policy does not
 * depend on Android or on the application-wide constants container.
 */
internal object ProfileSelectionPolicy {

    fun normalize(requested: Int, minimum: Int, maximum: Int): Int {
        require(minimum <= maximum) { "Minimum profile index must not exceed maximum" }
        return requested.coerceIn(minimum, maximum)
    }

    fun requireValid(profile: Int, minimum: Int, maximum: Int) {
        require(minimum <= maximum && profile in minimum..maximum) {
            "Profile index is outside the supported range"
        }
    }

    /**
     * When the active slot is cleared, select the first other slot that still
     * contains a connection or participant link. If none remain, use minimum.
     */
    fun fallbackAfterClearing(
        clearedProfile: Int,
        occupiedProfiles: Set<Int>,
        minimum: Int,
        maximum: Int,
    ): Int {
        require(minimum <= maximum) { "Minimum profile index must not exceed maximum" }
        return (minimum..maximum).firstOrNull {
            it != clearedProfile && it in occupiedProfiles
        } ?: minimum
    }
}
