package com.shod.client.vpn.routing.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedApplicationsOrderTest {
    private data class App(val packageName: String, val label: String)

    @Test
    fun selectedAppsComeFirstAndRemainingAppsAreAlphabetical() {
        val apps = listOf(
            App("org.zeta", "Zeta"),
            App("org.beta", "Beta"),
            App("org.alpha", "Alpha"),
            App("org.gamma", "Gamma"),
        )
        val result = selectedApplicationsFirst(
            apps,
            linkedSetOf("org.gamma", "org.beta"),
            App::packageName,
            App::label,
        )
        assertEquals(
            listOf("org.beta", "org.gamma", "org.alpha", "org.zeta"),
            result.map(App::packageName),
        )
    }

    @Test
    fun packageNameBreaksTiesForEqualLabels() {
        val apps = listOf(App("z.example", "Same"), App("a.example", "same"))
        val result = selectedApplicationsFirst(apps, emptySet(), App::packageName, App::label)
        assertEquals(listOf("a.example", "z.example"), result.map(App::packageName))
    }
}
