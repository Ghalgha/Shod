package com.shod.client.settings.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists update-check state and update-dialog interaction metadata. */
internal class UpdatePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val lastCheckAtKey = longPreferencesKey("update_last_check_at")
        private val latestVersionKey = stringPreferencesKey("update_latest_version")
        private val lastErrorKey = stringPreferencesKey("update_last_error")
        private val checkIntervalHoursKey = intPreferencesKey("update_check_interval_hours")
        private val postponeUntilKey = longPreferencesKey("update_postpone_until")
        private val postponeVersionKey = stringPreferencesKey("update_postpone_version")
        private val dialogLastShownVersionKey = stringPreferencesKey("update_dialog_last_shown_version")
        private val dialogLastShownAtKey = longPreferencesKey("update_dialog_last_shown_at")
        private val dialogLastActionVersionKey = stringPreferencesKey("update_dialog_last_action_version")
        private val dialogLastActionKey = stringPreferencesKey("update_dialog_last_action")
        private val dialogLastActionAtKey = longPreferencesKey("update_dialog_last_action_at")
    }

    val lastCheckAt: Flow<Long> = dataStore.data.map { it[lastCheckAtKey] ?: 0L }
    val latestVersion: Flow<String> = dataStore.data.map { it[latestVersionKey] ?: "" }
    val lastError: Flow<String> = dataStore.data.map { it[lastErrorKey] ?: "" }
    val checkIntervalHours: Flow<Int> = dataStore.data.map { it[checkIntervalHoursKey] ?: 24 }
    val postponeUntil: Flow<Long> = dataStore.data.map { it[postponeUntilKey] ?: 0L }
    val postponeVersion: Flow<String> = dataStore.data.map { it[postponeVersionKey] ?: "" }

    suspend fun saveState(lastCheckAt: Long, latestVersion: String, error: String) {
        dataStore.edit {
            it[lastCheckAtKey] = lastCheckAt
            it[latestVersionKey] = latestVersion
            it[lastErrorKey] = error
        }
    }

    suspend fun saveCheckIntervalHours(hours: Int) {
        dataStore.edit { it[checkIntervalHoursKey] = hours }
    }

    suspend fun savePostpone(version: String, until: Long) {
        dataStore.edit {
            it[postponeVersionKey] = version
            it[postponeUntilKey] = until
        }
    }

    suspend fun saveDialogShown(version: String, shownAt: Long) {
        dataStore.edit {
            it[dialogLastShownVersionKey] = version
            it[dialogLastShownAtKey] = shownAt
        }
    }

    suspend fun saveDialogAction(version: String, action: String, actedAt: Long) {
        dataStore.edit {
            it[dialogLastActionVersionKey] = version
            it[dialogLastActionKey] = action
            it[dialogLastActionAtKey] = actedAt
        }
    }
}
