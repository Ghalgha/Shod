package com.shod.client.settings.deploy

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shod.client.settings.profile.ProfilePreferenceKeys

internal data class DeployInspectionState(
    val serverConfigured: Boolean = false,
    val checkedAtMs: Long = 0L,
    val inspectionOk: Boolean = false,
    val arch: String = "",
    val serviceStatus: String = "",
    val installedVersion: String = "",
    val message: String = "",
    val panelAvailable: Boolean = false,
)

/** Owns deploy-server configuration status and its last successful inspection result. */
internal class DeployInspectionStore {
    private val serverConfiguredKey = booleanPreferencesKey("deploy_server_configured")
    private val inspectionAtKey = longPreferencesKey("deploy_last_inspection_at")
    private val inspectionOkKey = booleanPreferencesKey("deploy_last_inspection_ok")
    private val inspectionArchKey = stringPreferencesKey("deploy_last_inspection_arch")
    private val inspectionServiceKey = stringPreferencesKey("deploy_last_inspection_service")
    private val inspectionVersionKey = stringPreferencesKey("deploy_last_inspection_version")
    private val inspectionMessageKey = stringPreferencesKey("deploy_last_inspection_message")
    private val inspectionPanelKey = booleanPreferencesKey("deploy_last_inspection_panel")

    private fun <T> key(base: Preferences.Key<T>, profile: Int): Preferences.Key<T> =
        ProfilePreferenceKeys.forProfile(base, profile)

    fun read(prefs: Preferences, profile: Int): DeployInspectionState = DeployInspectionState(
        serverConfigured = prefs[key(serverConfiguredKey, profile)] ?: false,
        checkedAtMs = prefs[key(inspectionAtKey, profile)] ?: 0L,
        inspectionOk = prefs[key(inspectionOkKey, profile)] ?: false,
        arch = prefs[key(inspectionArchKey, profile)] ?: "",
        serviceStatus = prefs[key(inspectionServiceKey, profile)] ?: "",
        installedVersion = prefs[key(inspectionVersionKey, profile)] ?: "",
        message = prefs[key(inspectionMessageKey, profile)] ?: "",
        panelAvailable = prefs[key(inspectionPanelKey, profile)] ?: false,
    )

    fun setServerConfigured(prefs: MutablePreferences, profile: Int, configured: Boolean) {
        prefs[key(serverConfiguredKey, profile)] = configured
    }

    fun clearServerConfigured(prefs: MutablePreferences, profile: Int) {
        prefs.remove(key(serverConfiguredKey, profile))
    }

    fun saveInspection(
        prefs: MutablePreferences,
        profile: Int,
        checkedAtMs: Long,
        arch: String,
        serviceStatus: String,
        installedVersion: String,
        message: String,
        panelAvailable: Boolean,
    ) {
        prefs[key(inspectionAtKey, profile)] = checkedAtMs
        prefs[key(inspectionOkKey, profile)] = true
        prefs[key(inspectionArchKey, profile)] = arch
        prefs[key(inspectionServiceKey, profile)] = serviceStatus
        prefs[key(inspectionVersionKey, profile)] = installedVersion
        prefs[key(inspectionMessageKey, profile)] = message
        prefs[key(inspectionPanelKey, profile)] = panelAvailable
    }

    fun clearInspection(prefs: MutablePreferences, profile: Int) {
        prefs.remove(key(inspectionAtKey, profile))
        prefs.remove(key(inspectionOkKey, profile))
        prefs.remove(key(inspectionArchKey, profile))
        prefs.remove(key(inspectionServiceKey, profile))
        prefs.remove(key(inspectionVersionKey, profile))
        prefs.remove(key(inspectionMessageKey, profile))
        prefs.remove(key(inspectionPanelKey, profile))
    }
}
