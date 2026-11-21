package com.shod.client.deploy.execution

/**
 * Progress event emitted by deploy.sh on stdout.
 *
 * The script protocol is deliberately parsed separately from human-readable
 * output so SSH stream handling can forward progress without leaking protocol
 * markers into the deployment log.
 */
internal data class DeployProgressEvent(
    val fraction: Float,
    val message: String,
)

private val DEPLOY_PROGRESS_PATTERN =
    Regex("^Shod_PROGRESS\\|(\\d+(?:\\.\\d+)?)\\|(.+)$")

/**
 * Parses a Shod_PROGRESS protocol line. Values outside 0..1 are rejected:
 * accepting percentages or arbitrary numbers would silently distort UI state.
 */
internal fun parseDeployProgressEvent(rawLine: String): DeployProgressEvent? {
    val line = rawLine.trim()
    val match = DEPLOY_PROGRESS_PATTERN.matchEntire(line) ?: return null
    val fraction = match.groupValues[1].toFloatOrNull() ?: return null
    val message = match.groupValues[2].trim()
    if (!fraction.isFinite() || fraction !in 0f..1f || message.isEmpty()) return null
    return DeployProgressEvent(fraction, message)
}

/** Any protocol-prefixed line is withheld from ordinary log output, even if malformed. */
internal fun isDeployProgressProtocolLine(rawLine: String): Boolean =
    rawLine.trimStart().startsWith("Shod_PROGRESS")
