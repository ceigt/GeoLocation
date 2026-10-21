package io.github.ceigt.geolocation.xposed.hooks

/** Cache the verified constructor, never the slice: Android permits each slice to be parceled once. */
internal fun emptyScanPayloadFactory(type: Class<*>): () -> Any {
    val empty = emptyList<Any>()
    if (type.isInstance(empty)) return { emptyList<Any>() }
    val create: () -> Any = runCatching {
        val constructor = type.getDeclaredConstructor(List::class.java).apply { isAccessible = true }
        constructor.newInstance(empty) // Probe before installing the Binder adapter.
        val factory: () -> Any = { constructor.newInstance(empty) }
        factory
    }.getOrElse { constructorFailure ->
        val method = runCatching { type.getMethod("emptyList") }.getOrElse { throw constructorFailure }
        val factory: () -> Any = { checkNotNull(method.invoke(null)) }
        factory
    }
    val first = create()
    check(type.isInstance(first) && first !== create()) { "Empty scan slice must have a fresh compatible instance" }
    val list = type.getMethod("getList").invoke(first) as? List<*>
    check(list != null && list.isEmpty()) { "Empty scan result could not be verified" }
    return create
}
