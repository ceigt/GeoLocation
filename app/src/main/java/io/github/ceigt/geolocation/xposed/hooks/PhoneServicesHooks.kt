package io.github.ceigt.geolocation.xposed.hooks

import android.telephony.CellInfo
import android.telephony.NeighboringCellInfo
import android.util.Log
import io.github.ceigt.geolocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

class PhoneServicesHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[PhoneServicesHooks]"

    private companion object {
        const val LOG_PHONE_EVENTS = false
    }

    fun initHooks() {
        val phoneInterfaceManagerClass = findClass(
            classLoader,
            "com.android.phone.PhoneInterfaceManager"
        ) ?: return

        hookCellLocation(phoneInterfaceManagerClass)
        hookCellInfo(phoneInterfaceManagerClass)
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private inline fun logPhoneEvent(message: () -> String) {
        if (LOG_PHONE_EVENTS) {
            module.log(Log.INFO, tag, message())
        }
    }

    private fun hookCellLocation(phoneInterfaceManagerClass: Class<*>) {
        hookAll(phoneInterfaceManagerClass, "getCellLocation") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookCellInfo(phoneInterfaceManagerClass: Class<*>) {
        hookAll(phoneInterfaceManagerClass, "getAllCellInfo") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                emptyList<CellInfo>()
            } else {
                chain.proceed()
            }
        }

        hookAll(phoneInterfaceManagerClass, "getNeighboringCellInfo") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                // Same reasoning as getAllCellInfo: keep neighboring towers empty to encourage
                // GPS fallback in apps that combine cell and GNSS signals.
                // TODO: If consistency checks fail in some apps, provide coherent fake neighbors.
                logPhoneEvent { "Cleared neighboring cell info while spoofing." }
                emptyList<NeighboringCellInfo>()
            } else {
                chain.proceed()
            }
        }

        hookAll(phoneInterfaceManagerClass, "requestCellInfoUpdateInternal") { chain ->
            if (shouldSpoofArgs(chain.args)) {
                // Android 15 clients commonly wait for this Binder callback before starting their
                // network-location pipeline. Dropping the request makes those clients report that
                // location services are disabled even while synthetic GNSS fixes are arriving.
                val callback = chain.args.getOrNull(1)
                val cells = emptyList<CellInfo>()
                if (!deliverCellInfo(callback, cells)) {
                    module.log(Log.WARN, tag, "Unable to deliver synthetic async cell info callback.")
                }
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookAll(clazz: Class<*>, methodName: String, hooker: Hooker) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            module.log(Log.WARN, tag, "No method named $methodName on ${clazz.name}")
            return
        }

        var hooked = 0
        methods.forEach { method ->
            try {
                module.hook(method).intercept(hooker)
                hooked++
            } catch (e: Throwable) {
                module.log(Log.ERROR, tag, "Failed hooking ${clazz.name}#$methodName: ${e.message}")
            }
        }

        if (hooked > 0) {
            module.log(Log.INFO, tag, "Hooked ${clazz.name}#$methodName ($hooked overloads).")
        }
    }

    private fun findClass(classLoader: ClassLoader, vararg names: String): Class<*>? {
        names.forEach { name ->
            try {
                return Class.forName(name, false, classLoader)
            } catch (_: Throwable) {
                // Keep trying ROM-specific framework names.
            }
        }
        module.log(Log.WARN, tag, "None of these classes were found: ${names.joinToString()}")
        return null
    }

    // In system-hook mode every third-party caller is a target. Requiring the package to also be
    // selected in the manager defeats global mode and lets vendor SDKs recover a nearby real fix
    // from cell towers when the system location switch is on.
    private fun shouldSpoofArgs(args: List<Any?>?): Boolean {
        val config = PreferencesUtil.snapshot()
        if (!config.enableSystemHooks || !config.isPlaying) return false
        return args?.asSequence()
            ?.mapNotNull(::extractPackageName)
            ?.any(::isThirdPartyPackage) == true
    }

    private fun deliverCellInfo(callback: Any?, cells: List<CellInfo>): Boolean {
        if (callback == null) return false
        return try {
            val method = callback.javaClass.methods.firstOrNull {
                it.name == "onCellInfo" && it.parameterCount == 1
            } ?: callback.javaClass.declaredMethods.firstOrNull {
                it.name == "onCellInfo" && it.parameterCount == 1
            } ?: return false
            method.isAccessible = true
            method.invoke(callback, cells)
            true
        } catch (error: Throwable) {
            module.log(Log.ERROR, tag, "Synthetic cell info callback failed: ${error.message}")
            false
        }
    }

    private fun isThirdPartyPackage(packageName: String): Boolean =
        packageName != "android" &&
            packageName != "system" &&
            packageName != "com.android.phone" &&
            !packageName.startsWith("android.") &&
            !packageName.startsWith("com.android.")

    private fun extractPackageName(value: Any?): String? {
        if (value is String) return value.takeIf { "." in it && !it.startsWith("android.") }
        return null
    }

    private fun defaultReturnValue(method: Method?): Any? {
        return when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }
}
