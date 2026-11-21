package com.shod.client.vpn.routing.ui.apps

import java.util.Locale

/**
 * UI ordering for the installed-app picker:
 * selected packages retain their selection order, then unselected apps are alphabetical.
 */
internal fun <T> selectedApplicationsFirst(
    apps: List<T>,
    selected: Set<String>,
    packageName: (T) -> String,
    label: (T) -> String,
): List<T> = apps.sortedWith(
    compareByDescending<T> { packageName(it) in selected }
        .thenBy { label(it).lowercase(Locale.ROOT) }
        .thenBy { packageName(it) },
)
