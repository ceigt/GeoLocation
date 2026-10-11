package io.github.ceigt.geolocation.xposed.hooks

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** A delivered fix remains a snapshot even if settings or the SDK's object later change. */
internal fun frozenLocationProxy(type: Class<*>, original: Any, overrides: Map<String, Any?>): Any {
    fun invoke(method: Method, args: Array<out Any?>?): Any? = try {
        method.invoke(original, *(args ?: emptyArray()))
    } catch (error: InvocationTargetException) { throw error.targetException }
    val values = type.methods.filter { it.parameterCount == 0 && it.declaringClass != Any::class.java }
        .associateWith { method ->
            try {
                Result.success(if (method.name in overrides) typedLocationValue(method.returnType, overrides[method.name])
                    else invoke(method, null))
            } catch (error: Exception) { Result.failure(error) }
        }
    return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when {
            method.declaringClass == Any::class.java -> when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "GeoLocation location snapshot"
                else -> null
            }
            method in values -> values.getValue(method).getOrThrow()
            else -> invoke(method, args)
        }
    }
}

private fun typedLocationValue(type: Class<*>, value: Any?): Any? = when {
    type == Boolean::class.javaPrimitiveType && value is Number -> value.toInt() != 0
    value !is Number -> value
    type == Float::class.javaPrimitiveType -> value.toFloat()
    type == Double::class.javaPrimitiveType -> value.toDouble()
    type == Long::class.javaPrimitiveType -> value.toLong()
    type == Int::class.javaPrimitiveType -> value.toInt()
    else -> value
}
