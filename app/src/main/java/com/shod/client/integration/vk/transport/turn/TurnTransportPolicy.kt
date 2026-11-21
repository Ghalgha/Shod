package com.shod.client.integration.vk.transport.turn

import com.shod.client.ShodConstants

internal fun shouldConfirmTcpTransport(
    currentTransport: String,
    requestedTransport: String,
    warningAcknowledged: Boolean,
): Boolean =
    !warningAcknowledged &&
        currentTransport != ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS &&
        requestedTransport == ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
