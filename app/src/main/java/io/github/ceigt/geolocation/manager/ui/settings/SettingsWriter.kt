package io.github.ceigt.geolocation.manager.ui.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** A failed write must not cancel subsequent saves or reverse their order. */
internal class SettingsWriter(scope: CoroutineScope, private val onFailure: (Exception) -> Unit) {
    private val queue = Channel<suspend () -> Unit>(64)
    private val worker = scope.launch {
        for (operation in queue) {
            try { withContext(Dispatchers.IO) { operation() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onFailure(error) }
        }
    }
    fun enqueue(operation: suspend () -> Unit) {
        if (!queue.trySend(operation).isSuccess) onFailure(IllegalStateException("Settings queue unavailable"))
    }
    fun close() { queue.close() }
    suspend fun join() = worker.join()
}
