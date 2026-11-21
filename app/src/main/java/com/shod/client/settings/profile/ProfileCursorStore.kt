package com.shod.client.settings.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Owns the active connection-profile cursor in the existing "settings" store.
 *
 * This component deliberately stores only the cursor. Profile records remain
 * under their historical keys and are addressed by ProfilePreferenceKeys.
 * The key name "active_profile" is part of the persisted format.
 */
internal class ProfileCursorStore(
    private val dataStore: DataStore<Preferences>,
    private val minimumProfile: Int,
    private val maximumProfile: Int,
) {
    init {
        require(minimumProfile <= maximumProfile) {
            "Minimum profile index must not exceed maximum profile index"
        }
    }

    private val activeProfileKey = intPreferencesKey("active_profile")

    val activeProfile: Flow<Int> = dataStore.data.map { preferences ->
        ProfileSelectionPolicy.normalize(
            requested = preferences[activeProfileKey] ?: minimumProfile,
            minimum = minimumProfile,
            maximum = maximumProfile,
        )
    }

    /** Matches the legacy UI-facing setter: out-of-range values are clamped. */
    suspend fun saveActiveProfile(profile: Int) {
        dataStore.edit { preferences ->
            preferences[activeProfileKey] = ProfileSelectionPolicy.normalize(
                requested = profile,
                minimum = minimumProfile,
                maximum = maximumProfile,
            )
        }
    }

    /** Matches the stricter internal selector used by connection workflows. */
    suspend fun selectConnectionProfile(profile: Int) {
        ProfileSelectionPolicy.requireValid(profile, minimumProfile, maximumProfile)
        dataStore.edit { preferences -> preferences[activeProfileKey] = profile }
    }

    suspend fun currentProfile(): Int = activeProfile.first()
}
