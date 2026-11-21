package com.shod.client.deploy.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployStderrPolicyTest {

    @Test
    fun recognizesDebconfWarnings() {
        assertTrue(isBenignDeployStderr("debconf: delaying package configuration"))
        assertTrue(isBenignDeployStderr("dpkg-preconfigure: unable to re-open stdin"))
    }

    @Test
    fun recognizesMissingTerminalWarnings() {
        assertTrue(isBenignDeployStderr("debconf: TERM is not set"))
        assertTrue(isBenignDeployStderr("falling back to frontend: Readline"))
        assertTrue(isBenignDeployStderr("unable to initialize frontend: Teletype"))
    }

    @Test
    fun recognizesSudoPasswordPrompt() {
        assertTrue(isBenignDeployStderr("[sudo] password for deploy:"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertTrue(isBenignDeployStderr("WARNING: package configuration"))
    }

    @Test
    fun preservesUnrelatedErrors() {
        assertFalse(isBenignDeployStderr("Permission denied"))
        assertFalse(isBenignDeployStderr("fatal: disk is full"))
        assertFalse(isBenignDeployStderr(""))
    }
}
