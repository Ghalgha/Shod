package com.shod.client.connection.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionStartPolicyTest {
    @Test
    fun tokenFromParticipantLinkIsReadyWithoutStoredOwnerToken() {
        assertEquals(true, ConnectionStartPolicy.hasReadyVkCredentials(true, 0, true, true, false, 0))
    }

    @Test
    fun participantLinkUsesExplicitlyEnteredHashesWhenLinkHasNoVkCredentials() {
        assertEquals(true, ConnectionStartPolicy.hasReadyVkCredentials(true, 0, false, false, false, 1))
    }

    @Test
    fun participantLinkWithoutCredentialsIsBlockedUntilUserEntersThem() {
        assertEquals(false, ConnectionStartPolicy.hasReadyVkCredentials(true, 0, false, false, false, 0))
    }

    @Test
    fun validParticipantLinkDoesNotRequireLocalPeerOrPassword() {
        assertNull(ConnectionStartPolicy.blocker(true, true, false, false, false, hashesReady = true))
    }

    @Test
    fun missingParticipantHashesAreReportedFirst() {
        assertEquals(
            ConnectionStartBlocker.Hashes,
            ConnectionStartPolicy.blocker(true, true, false, false, false, hashesReady = false),
        )
    }

    @Test
    fun ownerValidationIdentifiesTheFirstMissingSetting() {
        assertEquals(
            ConnectionStartBlocker.Server,
            ConnectionStartPolicy.blocker(false, false, false, true, true, hashesReady = true),
        )
        assertNull(ConnectionStartPolicy.blocker(false, false, true, true, false, hashesReady = true))
    }

    @Test
    fun unreadableStoredSecretDoesNotBlockPasswordlessConnection() {
        assertNull(
            ConnectionStartPolicy.blocker(
                false, false, true, true, false,
                connectionPasswordReadable = false,
                hashesReady = true,
            ),
        )
    }

    @Test
    fun linkModeDoesNotDependOnLocalEncryptedPassword() {
        assertNull(
            ConnectionStartPolicy.blocker(
                true, true, false, false, false,
                connectionPasswordReadable = false,
                hashesReady = true,
            ),
        )
    }
}
