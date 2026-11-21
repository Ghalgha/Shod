package com.shod.client.deploy.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DeployCommandPolicyTest {
    @Test
    fun environmentValuesPreserveSystemdEscapingAndDockerNewlineNormalization() {
        assertEquals("\"line1 line2\\\\path\\\"quoted\"", deployEnvironmentValue("line1\nline2\\path\"quoted", false))
        assertEquals("line1 line2", deployEnvironmentValue("line1\nline2\r", true))
    }

    @Test
    fun deployModeMatchesSelectedRuntime() {
        assertEquals("docker", deployMode(true))
        assertEquals("systemd", deployMode(false))
    }

    @Test
    fun rootCommandQuotesShellInputAndSupportsRootAndSudo() {
        val command = rootCommand("echo 'safe'", "p'ass")
        assertTrue(command.contains("echo '\"'\"'safe'"))
        assertTrue(command.contains("printf '%s\\n'"))
        assertTrue(command.contains("sudo -S -p '' bash -c"))
        assertTrue(command.contains("id -u"))
    }
}
