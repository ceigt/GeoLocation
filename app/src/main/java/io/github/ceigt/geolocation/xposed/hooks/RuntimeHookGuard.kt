package io.github.ceigt.geolocation.xposed.hooks

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil

/** Adapter failures must not replay a native call or swallow its permission exceptions. */
internal fun XposedInterface.interceptSafely(method: Method, failure: (Throwable) -> Unit, hooker: Hooker) {
    hook(method).intercept(guardRuntimeHook(failure, { chain ->
        if (method.name in setOf("onLocationChanged", "deliverOnLocationChanged")) {
            isolateHook(failure) {
                val completion = chain.args.getOrNull(1)
                if (completion is Runnable) completion.run()
                else completion?.javaClass?.getMethod("sendResult", android.os.Bundle::class.java)
                    ?.apply { isAccessible = true }?.invoke(completion, null)
            }
        }
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }, hooker, { PreferencesUtil.snapshot().let { !it.isPlaying || !it.enableSystemHooks } }))
}

internal fun guardRuntimeHook(failure: (Throwable) -> Unit, fallback: (Chain) -> Any?, hooker: Hooker,
    passThroughOnFailure: () -> Boolean = { false }): Hooker = Hooker { chain ->
    var nativeFailure: Throwable? = null
    var nativeCompleted = false
    var nativeResult: Any? = null
    val tracked = object : Chain by chain {
        fun call(action: () -> Any?): Any? = try { action().also { nativeResult = it; nativeCompleted = true } } catch (error: Throwable) {
            nativeFailure = error
            throw error
        }
        override fun proceed(): Any? = call { chain.proceed() }
        override fun proceed(args: Array<out Any?>): Any? = call { chain.proceed(args) }
        override fun proceedWith(receiver: Any): Any? = call { chain.proceedWith(receiver) }
        override fun proceedWith(receiver: Any, args: Array<out Any?>): Any? = call { chain.proceedWith(receiver, args) }
    }
    fun recover(error: Throwable): Any? {
        if (nativeFailure === error) throw error
        failure(error)
        if (runCatching(passThroughOnFailure).getOrDefault(false)) return if (nativeCompleted) nativeResult else tracked.proceed()
        return fallback(chain)
    }
    try { hooker.intercept(tracked) }
    catch (error: Exception) { recover(error) }
    catch (error: LinkageError) { recover(error) }
}
