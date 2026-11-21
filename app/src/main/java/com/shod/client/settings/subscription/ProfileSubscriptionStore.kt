package com.shod.client.settings.subscription

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shod.client.settings.model.ProfileSubscriptionInfo
import com.shod.client.settings.profile.ProfileLifecycleStore
import com.shod.client.settings.profile.ProfilePreferenceKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Reads subscription ownership from profile slots and coordinates subscription removal.
 *
 * The persisted per-profile fields remain unchanged. Profile clearing is delegated
 * to ProfileLifecycleStore so revision and active-profile behavior stay centralized.
 */
internal class ProfileSubscriptionStore(
    private val dataStore: DataStore<Preferences>,
    private val lifecycleStore: ProfileLifecycleStore,
    private val minimumProfile: Int,
    private val maximumProfile: Int,
) {
    init {
        require(minimumProfile <= maximumProfile) {
            "Minimum profile index must not exceed maximum profile index"
        }
    }

    private val subscriptionUrlKey = stringPreferencesKey("subscription_url")
    private val subscriptionTitleKey = stringPreferencesKey("subscription_title")
    private val subscriptionUuidKey = stringPreferencesKey("subscription_uuid")
    private val subscriptionNodeKey = stringPreferencesKey("subscription_node")
    private val subscriptionPowerKey = androidx.datastore.preferences.core.intPreferencesKey("subscription_power")
    private val subscriptionMetaKey = stringPreferencesKey("subscription_meta_v1")

    val profileSubscriptions: Flow<Map<Int, ProfileSubscriptionInfo>> =
        dataStore.data.map { preferences ->
            (minimumProfile..maximumProfile).mapNotNull { profile ->
                val url = preferences[
                    ProfilePreferenceKeys.forProfile(subscriptionUrlKey, profile)
                ]?.trim().orEmpty()
                if (url.isEmpty()) return@mapNotNull null

                profile to ProfileSubscriptionInfo(
                    url = url,
                    title = preferences[
                        ProfilePreferenceKeys.forProfile(subscriptionTitleKey, profile)
                    ]?.trim().orEmpty(),
                    subUuid = preferences[
                        ProfilePreferenceKeys.forProfile(subscriptionUuidKey, profile)
                    ].orEmpty(),
                    nodeName = preferences[
                        ProfilePreferenceKeys.forProfile(subscriptionNodeKey, profile)
                    ]?.trim().orEmpty(),
                    powerLimit = preferences[
                        ProfilePreferenceKeys.forProfile(subscriptionPowerKey, profile)
                    ] ?: 0,
                )
            }.toMap()
        }

    /** Clears every profile belonging to the URL, then removes its shared metadata. */
    suspend fun clearSubscription(url: String) {
        if (url.isBlank()) return

        val preferences = dataStore.data.first()
        val targetProfiles = (minimumProfile..maximumProfile).filter { profile ->
            preferences[
                ProfilePreferenceKeys.forProfile(subscriptionUrlKey, profile)
            ].orEmpty() == url
        }

        for (profile in targetProfiles) {
            lifecycleStore.clearProfile(profile)
        }

        val metadataKey = url.trim()
        if (metadataKey.isEmpty()) return
        dataStore.edit { mutable ->
            val metadata = SubscriptionMetaCodec.decode(mutable[subscriptionMetaKey]).toMutableMap()
            if (metadata.remove(metadataKey) != null) {
                mutable[subscriptionMetaKey] = SubscriptionMetaCodec.encode(metadata)
            }
        }
    }
}
