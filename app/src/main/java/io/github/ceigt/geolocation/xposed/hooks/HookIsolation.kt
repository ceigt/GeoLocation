package io.github.ceigt.geolocation.xposed.hooks

/** Optional compatibility adapters must fail independently, without swallowing VM failures. */
internal inline fun isolateHook(onFailure: (Throwable) -> Unit, install: () -> Unit) {
    try {
        install()
    } catch (error: Exception) {
        onFailure(error)
    } catch (error: LinkageError) {
        onFailure(error)
    }
}
