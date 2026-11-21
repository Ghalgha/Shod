package com.shod.client.deploy.version

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerVersionPolicyTest {

    @Test
    fun extractsVersionWithPrefix() {
        assertEquals("2.5.0", comparableServerVersion("v2.5.0"))
    }

    @Test
    fun extractsVersionBeforeBuildMetadata() {
        assertEquals("2.5.0", comparableServerVersion("2.5.0 (build 123)"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("2.5.0", comparableServerVersion("  2.5.0  "))
    }

    @Test
    fun returnsEmptyWhenVersionIsMissing() {
        assertEquals("", comparableServerVersion("unknown"))
    }

    @Test
    fun extractsFirstNumericVersionSequence() {
        assertEquals("2.5.0", comparableServerVersion("server 2.5.0 build 123"))
    }
}
