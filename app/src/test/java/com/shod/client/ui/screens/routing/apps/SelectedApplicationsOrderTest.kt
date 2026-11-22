package com.shod.client.ui.screens.routing.apps

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

    private fun order(selected: Set<String>): List<String> {
        val apps = listOf(
            App("z", "Zulu"),
            App("b", "beta"),
            App("a", "Alpha"),
            App("c", "Charlie"),
        )
        return selectedApplicationsFirst(apps, selected, App::packageName, App::label)
            .map(App::packageName)
    }

    @Test
    fun selectedAppsPrecedeUnselectedAndEachGroupIsAlphabetical() {
        assertEquals(listOf("b", "z", "a", "c"), order(setOf("z", "b")))
    }

    @Test
    fun blackAndWhiteListsUseTheirOwnSelection() {
        assertEquals(listOf("c", "a", "b", "z"), order(setOf("c")))
        assertEquals(listOf("z", "a", "b", "c"), order(setOf("z")))
    }

    @Test
    fun emptySelectionAndUnknownPackagesKeepAlphabeticalOrder() {
        assertEquals(listOf("a", "b", "c", "z"), order(emptySet()))
        assertEquals(listOf("a", "b", "c", "z"), order(setOf("not.installed")))
    }

    @Test
    fun packageNameBreaksTiesForEqualLabels() {
        val apps = listOf(App("z.example", "Same"), App("a.example", "same"))
        val result = selectedApplicationsFirst(apps, emptySet(), App::packageName, App::label)
        assertEquals(listOf("a.example", "z.example"), result.map(App::packageName))
    }
}
