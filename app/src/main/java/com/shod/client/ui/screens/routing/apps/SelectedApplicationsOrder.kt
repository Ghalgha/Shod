package com.shod.client.ui.screens.routing.apps

import java.util.Locale

/**
 * UI ordering for the installed-app picker:
 * selected packages are listed first, then remaining apps alphabetically.
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
