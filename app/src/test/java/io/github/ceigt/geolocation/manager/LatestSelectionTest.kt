package io.github.ceigt.geolocation.manager

import io.github.ceigt.geolocation.manager.ui.settings.LatestSelection
import io.github.ceigt.geolocation.manager.ui.settings.SettingsWriter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LatestSelectionTest {
    @Test fun newerSelectionWinsWhileValidationIsBlocked() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<String>()
        val writer = SettingsWriter(this) { throw AssertionError(it) }
        val selection = LatestSelection<String>(writer::enqueue, {
            if (it == "system") { entered.complete(Unit); release.await() }
            true
        }, { saved.add(it) })
        selection.select("system")
        entered.await()
        selection.select("mock")
        release.complete(Unit)
        writer.close(); writer.join()
        assertEquals(listOf("mock"), saved)
    }
    @Test fun failedSaveDoesNotPreventNextSelection() = runBlocking {
        val failed = CompletableDeferred<Unit>()
        val failures = mutableListOf<Exception>()
        val saved = mutableListOf<String>()
        val writer = SettingsWriter(this) { failures.add(it); failed.complete(Unit) }
        val selection = LatestSelection<String>(writer::enqueue, { true }, {
            if (it == "system") error("disk failure")
            saved.add(it)
        })
        selection.select("system")
        failed.await()
        selection.select("app")
        writer.close(); writer.join()
        assertEquals(listOf("app"), saved)
        assertEquals(1, failures.size)
    }
}
