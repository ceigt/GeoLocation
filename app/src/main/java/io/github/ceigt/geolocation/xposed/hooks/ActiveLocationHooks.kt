package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Native requests retain permission/cancellation checks; supplements share their limits. */
internal class ActiveLocationHooks(private val module: XposedInterface) {
    private class Key(val listener: LocationListener, val provider: String) {
        override fun equals(other: Any?) = other is Key && listener === other.listener && provider == other.provider
        override fun hashCode() = 31 * System.identityHashCode(listener) + provider.hashCode()
    }
    private val skippedSignatures = ConcurrentHashMap.newKeySet<String>()
    private val registrationLock = Any()
    private val registrations = ConcurrentHashMap<Key, Registration>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var ticking = false
    private fun active() = PreferencesUtil.snapshot().isPlaying

    private inner class Registration(
        val key: Key, val executor: Executor, val manager: LocationManager,
        val budget: SystemDeliveryBudget, val minimumDistance: Float, val maxNativeUpdates: Int
    ) : LocationListener {
        private val pending = AtomicBoolean(false)
        private val delivery = LocationDeliveryPolicy<Location>(budget, minimumDistance) { a, b -> a.distanceTo(b) }
        private var nativeUpdates = 0L

        override fun onLocationChanged(location: Location) {
            deliver(listOf(location), false, false)
        }

        override fun onLocationChanged(locations: MutableList<Location>) {
            deliver(locations, false, true)
        }

        @Synchronized
        private fun deliver(locations: List<Location>, synthetic: Boolean, batched: Boolean) {
            if (registrations[key] !== this) return
            val now = SystemClock.elapsedRealtime()
            if (budget.expired(now)) { retire(); return }
            if (!synthetic) nativeUpdates += locations.size
            val spoof = active()
            try {
                if (synthetic && (!ApplicationLocationAccess.maySupplement() || !budget.due(now))) return
                val fixes = locations.mapNotNull { if (spoof) ApplicationLocationAccess.replace(it) else it }
                val accepted = delivery.accept(now, fixes, spoof, synthetic)
                if (accepted.isEmpty()) return
                if (batched && Build.VERSION.SDK_INT >= 31) key.listener.onLocationChanged(accepted)
                else accepted.forEach(key.listener::onLocationChanged)
            } finally {
                if (budget.expired(now) || nativeUpdates >= maxNativeUpdates) retire()
            }
        }

        override fun onProviderEnabled(provider: String) = key.listener.onProviderEnabled(provider)
        override fun onProviderDisabled(provider: String) = key.listener.onProviderDisabled(provider)
        override fun onFlushComplete(requestCode: Int) {
            if (Build.VERSION.SDK_INT >= 31) key.listener.onFlushComplete(requestCode)
        }

        fun retire() {
            registrations.remove(key, this)
            runCatching { manager.removeUpdates(this) }
        }

        fun dispatch() {
            val now = SystemClock.elapsedRealtime()
            if (budget.expired(now)) { retire(); return }
            if (!active() || !budget.due(now) || !pending.compareAndSet(false, true)) return
            try {
                executor.execute {
                    try {
                        if (registrations[key] === this && active() && ApplicationLocationAccess.maySupplement())
                            deliver(listOf(LocationUtil.createFakeLocation(provider = key.provider)), true, false)
                    } finally { pending.set(false) }
                }
            } catch (error: Exception) {
                pending.set(false)
                retire()
                module.log(Log.WARN, TAG, "Callback executor failed: ${error.javaClass.simpleName}")
            }
        }

    }

    private val tick = object : Runnable {
        override fun run() {
            if (registrations.isEmpty()) { ticking = false; return }
            registrations.values.toList().forEach { it.dispatch() }
            handler.postDelayed(this, 1000)
        }
    }

    fun initHooks() {
        LocationManager::class.java.declaredMethods.filter {
            it.name in setOf("requestLocationUpdates", "requestSingleUpdate", "removeUpdates", "getCurrentLocation", "requestFlush")
        }.forEach { method ->
            isolateHook({ module.log(Log.WARN, TAG, "${method.name}: ${it.javaClass.simpleName}") }) {
                if (method.name == "getCurrentLocation" && method.parameterTypes.contains(Consumer::class.java)) {
                    module.hook(method).intercept { chain ->
                        val index = chain.args.indexOfFirst { it is Consumer<*> }
                        if (index < 0) return@intercept chain.proceed()
                        @Suppress("UNCHECKED_CAST")
                        val original = chain.args[index] as Consumer<Location?>
                        val args = chain.args.toTypedArray()
                        args[index] = Consumer<Location?> { fix ->
                            original.accept(if (fix != null && active()) ApplicationLocationAccess.replace(fix) else fix)
                        }
                        chain.proceed(args)
                    }
                } else if (method.parameterTypes.contains(LocationListener::class.java)) {
                    module.hook(method).intercept { chain ->
                        synchronized(registrationLock) {
                        val index = chain.args.indexOfFirst { it is LocationListener }
                        val listener = chain.args.getOrNull(index) as? LocationListener ?: return@intercept chain.proceed()
                        if (listener is Registration) return@intercept chain.proceed()
                        ApplicationLocationAccess.init(chain.thisObject as LocationManager)
                        if (method.name == "removeUpdates") {
                            registrations.values.filter { it.key.listener === listener }.forEach { it.retire() }
                            return@intercept chain.proceed()
                        }
                        val provider = chain.args.filterIsInstance<String>().firstOrNull()
                        if (method.name == "requestFlush") {
                            val entry = provider?.let { registrations[Key(listener, it)] } ?: return@intercept chain.proceed()
                            return@intercept chain.proceed(chain.args.toTypedArray().apply { this[index] = entry })
                        }
                        if (provider == null) {
                            if (skippedSignatures.add(method.toGenericString())) {
                                module.log(Log.INFO, "[ActiveLocationHooks]", "Native-only overload: ${method.toGenericString()}")
                            }
                            return@intercept chain.proceed()
                        }
                        val request = chain.args.firstOrNull { it?.javaClass?.name == "android.location.LocationRequest" }
                        fun number(name: String, fallback: Long) = runCatching {
                            (request?.javaClass?.getMethod(name)?.invoke(request) as? Number)?.toLong() ?: fallback
                        }.getOrDefault(fallback)
                        val interval = number("getIntervalMillis", chain.args.filterIsInstance<Long>().firstOrNull() ?: 1000L)
                        if (provider == "passive" || interval == Long.MAX_VALUE) return@intercept chain.proceed()
                        val maxUpdates = if (method.name == "requestSingleUpdate") 1 else number("getMaxUpdates", Int.MAX_VALUE.toLong()).toInt()
                        val distance = runCatching {
                            (request?.javaClass?.getMethod("getMinUpdateDistanceMeters")?.invoke(request) as? Number)?.toFloat()
                        }.getOrNull() ?: chain.args.filterIsInstance<Float>().firstOrNull() ?: 0f
                        val deliveryHandler = Handler(chain.args.filterIsInstance<Looper>().firstOrNull()
                            ?: Looper.myLooper() ?: Looper.getMainLooper())
                        val executor = chain.args.filterIsInstance<Executor>().firstOrNull() ?: Executor {
                            check(deliveryHandler.post(it)) { "Delivery looper has stopped" }
                        }
                        val key = Key(listener, provider)
                        val entry = Registration(key, executor, chain.thisObject as LocationManager,
                            SystemDeliveryBudget(SystemClock.elapsedRealtime(), interval,
                                number("getDurationMillis", Long.MAX_VALUE), maxUpdates, 0L), distance, maxUpdates)
                        val old = registrations.put(key, entry)
                        val args = chain.args.toTypedArray().apply { this[index] = entry }
                        try {
                            val result = chain.proceed(args)
                            old?.retire()
                            handler.post { if (!ticking) { ticking = true; handler.post(tick) } }
                            result
                        } catch (error: Throwable) {
                            if (old == null) registrations.remove(key, entry) else registrations.replace(key, entry, old)
                            throw error
                        }
                        }
                    }
                }
            }
        }
    }

    private companion object { const val TAG = "[ActiveLocationHooks]" }
}
