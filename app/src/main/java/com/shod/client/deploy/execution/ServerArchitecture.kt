package com.shod.client.deploy.execution

import java.io.IOException
import java.util.Locale

internal enum class ServerArchitecture(
    val assetName: String,
    val displayName: String,
) {
    AMD64("shod-linux-amd64", "amd64"),
    ARM64("shod-linux-arm64", "ARM64"),
    ARMV7("shod-linux-armv7", "ARM32"),
}

internal fun serverArchitectureForMachine(output: String): ServerArchitecture {
    val machine = output.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
        ?.lowercase(Locale.ROOT)
        ?: throw IOException("VPS не вернул архитектуру через uname -m")
    return when (machine) {
        "x86_64", "amd64" -> ServerArchitecture.AMD64
        "aarch64", "arm64" -> ServerArchitecture.ARM64
        "armv7l", "armv7", "armhf" -> ServerArchitecture.ARMV7
        else -> throw IOException(
            "Архитектура VPS $machine пока не поддерживается. Поддерживаются: x86_64, aarch64, armv7l",
        )
    }
}
