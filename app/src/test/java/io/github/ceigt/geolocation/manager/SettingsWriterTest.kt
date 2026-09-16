package io.github.ceigt.geolocation.manager

import io.github.ceigt.geolocation.manager.ui.settings.SettingsWriter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SettingsWriterTest {
    @Test fun failureIsReportedAndLaterWritesContinueInOrder() = runBlocking {
        val writes = mutableListOf<Int>()
        val failures = mutableListOf<Exception>()
        val writer = SettingsWriter(this) { failures.add(it) }
        writer.enqueue { delay(10); writes.add(1) }
        writer.enqueue { throw IllegalStateException("disk failure") }
        writer.enqueue { writes.add(2) }
        writer.close(); writer.join()
        assertEquals(listOf(1, 2), writes)
        assertEquals(1, failures.size)
    }
    @Test fun cancellationIsNotReportedAsSaveFailure() = runBlocking {
        val failures = mutableListOf<Exception>()
        val writer = SettingsWriter(this) { failures.add(it) }
        writer.enqueue { throw CancellationException() }
        writer.close(); writer.join()
        assertTrue(failures.isEmpty())
    }
}
