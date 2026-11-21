package com.shod.client.vpn.diagnostics

import androidx.compose.runtime.Immutable

@Immutable
enum class LogLevel {
    OK,
    DBG,
    NET,
    PING,
    TURN,
    LOG,
    ERR,
}

@Immutable
data class LogEntry(
    val key: String,
    val message: String,
    val count: Int = 1,
    val priority: Int = 99, 
    val isError: Boolean = false,
    val level: LogLevel? = null,
)
