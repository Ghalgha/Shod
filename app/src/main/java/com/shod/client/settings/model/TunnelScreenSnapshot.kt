package com.shod.client.settings.model

/**
 * One persisted revision: profile identity and connection credentials are read together.
 * This prevents the UI from combining values from different active profiles during a switch.
 */
internal data class TunnelScreenSnapshot(
    val profile: Int,
    val name: String,
    val connection: ProfileConnectionSnapshot,
    val hashes: String,
    val hashMode: String,
    val authMode: String,
    val token: String,
    val srtp: String,
    val transport: String,
    val subnet: String,
    val ipLimit: String,
    val hashResults: String,
    val removedOwnerHashes: Set<String>,
    val autoJsAcknowledged: Boolean,
    val tcpAcknowledged: Boolean,
)
