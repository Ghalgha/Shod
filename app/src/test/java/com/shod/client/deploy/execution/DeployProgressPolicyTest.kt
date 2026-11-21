package com.shod.client.deploy.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployProgressPolicyTest {

    @Test
    fun parsesValidProgressEvent() {
        val event = parseDeployProgressEvent("Shod_PROGRESS|0.45|Установка сервера")
        assertEquals(0.45f, event?.fraction)
        assertEquals("Установка сервера", event?.message)
    }

    @Test
    fun trimsWhitespaceAroundProtocolFields() {
        val event = parseDeployProgressEvent("  Shod_PROGRESS|1| Готово  ")
        assertEquals(1f, event?.fraction)
        assertEquals("Готово", event?.message)
    }

    @Test
    fun rejectsMalformedOrOutOfRangeEvents() {
        assertNull(parseDeployProgressEvent("Shod_PROGRESS|abc|Шаг"))
        assertNull(parseDeployProgressEvent("Shod_PROGRESS|1.1|Шаг"))
        assertNull(parseDeployProgressEvent("Shod_PROGRESS|-0.1|Шаг"))
        assertNull(parseDeployProgressEvent("Shod_PROGRESS|0.5|   "))
        assertNull(parseDeployProgressEvent("Shod_PROGRESS|0.5"))
        assertNull(parseDeployProgressEvent("ordinary output"))
    }

    @Test
    fun suppressesMalformedProtocolLinesFromOrdinaryLogs() {
        assertTrue(isDeployProgressProtocolLine("Shod_PROGRESS|broken"))
        assertTrue(isDeployProgressProtocolLine("  Shod_PROGRESS|0.2|Шаг"))
        assertFalse(isDeployProgressProtocolLine("обычная строка"))
    }

    @Test
    fun outputParserDelegatesProgressProtocolRecognition() {
        assertNull(parseDeployOutputLine("Shod_PROGRESS|0.2|Шаг"))
        assertNull(parseDeployOutputLine("Shod_PROGRESS|malformed"))
        assertEquals("Обычный вывод", parseDeployOutputLine("Обычный вывод")?.message)
    }
}
