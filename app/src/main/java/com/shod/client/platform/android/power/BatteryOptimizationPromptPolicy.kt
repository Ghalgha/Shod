package com.shod.client.platform.android.power

internal fun shouldRequestBatteryOptimizationExemption(
    promptHandled: Boolean,
    alreadyExempt: Boolean,
): Boolean = !promptHandled && !alreadyExempt
