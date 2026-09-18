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
import io.github.ceigt.geolocation.manager.ui.map.CoordinateTransform
import io.github.ceigt.geolocation.manager.ui.map.GeoPoint

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
    private val listenerProxies = WeakListenerRegistry()
    private val installedMethods = mutableSetOf<Method>()

    private val reportedEvents = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun reportOnce(event: String) {
        if (reportedEvents.addBounded(event)) module.log(Log.INFO, tag, event)
    }

    fun initHooks(loader: ClassLoader = classLoader) {
        TencentSdkDiscovery.namespaces.forEach { namespace ->
            runCatching {
                val sdk = TencentSdkDiscovery.resolve(namespace, loader)
                hookListenerRegistration(sdk.manager, sdk.listener, sdk.location)
                hookListenerRemoval(sdk.manager, sdk.listener)
                hookLastKnownLocation(sdk.manager, sdk.location)
                reportOnce("SDK available: $namespace; total hooked methods=${installedMethods.size}")
            }.onFailure {
                reportOnce("Optional SDK unavailable: $namespace (${it.javaClass.simpleName})")
            }
        }
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
                    if (isOurListenerProxy(originalListener)) return@hook chain.proceed()
                    val owner = chain.thisObject ?: return@hook chain.proceed()
                    val single = method.name == "requestSingleFreshLocation"
                    val proxyListener = listenerProxies.acquire(owner, originalListener, single) {
                        Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass),
                            TencentListenerHandler(originalListener, locationClass, single))
                    }
                    val newArgs = chain.args.toTypedArray()
                    newArgs[listenerIndex] = proxyListener
                    try {
                        val result = chain.proceed(newArgs)
                        listenerProxies.registered(proxyListener, result !is Number || result.toInt() == 0)
                        reportOnce("Listener registration: ${managerClass.name}.${method.name}; result=$result")
                        result
                    } catch (error: Throwable) {
                        listenerProxies.registered(proxyListener, false)
                        throw error
                    }
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
                    val owner = chain.thisObject ?: return@hook chain.proceed()
                    val proxies = listenerProxies.proxies(owner, originalListener)
                    if (proxies.isEmpty()) return@hook chain.proceed()
                    var result: Any? = null
                    for ((index, proxyListener) in proxies.withIndex()) {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[listenerIndex] = proxyListener
                        result = if (index == 0) chain.proceed(newArgs) else invokeOriginal(method, owner, newArgs)
                        listenerProxies.remove(proxyListener)
                    }
                    result
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

    private fun isOurListenerProxy(value: Any): Boolean =
        Proxy.isProxyClass(value.javaClass) &&
            runCatching { Proxy.getInvocationHandler(value) is TencentListenerHandler }
                .getOrDefault(false)

    private inner class TencentListenerHandler(
        private val original: Any,
        private val locationClass: Class<*>,
        private val single: Boolean
    ) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            if (method.declaringClass == Any::class.java) {
                return invokeObjectMethod(proxy, method.name, args)
            }

            try {
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
                reportOnce("SDK callback replaced: ${locationClass.name}")
            }
                return invokeOriginal(method, original, forwardedArgs)
            } finally {
                if (single && method.name == "onLocationChanged") listenerProxies.remove(proxy)
            }
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

            // Tencent exposes WGS-84/GCJ-02 only. Convert BD-09 at this interface boundary.
            val point = synchronized(LocationUtil) {
                LocationUtil.updateLocation(config)
                val lat = LocationUtil.latitude
                val lon = LocationUtil.longitude
                if (config.appCoordinateSystems[LocationUtil.targetPackageName] == CoordinateSystem.BD09) {
                    CoordinateTransform.bd09ToGcj02(lat, lon)
                } else GeoPoint(lat, lon)
            }
            when (method.name) {
                "getLatitude" -> point.latitude
                "getLongitude" -> point.longitude
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
        synchronized(installedMethods) {
            if (method in installedMethods) return
            runCatching {
                module.hook(method).intercept(interceptor)
                installedMethods.add(method)
            }.onFailure {
                module.log(Log.WARN, tag, "Could not hook ${method.name}: ${it.message}")
            }
        }
    }

    private companion object {
        const val TENCENT_COORDINATE_WGS84 = 0
        const val TENCENT_COORDINATE_GCJ02 = 1
        val REQUEST_METHOD_NAMES = setOf("requestLocationUpdates", "requestSingleFreshLocation")
    }
}
