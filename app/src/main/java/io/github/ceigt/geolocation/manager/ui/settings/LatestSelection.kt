package io.github.ceigt.geolocation.manager.ui.settings

import java.util.concurrent.atomic.AtomicLong

/** Validate inside the serial writer; discard selections superseded during validation. */
internal class LatestSelection<T>(
    private val enqueue: (suspend () -> Unit) -> Unit,
    private val validate: suspend (T) -> Boolean,
    private val save: suspend (T) -> Unit
) {
    private val revision = AtomicLong()
    fun select(value: T) {
        val selected = revision.incrementAndGet()
        enqueue {
            if (selected == revision.get() && validate(value) && selected == revision.get()) {
                save(value)
            }
        }
    }
}
