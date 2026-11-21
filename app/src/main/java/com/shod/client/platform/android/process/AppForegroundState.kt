package com.shod.client.platform.android.process

/**
 * Process-level foreground state maintained by Android activity lifecycle callbacks.
 * Android integrations can observe app visibility without depending on a specific screen.
 */
object AppForegroundState {
    @Volatile
    var activeActivities: Int = 0
        private set

    val isForeground: Boolean
        get() = activeActivities > 0

    fun onActivityStarted() {
        activeActivities++
    }

    fun onActivityStopped() {
        activeActivities = (activeActivities - 1).coerceAtLeast(0)
    }
}
