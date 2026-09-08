package io.github.ceigt.geolocation.xposed.hooks

import android.util.Log
import io.github.ceigt.geolocation.data.CoordinateSystem
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

/**
 * Adapts Tencent Location SDK callbacks used by WeChat and WeCom.
 *
 * Tencent's public callback carries a TencentLocation interface rather than an Android Location,
 * so framework-only hooks do not affect values read from that object. Listener proxies preserve
 * the SDK's normal acquisition/error flow and replace only the reported location fields.
 */
internal class TencentLocationHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[TencentLocationHooks]"
    private val listenerProxies = IdentityHashMap<Any, Any>()

    fun initHooks() {
        val managerClass = findClass(TENCENT_MANAGER_CLASS) ?: return
        val listenerClass = findClass(TENCENT_LISTENER_CLASS) ?: return
        val locationClass = findClass(TENCENT_LOCATION_CLASS) ?: return

        hookListenerRegistration(managerClass, listenerClass, locationClass)
        hookListenerRemoval(managerClass, listenerClass)
        hookLastKnownLocation(managerClass, locationClass)
        module.log(Log.INFO, tag, "Tencent Location SDK compatibility hooks installed.")
    }

    private fun hookListenerRegistration(
        managerClass: Class<*>,
        listenerClass: Class<*>,
        locationClass: Class<*>
    ) {
        managerClass.declaredMethods
            .filter { method ->
                method.name in REQUEST_METHOD_NAMES &&
                    method.parameterTypes.any { it == listenerClass }
            }
            .forEach { method ->
                hook(method) { chain ->
                    val listenerIndex = chain.args.indexOfFirst(listenerClass::isInstance)
                    if (listenerIndex < 0) return@hook chain.proceed()

                    val originalListener = chain.args[listenerIndex] ?: return@hook chain.proceed()
                    val proxyListener = getOrCreateListenerProxy(
                        originalListener,
                        listenerClass,
                        locationClass
                    )
                    if (proxyListener === originalListener) return@hook chain.proceed()

                    val newArgs = chain.args.toTypedArray()
                    newArgs[listenerIndex] = proxyListener
                    chain.proceed(newArgs)
                }
            }
    }

    private fun hookListenerRemoval(managerClass: Class<*>, listenerClass: Class<*>) {
        managerClass.declaredMethods
            .filter { method ->
                method.name == "removeUpdates" &&
                    method.parameterTypes.any { it == listenerClass }
            }
            .forEach { method ->
                hook(method) { chain ->
                    val listenerIndex = chain.args.indexOfFirst(listenerClass::isInstance)
                    if (listenerIndex < 0) return@hook chain.proceed()

                    val originalListener = chain.args[listenerIndex] ?: return@hook chain.proceed()
                    val proxyListener = synchronized(listenerProxies) {
                        listenerProxies.remove(originalListener)
                    } ?: return@hook chain.proceed()

                    val newArgs = chain.args.toTypedArray()
                    newArgs[listenerIndex] = proxyListener
                    chain.proceed(newArgs)
                }
            }
    }

    private fun hookLastKnownLocation(managerClass: Class<*>, locationClass: Class<*>) {
        managerClass.declaredMethods
            .filter { it.name == "getLastKnownLocation" && it.parameterTypes.isEmpty() }
            .forEach { method ->
                hook(method) { chain ->
                    val result = chain.proceed()
                    if (
                        result != null &&
                        locationClass.isInstance(result) &&
                        PreferencesUtil.snapshot().isPlaying
                    ) {
                        wrapLocation(result, locationClass)
                    } else {
                        result
                    }
                }
            }
    }

    private fun getOrCreateListenerProxy(
        original: Any,
        listenerClass: Class<*>,
        locationClass: Class<*>
    ): Any {
        if (isOurListenerProxy(original)) return original

        return synchronized(listenerProxies) {
            listenerProxies[original] ?: Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass),
                TencentListenerHandler(original, locationClass)
            ).also { listenerProxies[original] = it }
        }
    }

    private fun isOurListenerProxy(value: Any): Boolean =
        Proxy.isProxyClass(value.javaClass) &&
            runCatching { Proxy.getInvocationHandler(value) is TencentListenerHandler }
                .getOrDefault(false)

    private inner class TencentListenerHandler(
        private val original: Any,
        private val locationClass: Class<*>
    ) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            if (method.declaringClass == Any::class.java) {
                return invokeObjectMethod(proxy, method.name, args)
            }

            val forwardedArgs = args?.let { source ->
                Array<Any?>(source.size) { index -> source[index] }
            }
            if (
                method.name == "onLocationChanged" &&
                forwardedArgs != null &&
                forwardedArgs.isNotEmpty() &&
                forwardedArgs[0] != null &&
                locationClass.isInstance(forwardedArgs[0]) &&
                PreferencesUtil.snapshot().isPlaying
            ) {
                forwardedArgs[0] = wrapLocation(forwardedArgs[0]!!, locationClass)
            }
            return invokeOriginal(method, original, forwardedArgs)
        }
    }

    private fun wrapLocation(original: Any, locationClass: Class<*>): Any =
        Proxy.newProxyInstance(
            locationClass.classLoader,
            arrayOf(locationClass)
        ) { proxy, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance invokeObjectMethod(proxy, method.name, args)
            }

            val config = PreferencesUtil.snapshot()
            if (!config.isPlaying) {
                return@newProxyInstance invokeOriginal(method, original, args)
            }

            LocationUtil.updateLocation(config)
            when (method.name) {
                "getLatitude" -> LocationUtil.latitude
                "getLongitude" -> LocationUtil.longitude
                "getAltitude" -> if (config.useAltitude) {
                    LocationUtil.altitude
                } else {
                    invokeOriginal(method, original, args)
                }
                "getAccuracy" -> if (config.useAccuracy) {
                    LocationUtil.accuracy
                } else {
                    invokeOriginal(method, original, args)
                }
                "getSpeed" -> if (config.useSpeed) {
                    LocationUtil.speed
                } else {
                    invokeOriginal(method, original, args)
                }
                "getTime" -> System.currentTimeMillis()
                "getCoordinateType" -> tencentCoordinateType(config)
                "isMockGps" -> 0
                else -> invokeOriginal(method, original, args)
            }
        }

    private fun tencentCoordinateType(config: PreferencesUtil.PreferencesSnapshot): Int {
        val configured = config.appCoordinateSystems[LocationUtil.targetPackageName]
            ?: CoordinateSystem.WGS84
        return if (configured == CoordinateSystem.WGS84) {
            TENCENT_COORDINATE_WGS84
        } else {
            TENCENT_COORDINATE_GCJ02
        }
    }

    private fun invokeObjectMethod(proxy: Any, name: String, args: Array<out Any?>?): Any? =
        when (name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "GeoLocation Tencent SDK proxy"
            else -> null
        }

    private fun invokeOriginal(
        method: Method,
        target: Any,
        args: Array<out Any?>?
    ): Any? = try {
        method.invoke(target, *(args ?: emptyArray<Any?>()))
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    private fun hook(
        method: Method,
        interceptor: Hooker
    ) {
        runCatching {
            module.hook(method).intercept(interceptor)
        }.onFailure {
            module.log(Log.WARN, tag, "Could not hook ${method.name}: ${it.message}")
        }
    }

    private fun findClass(name: String): Class<*>? = runCatching {
        Class.forName(name, false, classLoader)
    }.getOrElse {
        module.log(Log.INFO, tag, "Optional Tencent class unavailable: $name")
        null
    }

    private companion object {
        const val TENCENT_MANAGER_CLASS = "com.tencent.map.geolocation.TencentLocationManager"
        const val TENCENT_LISTENER_CLASS = "com.tencent.map.geolocation.TencentLocationListener"
        const val TENCENT_LOCATION_CLASS = "com.tencent.map.geolocation.TencentLocation"
        const val TENCENT_COORDINATE_WGS84 = 0
        const val TENCENT_COORDINATE_GCJ02 = 1
        val REQUEST_METHOD_NAMES = setOf("requestLocationUpdates", "requestSingleFreshLocation")
    }
}
