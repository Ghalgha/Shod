package com.shod.client.vpn.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.shod.client.vpn.power.BatterySaver
import com.shod.client.SettingsStore
import com.shod.client.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Observes Android battery state and applies the configured battery-saving policy. */
internal class TunnelBatteryMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val isDeploying: StateFlow<Boolean>,
    private val onPolicyChanged: (save: Boolean, changed: Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private var receiver: BroadcastReceiver? = null
    private var settingsJob: Job? = null
    private var mode = BatterySaver.DEFAULT_MODE
    private var thresholdPercent = BatterySaver.DEFAULT_PERCENT
    private var batteryLevel = -1
    private var charging = false
    private var systemPowerSave = false
    private var settingsLoaded = false

    fun start() {
        if (receiver != null || settingsJob != null) return
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        systemPowerSave = powerManager?.isPowerSaveMode == true
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    systemPowerSave = powerManager?.isPowerSaveMode == true
                } else {
                    readBattery(intent)
                }
                reconcile()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onSuccess { sticky ->
            receiver = batteryReceiver
            sticky?.let(::readBattery)
        }

        settingsJob = scope.launch {
            val store = SettingsStore(appContext)
            // Deploy and tunnel state changes also require policy reconciliation.
            combine(store.batterySaverMode, store.batterySaverPercent, isDeploying, TunnelManager.running) {
                    configuredMode, percent, _, _ -> configuredMode to percent
            }.collect { (configuredMode, percent) ->
                withContext(Dispatchers.Main) {
                    mode = configuredMode
                    thresholdPercent = percent
                    settingsLoaded = true
                    reconcile()
                }
            }
        }
    }

    fun stop() {
        settingsJob?.cancel()
        settingsJob = null
        receiver?.let { registered ->
            runCatching { appContext.unregisterReceiver(registered) }
        }
        receiver = null
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun reconcile() {
        if (!settingsLoaded) return
        val save = BatterySaver.shouldSave(mode, thresholdPercent, batteryLevel, charging, systemPowerSave)
        val changed = save != BatterySaver.active.value
        BatterySaver.setActive(save)
        onPolicyChanged(save, changed)
    }
}
