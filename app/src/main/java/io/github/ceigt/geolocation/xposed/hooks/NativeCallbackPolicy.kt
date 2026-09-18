package io.github.ceigt.geolocation.xposed.hooks

import java.util.WeakHashMap

/** Only positively identified system/manager callbacks may bypass an active native guard. */
internal class NativeCallbackPolicy<T : Any> {
    private val exemptions = WeakHashMap<T, Boolean>()

    @Synchronized fun record(token: T, exempt: Boolean) {
        if (exempt) exemptions[token] = true else exemptions.remove(token)
    }

    @Synchronized fun mayPassUntracked(active: Boolean, token: T?): Boolean =
        !active || (token != null && exemptions[token] == true)
}
