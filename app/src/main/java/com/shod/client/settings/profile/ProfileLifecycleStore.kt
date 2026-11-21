package com.shod.client.settings.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Performs the legacy profile-clear transaction as one DataStore edit.
 *
 * The callback lets the application layer invalidate its in-memory ping cache
 * without making the settings package depend on the connection/runtime package.
 */
internal class ProfileLifecycleStore(
    private val dataStore: DataStore<Preferences>,
    private val minimumProfile: Int,
    private val maximumProfile: Int,
    private val onProfileCleared: (Int) -> Unit = {},
) {
    init {
        require(minimumProfile <= maximumProfile) {
            "Minimum profile index must not exceed maximum profile index"
        }
    }

    private val activeProfileKey = intPreferencesKey("active_profile")
    private val profileRemovedKey = booleanPreferencesKey("profile_removed")
    private val profileRevisionKey = longPreferencesKey("profile_revision")
    private val peerKey = stringPreferencesKey("peer")
    private val participantLinkKey = stringPreferencesKey("shod_link")

    private val keysClearedWithProfile = listOf(
        stringPreferencesKey("profile_name"),
        peerKey,
        intPreferencesKey("server_peer_port"),
        intPreferencesKey("workers_per_hash"),
        stringPreferencesKey("connection_password"),
        stringPreferencesKey("connection_password_encrypted"),
        stringPreferencesKey("connection_password_local"),
        participantLinkKey,
        booleanPreferencesKey("shod_link_mode"),
        stringPreferencesKey("link_hashes_removed"),
        stringPreferencesKey("subscription_url"),
        stringPreferencesKey("subscription_title"),
        stringPreferencesKey("subscription_uuid"),
        stringPreferencesKey("subscription_node"),
        intPreferencesKey("subscription_power"),
    )

    /**
     * Marks the slot removed, increments its revision, clears connection data,
     * and selects a remaining occupied connection slot if it was active.
     */
    suspend fun clearProfile(profile: Int) {
        dataStore.edit { preferences ->
            val removedKey = ProfilePreferenceKeys.forProfile(profileRemovedKey, profile)
            val revisionKey = ProfilePreferenceKeys.forProfile(profileRevisionKey, profile)
            preferences[removedKey] = true
            preferences[revisionKey] = (preferences[revisionKey] ?: 0L) + 1L

            // Keep this invalidation in the same transaction callback as the legacy implementation.
            onProfileCleared(profile)

            keysClearedWithProfile.forEach { key ->
                preferences.remove(ProfilePreferenceKeys.forProfile(key, profile))
            }

            val active = preferences[activeProfileKey] ?: minimumProfile
            if (active == profile) {
                val fallback = (minimumProfile..maximumProfile).firstOrNull { candidate ->
                    candidate != profile && (
                        !preferences[ProfilePreferenceKeys.forProfile(peerKey, candidate)].isNullOrBlank() ||
                            !preferences[ProfilePreferenceKeys.forProfile(participantLinkKey, candidate)].isNullOrBlank()
                        )
                }
                preferences[activeProfileKey] = fallback ?: minimumProfile
            }
        }
    }
}
