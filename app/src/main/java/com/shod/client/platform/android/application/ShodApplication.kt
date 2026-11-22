package com.shod.client.platform.android.application

import com.shod.client.*
import com.shod.client.platform.android.deploy.DeployDiagnostics

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class ShodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DeployDiagnostics.initialize(this)

        // The QS tile and the widgets follow the tunnel from process start, not from MainActivity:
        // a tile or widget tap starts the process too, and the tile is refreshed only on request.
        TunnelManager.initObservers(this)

        val settingsStore = SettingsStore(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                settingsStore.loggingEnabled.collect { enabled ->
                    TunnelManager.isLoggingEnabled = enabled
                }
            } catch (e: Exception) {
                Log.e("ShodApp", "Не удалось отслеживать флаг логирования: ${e.message}")
            }
        }
    }
}
