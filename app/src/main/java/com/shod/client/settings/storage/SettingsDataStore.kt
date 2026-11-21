package com.shod.client.settings.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single Preferences DataStore used by settings.
 *
 * Keep the on-disk name "settings": changing it would make existing user
 * preferences appear to be missing. Feature-specific repositories share this
 * instance rather than creating independent stores.
 */
internal val Context.settingsDataStore by preferencesDataStore(name = "settings")
