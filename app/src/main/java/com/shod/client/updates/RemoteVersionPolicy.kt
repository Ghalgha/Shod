package com.shod.client.updates

import com.shod.client.ShodConstants

const val UPDATE_CHECK_NEVER = ShodConstants.Update.CHECK_NEVER
const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = ShodConstants.Update.DEFAULT_CHECK_INTERVAL_HOURS
const val UPDATE_DIALOG_ACTION_POSTPONED = ShodConstants.Update.ACTION_POSTPONED
const val UPDATE_DIALOG_ACTION_UPDATE = ShodConstants.Update.ACTION_UPDATE

private val VERSION_NUMBER_REGEX = ShodConstants.Patterns.VERSION_NUMBER

fun updateIntervalHoursToMillis(hours: Int): Long? = when {
    hours <= 0 -> null
    else -> hours * 60L * 60L * 1000L
}

data class AppReleaseInfo(
    val versionTag: String,
    val releaseUrl: String,
    val source: RemoteVersionSource
)

enum class RemoteVersionSource {
    Release,
    Tag
}

private fun versionParts(version: String): List<Int> {
    val normalized = VERSION_NUMBER_REGEX.find(version.trim())?.value ?: return emptyList()
    return normalized.split(".").mapNotNull { it.toIntOrNull() }
}

internal fun normalizeVersionTag(version: String): String {
    val trimmed = version.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.startsWith("v", ignoreCase = true)) trimmed else "v$trimmed"
}

private fun extractTagFromReleaseUrl(releaseUrl: String): String? {
    val marker = "/releases/tag/"
    val index = releaseUrl.indexOf(marker)
    if (index < 0) return null
    return releaseUrl.substring(index + marker.length)
        .substringBefore("?")
        .substringBefore("#")
        .substringBefore("/")
        .takeIf { it.isNotBlank() }
        ?.let(::normalizeVersionTag)
}
