package com.shod.client.settings.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single Preferences DataStore backing user settings.
 *
 * Keep the persisted name stable: existing installations store their data in "settings".
 * All SettingsStore instances must use this delegate rather than creating another DataStore.
 */
internal val Context.settingsDataStore by preferencesDataStore(name = "settings")
