package io.github.ceigt.geolocation.xposed.hooks

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Cache misses too. Class identity keeps members from different loaders isolated. */
internal class ReflectionCache {
    private data class Key(val type: Class<*>, val name: String, val parameters: List<Class<*>> = emptyList())
    private data class Lookup<T>(val member: T?)
    private val fields = ConcurrentHashMap<Key, Lookup<Field>>()
    private val methods = ConcurrentHashMap<Key, Lookup<Method>>()

    fun field(type: Class<*>, name: String): Field? = fields.computeIfAbsent(Key(type, name)) {
        var current: Class<*>? = type
        var found: Field? = null
        while (current != null && found == null) {
            try { found = current.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { current = current.superclass }
        }
        Lookup(found)
    }.member

    fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method? =
        methods.computeIfAbsent(Key(type, name, parameters.toList())) {
            var current: Class<*>? = type
            var found: Method? = null
            while (current != null && found == null) {
                try { found = current.getDeclaredMethod(name, *parameters).apply { isAccessible = true } }
                catch (_: NoSuchMethodException) { current = current.superclass }
            }
            Lookup(found ?: type.methods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(parameters)
            }?.apply { isAccessible = true })
        }.member
}
