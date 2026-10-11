package io.github.ceigt.geolocation.xposed.hooks

internal fun callbackRetryDelay(failures: Int): Long = minOf(30_000L, 1_000L shl (failures.coerceIn(1, 6) - 1))
