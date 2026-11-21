package com.shod.client.deploy.execution

/**
 * Filters known non-actionable messages emitted by Debian package tools and sudo.
 *
 * This policy is used only for stderr presentation; it does not suppress command
 * failures or change the remote process exit status.
 */
private val BENIGN_DEPLOY_STDERR = Regex(
    "debconf|dpkg-preconfigure|TERM is not set|falling back to frontend|" +
        "unable to re-open stdin|controlling tty|unable to initialize frontend|" +
        "^Warning:|frontend:\\s*(Readline|Teletype|Dialog)|\\[sudo\\] password for",
    RegexOption.IGNORE_CASE,
)

internal fun isBenignDeployStderr(line: String): Boolean =
    BENIGN_DEPLOY_STDERR.containsMatchIn(line)
