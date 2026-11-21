package com.shod.client.settings.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Owns persisted appearance preferences while preserving the legacy key names and defaults. */
internal class ThemePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val themeModeKey = stringPreferencesKey("theme_mode")
        val dynamicColorKey = booleanPreferencesKey("is_dynamic_color")
        val themePaletteKey = stringPreferencesKey("theme_palette")
    }

    val themeMode: Flow<String> = dataStore.data.map { it[themeModeKey] ?: "dark" }
    val isDynamicColor: Flow<Boolean> = dataStore.data.map { it[dynamicColorKey] ?: false }
    val themePalette: Flow<String> = dataStore.data.map { it[themePaletteKey] ?: "indigo" }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { it[themeModeKey] = mode }
    }

    suspend fun saveDynamicColor(enabled: Boolean) {
        dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun saveThemePalette(palette: String) {
        dataStore.edit { it[themePaletteKey] = palette }
    }
}
