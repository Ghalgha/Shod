package com.shod.client.settings.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shod.client.ShodConstants
import com.shod.client.settings.model.ProfileConnectionSnapshot
import com.shod.client.settings.secret.SecureStringStore
import com.shod.client.settings.secret.StoredSecret
import com.shod.client.settings.secret.StoredSecretState
import com.shod.client.settings.secret.resolveStoredSecret
import kotlinx.coroutines.flow.first

/**
 * Reads and updates the connection data belonging to one profile slot.
 *
 * Runtime cache invalidation and participant-link rewriting are injected so this
 * persistence component does not depend on UI or connection-runtime packages.
 */
internal class ProfileConnectionStore(
    private val dataStore: DataStore<Preferences>,
    private val secureStringStore: SecureStringStore,
    private val normalizeWorkers: (Int) -> Int,
    private val invalidateProfileCache: (Int) -> Unit,
    private val rewriteParticipantLink: (
        currentLink: String,
        peer: String,
        port: Int,
        password: String,
    ) -> String?,
) {
    private val peerKey = stringPreferencesKey("peer")
    private val serverPeerPortKey = intPreferencesKey("server_peer_port")
    private val workersPerHashKey = intPreferencesKey("workers_per_hash")
    private val extraWorkersKey = booleanPreferencesKey("extra_workers")
    private val connectionPasswordKey = stringPreferencesKey("connection_password")
    private val connectionPasswordEncryptedKey = stringPreferencesKey("connection_password_encrypted")
    private val connectionPasswordLocalKey = stringPreferencesKey("connection_password_local")
    private val participantLinkKey = stringPreferencesKey("shod_link")
    private val participantModeKey = booleanPreferencesKey("shod_link_mode")
    private val manualPortsEnabledKey = booleanPreferencesKey("manual_ports_enabled")
    private val subscriptionUrlKey = stringPreferencesKey("subscription_url")
    private val subscriptionTitleKey = stringPreferencesKey("subscription_title")
    private val subscriptionPowerKey = intPreferencesKey("subscription_power")
    private val profileRevisionKey = longPreferencesKey("profile_revision")
    private val profileRemovedKey = booleanPreferencesKey("profile_removed")

    suspend fun read(profile: Int): ProfileConnectionSnapshot {
        val preferences = dataStore.data.first()
        val secret = readConnectionPassword(preferences, profile)
        return ProfileConnectionSnapshot(
            revision = preferences[ProfilePreferenceKeys.forProfile(profileRevisionKey, profile)] ?: 0L,
            peer = preferences[ProfilePreferenceKeys.forProfile(peerKey, profile)] ?: "",
            serverPeerPort = preferences[ProfilePreferenceKeys.forProfile(serverPeerPortKey, profile)]
                ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
            connectionPassword = secret.value,
            connectionPasswordState = secret.state,
            workersPerHash = normalizeWorkers(
                preferences[ProfilePreferenceKeys.forProfile(workersPerHashKey, profile)] ?: 18,
            ),
            extraWorkers = preferences[ProfilePreferenceKeys.forProfile(extraWorkersKey, profile)] ?: false,
            shodLink = preferences[ProfilePreferenceKeys.forProfile(participantLinkKey, profile)] ?: "",
            shodLinkMode = preferences[ProfilePreferenceKeys.forProfile(participantModeKey, profile)] ?: false,
            subscriptionUrl = preferences[ProfilePreferenceKeys.forProfile(subscriptionUrlKey, profile)] ?: "",
            subscriptionTitle = preferences[ProfilePreferenceKeys.forProfile(subscriptionTitleKey, profile)] ?: "",
            subscriptionPower = preferences[ProfilePreferenceKeys.forProfile(subscriptionPowerKey, profile)] ?: 0,
        )
    }

    /**
     * expectedRevision protects edits made against an outdated profile snapshot.
     * A null revision denotes an explicit user update and advances the profile revision.
     */
    suspend fun save(
        profile: Int,
        peer: String,
        serverPeerPort: Int,
        connectionPassword: String,
        workersPerHash: Int,
        expectedRevision: Long? = null,
    ) {
        var shouldInvalidateProfileCache = false
        dataStore.edit { preferences ->
            val revisionKey = ProfilePreferenceKeys.forProfile(profileRevisionKey, profile)
            val removedKey = ProfilePreferenceKeys.forProfile(profileRemovedKey, profile)
            val revision = preferences[revisionKey] ?: 0L
            if (expectedRevision != null && expectedRevision != revision) return@edit
            if (expectedRevision != null && preferences[removedKey] == true) {
                if (peer.isBlank()) return@edit
                preferences[removedKey] = false
            }
            if (expectedRevision == null) {
                preferences[removedKey] = false
                preferences[revisionKey] = revision + 1L
            }

            val peerKeyForProfile = ProfilePreferenceKeys.forProfile(peerKey, profile)
            val portKeyForProfile = ProfilePreferenceKeys.forProfile(serverPeerPortKey, profile)
            val normalizedPeer = peer.trim()
            val normalizedPort = serverPeerPort.coerceIn(1, 65535)
            if (preferences[peerKeyForProfile].orEmpty() != normalizedPeer ||
                (preferences[portKeyForProfile] ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT) != normalizedPort ||
                readConnectionPassword(preferences, profile).value != connectionPassword
            ) {
                shouldInvalidateProfileCache = true
            }

            preferences[peerKeyForProfile] = normalizedPeer
            preferences[portKeyForProfile] = normalizedPort

            val participantMode = ProfilePreferenceKeys.forProfile(participantModeKey, profile)
            if (preferences[participantMode] == true) {
                val linkKey = ProfilePreferenceKeys.forProfile(participantLinkKey, profile)
                val currentLink = preferences[linkKey].orEmpty()
                rewriteParticipantLink(currentLink, normalizedPeer, normalizedPort, connectionPassword)
                    ?.let { preferences[linkKey] = it }
            }

            if (serverPeerPort != ShodConstants.Network.DEFAULT_SERVER_PEER_PORT) {
                preferences[ProfilePreferenceKeys.forProfile(manualPortsEnabledKey, profile)] = true
            }
            preferences[ProfilePreferenceKeys.forProfile(workersPerHashKey, profile)] =
                normalizeWorkers(workersPerHash)

            val localKey = ProfilePreferenceKeys.forProfile(connectionPasswordLocalKey, profile)
            val legacyKey = ProfilePreferenceKeys.forProfile(connectionPasswordKey, profile)
            val encryptedKey = ProfilePreferenceKeys.forProfile(connectionPasswordEncryptedKey, profile)
            if (connectionPassword.isBlank()) {
                preferences.remove(localKey)
            } else {
                preferences[localKey] = connectionPassword
            }
            preferences.remove(legacyKey)
            preferences.remove(encryptedKey)
        }
        if (shouldInvalidateProfileCache) {
            invalidateProfileCache(profile)
        }
    }

    /** Persists a normalized worker count for the selected profile. */
    suspend fun saveWorkersPerHash(profile: Int, workersPerHash: Int) {
        dataStore.edit { preferences ->
            preferences[ProfilePreferenceKeys.forProfile(workersPerHashKey, profile)] =
                normalizeWorkers(workersPerHash)
        }
    }

    private fun readConnectionPassword(
        preferences: Preferences,
        profile: Int,
    ): StoredSecret {
        val localValue = preferences[ProfilePreferenceKeys.forProfile(connectionPasswordLocalKey, profile)]
        if (!localValue.isNullOrBlank()) {
            return StoredSecret(localValue, StoredSecretState.Readable)
        }
        return resolveStoredSecret(
            preferences[ProfilePreferenceKeys.forProfile(connectionPasswordEncryptedKey, profile)],
            preferences[ProfilePreferenceKeys.forProfile(connectionPasswordKey, profile)],
            secureStringStore::decrypt,
        )
    }
}
