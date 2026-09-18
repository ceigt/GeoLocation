package io.github.ceigt.geolocation.manager

import io.github.ceigt.geolocation.manager.ui.settings.SettingsWriter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SettingsWriterTest {
    @Test fun failureIsReportedAndLaterWritesContinueInOrder() = runBlocking {
        val writes = mutableListOf<Int>()
        val failures = mutableListOf<Exception>()
        val writer = SettingsWriter { failures.add(it) }
        writer.enqueue { delay(10); writes.add(1) }
        writer.enqueue { throw IllegalStateException("disk failure") }
        writer.enqueue { writes.add(2) }
        writer.close(); writer.join()
        assertEquals(listOf(1, 2), writes)
        assertEquals(1, failures.size)
    }
    @Test fun operationCancellationIsReportedWithoutStoppingTheQueue() = runBlocking {
        val failures = mutableListOf<Exception>()
        val writes = mutableListOf<Int>()
        val writer = SettingsWriter { failures.add(it) }
        writer.enqueue { throw CancellationException() }
        writer.enqueue { writes.add(1) }
        writer.close(); writer.join()
        assertEquals(1, failures.size)
        assertEquals(listOf(1), writes)
    }
    @Test fun acceptedWritesDrainAfterClose() = runBlocking {
        val writes = mutableListOf<Int>()
        val failures = mutableListOf<Exception>()
        val writer = SettingsWriter { failures.add(it) }
        repeat(100) { value -> assertTrue(writer.enqueue { writes.add(value) }) }
        writer.close(); writer.join()
        assertEquals((0 until 100).toList(), writes)
        assertFalse(writer.enqueue { writes.add(101) })
        assertEquals(1, failures.size)
    }
}
