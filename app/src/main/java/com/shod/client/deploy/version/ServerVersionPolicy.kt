package com.shod.client.deploy.version

/**
 * Extracts the numeric version used for comparisons from server output.
 *
 * Accepts values such as "v2.5.0" and "2.5.0 (build 123)".
 * Returns an empty string when no numeric version is present.
 */
internal fun comparableServerVersion(raw: String): String =
    Regex("\\d+(?:\\.\\d+)*").find(raw.trim())?.value.orEmpty()
