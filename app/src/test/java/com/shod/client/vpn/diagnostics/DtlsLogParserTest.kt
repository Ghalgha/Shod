package com.shod.client.vpn.diagnostics

import com.shod.client.vpn.diagnostics.LogLevel

import org.junit.Assert.*
import org.junit.Test

class DtlsLogParserTest {
    @Test fun reproductionOfSeventyFourAttemptsKeepsSuccessAndFallbackSeparate() {
        val events = List(66) { DtlsLogParser.parse(it + 1, "DTLS-хендшейк успешен (ECDSA P-256)") } +
            List(8) { DtlsLogParser.parse(it + 1, "AUTO fallback -> WRAP-SRTP") }
        val counts = events.groupingBy { it.key }.eachCount()
        assertEquals(66, counts["dtls_success"])
        assertEquals(8, counts["dtls_fallback"])
        assertEquals(LogLevel.LOG, events.last().level)
        assertNotEquals(LogLevel.OK, DtlsLogParser.parse(1, "").level)
    }
    @Test fun failuresRetainWorkerAndNeverBecomeSuccess() {
        val entry = DtlsLogParser.parse(39, "рукопожатие не состоялось (timeout)")
        assertEquals("dtls_err_39", entry.key)
        assertTrue(entry.isError)
        assertEquals(LogLevel.ERR, entry.level)
        assertTrue(entry.message.contains("39"))
    }
    @Test fun negatedSuccessMessagesNeverIncrementSuccess() {
        val messages = listOf(
            "DTLS не завершено",
            "DTLS не был успешно завершен",
            "DTLS неуспешен"
        )
        messages.forEach { message ->
            assertNotEquals(message, "dtls_success", DtlsLogParser.parse(1, message).key)
            assertNotEquals(message, LogLevel.OK, DtlsLogParser.parse(1, message).level)
        }
    }

}
