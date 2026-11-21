package com.shod.client.deploy.execution

private fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

private fun systemdEnvironmentValue(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n', '\r' -> append(' ')
            else -> append(ch)
        }
    }
    append('"')
}

private fun dockerEnvironmentValue(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ')

internal fun deployEnvironmentValue(value: String, installInDocker: Boolean): String =
    if (installInDocker) dockerEnvironmentValue(value) else systemdEnvironmentValue(value)

internal fun deployMode(installInDocker: Boolean): String =
    if (installInDocker) "docker" else "systemd"

internal fun rootCommand(command: String, password: String? = null): String {
    val quoted = shellQuote(command)
    val sudoCmd = if (!password.isNullOrEmpty()) {
        val passQuoted = shellQuote(password)
        "printf '%s\\n' $passQuoted | sudo -S -p '' bash -c $quoted"
    } else {
        "sudo -S -p '' bash -c $quoted"
    }
    return "if [ \"\$(id -u)\" = \"0\" ]; then bash -c $quoted; " +
        "elif command -v sudo >/dev/null 2>&1; then $sudoCmd; " +
        "else echo 'error: root privileges required and sudo not found'; exit 1; fi"
}
