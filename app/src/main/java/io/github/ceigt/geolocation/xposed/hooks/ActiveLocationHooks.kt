package io.github.ceigt.geolocation.xposed.hooks

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.ceigt.geolocation.xposed.utils.LocationUtil
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Keeps normal provider registration and supplements it with fresh simulated callbacks. */
internal class ActiveLocationHooks(private val module: XposedInterface) {
    private val registry = ListenerRegistry<LocationListener, Registration>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var ticking = false // accessed only on handler
    private val reported = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun active(): Boolean = PreferencesUtil.snapshot().let {
        it.isPlaying && it.lastClickedLocation != null
    }

    private fun report(event: String) {
        if (reported.add(event)) module.log(Log.INFO, TAG, event)
    }

    private inner class Registration(
        val original: LocationListener,
        val provider: String,
        val executor: Executor,
        val single: Boolean,
        val manager: LocationManager
    ) : LocationListener {
        val pending = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)

        override fun onLocationChanged(location: Location) {
            if (single && !delivered.compareAndSet(false, true)) return
            original.onLocationChanged(if (active()) {
                report("framework callback replaced")
                LocationUtil.createFakeLocation(location)
            } else location)
            if (single) retire()
        }

        override fun onProviderEnabled(provider: String) = original.onProviderEnabled(provider)
        override fun onProviderDisabled(provider: String) = original.onProviderDisabled(provider)
        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) =
            original.onStatusChanged(provider, status, extras)

        fun retire() {
            if (registry.isCurrent(original, this)) registry.remove(original)
            if (single) runCatching { manager.removeUpdates(this) }
        }

        fun dispatch() {
            if (!pending.compareAndSet(false, true)) return
            try {
                executor.execute {
                    try {
                        if (registry.isCurrent(original, this) && active() &&
                            (!single || delivered.compareAndSet(false, true))) {
                            original.onLocationChanged(LocationUtil.createFakeLocation(provider = provider))
                            report("active framework callback delivered")
                            if (single) retire()
                        }
                    } catch (error: Exception) {
                        report("callback failed: ${error.javaClass.simpleName}")
                        retire()
                    } finally { pending.set(false) }
                }
            } catch (error: Exception) {
                pending.set(false)
                retire()
                report("executor rejected callback: ${error.javaClass.simpleName}")
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val entries = registry.snapshot()
            if (entries.isEmpty()) { ticking = false; return }
            if (active()) entries.forEach { it.second.dispatch() }
            handler.postDelayed(this, 1000L)
        }
    }

    fun initHooks() {
        var installed = 0
        LocationManager::class.java.declaredMethods.filter {
            it.name in setOf("requestLocationUpdates", "requestSingleUpdate", "removeUpdates", "getCurrentLocation")
        }.forEach { method ->
            runCatching {
                when {
                    method.name == "getCurrentLocation" && method.parameterTypes.contains(Consumer::class.java) -> {
                        module.hook(method).intercept { chain ->
                            val callback = chain.args.filterIsInstance<Consumer<Location?>>().firstOrNull()
                            val executor = chain.args.filterIsInstance<Executor>().firstOrNull()
                            val cancellation = chain.args.filterIsInstance<CancellationSignal>().firstOrNull()
                            if (!active() || callback == null || executor == null) return@intercept chain.proceed()
                            val provider = chain.args.filterIsInstance<String>().firstOrNull() ?: "gps"
                            executor.execute {
                                if (cancellation?.isCanceled != true) {
                                    // A stop between registration and execution must not leak a stale fake fix.
                                    callback.accept(if (active()) LocationUtil.createFakeLocation(provider = provider) else null)
                                    report("current-location callback delivered")
                                }
                            }
                            null
                        }
                        installed++
                    }
                    method.parameterTypes.contains(LocationListener::class.java) -> {
                        module.hook(method).intercept { chain ->
                            val index = chain.args.indexOfFirst { it is LocationListener }
                            val listener = chain.args.getOrNull(index) as? LocationListener
                                ?: return@intercept chain.proceed()
                            // Public overloads delegate to one another; wrap only the outer call.
                            if (listener is Registration) return@intercept chain.proceed()
                            val args = chain.args.toTypedArray()
                            if (method.name == "removeUpdates") {
                                val registration = registry.get(listener) ?: return@intercept chain.proceed()
                                args[index] = registration
                                val result = chain.proceed(args)
                                registration.retire()
                                report("framework listener removed")
                                result
                            } else {
                                val looper = chain.args.filterIsInstance<Looper>().firstOrNull()
                                    ?: Looper.myLooper() ?: Looper.getMainLooper()
                                val deliveryHandler = Handler(looper)
                                val executor = chain.args.filterIsInstance<Executor>().firstOrNull()
                                    ?: Executor { deliveryHandler.post(it) }
                                // Reuse the proxy so LocationManager keeps its normal listener identity.
                                val registration = registry.get(listener) ?: Registration(listener,
                                    chain.args.filterIsInstance<String>().firstOrNull() ?: "gps",
                                    executor, method.name == "requestSingleUpdate",
                                    chain.thisObject as LocationManager)
                                args[index] = registration
                                val result = chain.proceed(args) // preserve permission checks and errors
                                registry.put(listener, registration)
                                report("framework listener registered")
                                handler.post { if (!ticking) { ticking = true; handler.post(tick) } }
                                result
                            }
                        }
                        installed++
                    }
                }
            }.onFailure { module.log(Log.WARN, TAG, "Hook failed: ${method.name}: ${it.javaClass.simpleName}") }
        }
        module.log(Log.INFO, TAG, "Installed $installed framework registration/current-location hooks")
    }

    private companion object { const val TAG = "[ActiveLocationHooks]" }
}
