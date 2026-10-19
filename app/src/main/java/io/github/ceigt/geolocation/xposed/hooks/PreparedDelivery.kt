package io.github.ceigt.geolocation.xposed.hooks

/** Preparation failures may retry; a callback attempt must never refund its allowance. */
internal inline fun <T> deliverPrepared(prepare: () -> T, accept: (T) -> Boolean, send: (T) -> Unit): Boolean {
    val payload = prepare()
    if (!accept(payload)) return false
    send(payload)
    return true
}

internal fun suppressNativeGnss(ready: Boolean, active: Boolean, tracked: Boolean, synthetic: Boolean): Boolean =
    ready && active && tracked && !synthetic
