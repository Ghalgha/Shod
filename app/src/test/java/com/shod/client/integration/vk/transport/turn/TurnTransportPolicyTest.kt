package com.shod.client.integration.vk.transport.turn

import com.shod.client.ShodConstants

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnTransportPolicyTest {
    @Test
    fun tcpWarningAppearsOnlyWhenEnteringTcpWithoutAcknowledgement() {
        assertEquals(
            true,
            shouldConfirmTcpTransport(
                currentTransport = ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT,
                requestedTransport = ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmTcpTransport(
                currentTransport = ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                requestedTransport = ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmTcpTransport(
                currentTransport = ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT,
                requestedTransport = ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                warningAcknowledged = true,
            ),
        )
    }
}
