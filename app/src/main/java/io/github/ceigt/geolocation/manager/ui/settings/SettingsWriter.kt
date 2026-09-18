package io.github.ceigt.geolocation.manager.ui.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** A failed write must not cancel subsequent saves or reverse their order. */
internal class SettingsWriter(private val onFailure: (Exception) -> Unit) {
    // Settings are durable user actions. A ViewModel cancellation must not interrupt an already
    // accepted write, and a short burst of map taps must not silently overflow a bounded channel.
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val worker = workerScope.launch {
        for (operation in queue) {
            try { operation() }
            catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                onFailure(IllegalStateException("Setting write was cancelled", cancelled))
            }
            catch (error: Exception) { onFailure(error) }
        }
    }
    init {
        worker.invokeOnCompletion { workerScope.cancel() }
    }
    fun enqueue(operation: suspend () -> Unit): Boolean {
        val accepted = queue.trySend(operation).isSuccess
        if (!accepted) onFailure(IllegalStateException("Settings queue unavailable"))
        return accepted
    }
    fun close() { queue.close() }
    suspend fun join() {
        worker.join()
        workerScope.cancel()
    }
}
