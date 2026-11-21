package com.shod.client.settings.system

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/** Stores whether the battery-optimization prompt has already been handled. */
internal class BatteryOptimizationPromptStore(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val promptHandledKey =
            booleanPreferencesKey("battery_optimization_prompt_handled")
    }

    suspend fun isHandled(): Boolean =
        dataStore.data.first()[promptHandledKey] ?: false

    suspend fun markHandled() {
        dataStore.edit { it[promptHandledKey] = true }
    }
}
