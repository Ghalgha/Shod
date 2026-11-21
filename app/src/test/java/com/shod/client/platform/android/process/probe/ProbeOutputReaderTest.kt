package com.shod.client.platform.android.process.probe

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProbeOutputReaderTest {
    @Test
    fun silentNativeProcessCanBeCancelledWithoutBlockingRead() = runTest {
        val input = object : InputStream() {
            override fun available() = 0
            override fun read(): Int = error("Must never block on a silent process")
        }
        val reader = launch { readProbeOutput(input, { true }) { error("No output expected") } }
        runCurrent()
        reader.cancelAndJoin()
        assertTrue(reader.isCompleted)
    }

    @Test
    fun parsesCompleteAndFinalUtf8Lines() = runTest {
        val lines = mutableListOf<String>()
        readProbeOutput(
            ByteArrayInputStream("TUNCONF:1\r\nготово".toByteArray()),
            { false },
            lines::add
        )
        assertEquals(listOf("TUNCONF:1", "готово"), lines)
    }
}
