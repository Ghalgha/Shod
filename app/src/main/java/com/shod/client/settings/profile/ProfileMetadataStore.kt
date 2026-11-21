package com.shod.client.settings.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists profile display names and exposes the legacy occupancy definition.
 *
 * Profile names keep the historical key format: profile zero uses "profile_name";
 * other slots use "profile_name_<index>".
 */
internal class ProfileMetadataStore(
    private val dataStore: DataStore<Preferences>,
    private val minimumProfile: Int,
    private val maximumProfile: Int,
) {
    init {
        require(minimumProfile <= maximumProfile) {
            "Minimum profile index must not exceed maximum profile index"
        }
    }

    private val profileNameKey = stringPreferencesKey("profile_name")
    private val peerKey = stringPreferencesKey("peer")
    private val participantLinkKey = stringPreferencesKey("shod_link")
    private val deployIpKey = stringPreferencesKey("deploy_ip")

    val profileNames: Flow<Map<Int, String>> = dataStore.data.map { preferences ->
        (minimumProfile..maximumProfile).associateWith { profile ->
            preferences[ProfilePreferenceKeys.forProfile(profileNameKey, profile)]
                .takeUnless { it.isNullOrBlank() }
                ?: defaultProfileName(profile)
        }
    }

    /** A slot is occupied only by a peer or participant link, matching the legacy flow. */
    val occupiedProfiles: Flow<Set<Int>> = dataStore.data.map { preferences ->
        (minimumProfile..maximumProfile).filterTo(linkedSetOf()) { profile ->
            !preferences[ProfilePreferenceKeys.forProfile(peerKey, profile)].isNullOrBlank() ||
                !preferences[ProfilePreferenceKeys.forProfile(participantLinkKey, profile)].isNullOrBlank()
        }
    }

    /** Matches the legacy free-slot search, including deploy IP occupancy. */
    suspend fun findFirstFreeProfile(): Int? = dataStore.data.map { preferences ->
        (minimumProfile..maximumProfile).firstOrNull { profile ->
            preferences[ProfilePreferenceKeys.forProfile(peerKey, profile)].isNullOrBlank() &&
                preferences[ProfilePreferenceKeys.forProfile(participantLinkKey, profile)].isNullOrBlank() &&
                preferences[ProfilePreferenceKeys.forProfile(deployIpKey, profile)].isNullOrBlank()
        }
    }.first()

    /** VPS allocation uses the same slot-occupancy rule as connection allocation. */
    suspend fun findFirstFreeDeployProfile(): Int? = dataStore.data.map { preferences ->
        (minimumProfile..maximumProfile).firstOrNull { profile ->
            preferences[ProfilePreferenceKeys.forProfile(peerKey, profile)].isNullOrBlank() &&
                preferences[ProfilePreferenceKeys.forProfile(participantLinkKey, profile)].isNullOrBlank() &&
                preferences[ProfilePreferenceKeys.forProfile(deployIpKey, profile)].isNullOrBlank()
        }
    }.first()

    suspend fun saveProfileName(profile: Int, name: String) {
        ProfileSelectionPolicy.requireValid(profile, minimumProfile, maximumProfile)
        dataStore.edit { preferences ->
            val trimmed = name.trim()
            val key = ProfilePreferenceKeys.forProfile(profileNameKey, profile)
            if (trimmed.isEmpty() || trimmed == defaultProfileName(profile)) {
                preferences.remove(key)
            } else {
                preferences[key] = trimmed
            }
        }
    }
}
