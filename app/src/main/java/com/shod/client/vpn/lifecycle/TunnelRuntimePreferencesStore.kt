package com.shod.client.vpn.lifecycle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the tunnel's restart marker and the latest user-facing diagnostic. */
internal class TunnelRuntimePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val wasRunningKey = booleanPreferencesKey("tunnel_was_running")
        private val lastDiagnosticKey = stringPreferencesKey("tunnel_last_diagnostic")
        private val lastDiagnosticAtKey = longPreferencesKey("tunnel_last_diagnostic_at")
    }

    val wasRunning: Flow<Boolean> = dataStore.data.map { it[wasRunningKey] ?: false }
    val lastDiagnostic: Flow<String> = dataStore.data.map { it[lastDiagnosticKey] ?: "" }

    suspend fun saveWasRunning(running: Boolean) {
        dataStore.edit { it[wasRunningKey] = running }
    }

    suspend fun saveDiagnostic(code: String, message: String) {
        val normalizedCode = code.trim().take(80)
        val normalizedMessage = message.trim().replace(Regex("\\s+"), " ").take(400)
        dataStore.edit {
            it[lastDiagnosticKey] = "$normalizedCode|$normalizedMessage"
            it[lastDiagnosticAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun clearDiagnostic() {
        dataStore.edit {
            it.remove(lastDiagnosticKey)
            it.remove(lastDiagnosticAtKey)
        }
    }
}
