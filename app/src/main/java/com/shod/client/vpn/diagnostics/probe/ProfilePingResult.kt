package com.shod.client.vpn.diagnostics.probe

/** Sanitized outcome of checking a saved VPN profile through an isolated tunnel. */
internal sealed interface ProfilePingResult {
    data class Success(val milliseconds: Int) : ProfilePingResult
    data class Failure(val reason: String) : ProfilePingResult
}
