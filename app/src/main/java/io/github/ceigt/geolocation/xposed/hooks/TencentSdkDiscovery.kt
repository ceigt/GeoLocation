package io.github.ceigt.geolocation.xposed.hooks

/** Resolve matching manager/listener/location types from one SDK namespace and loader. */
internal object TencentSdkDiscovery {
    val namespaces = listOf("com.tencent.map.geolocation", "com.tencent.map.geolocation.sapp")
    data class Sdk(val manager: Class<*>, val listener: Class<*>, val location: Class<*>)

    fun resolve(namespace: String, loader: ClassLoader): Sdk {
        val manager = Class.forName("$namespace.TencentLocationManager", false, loader)
        val listener = Class.forName("$namespace.TencentLocationListener", false, loader)
        val location = Class.forName("$namespace.TencentLocation", false, loader)
        require(listener.isInterface && location.isInterface) { "Unsupported Tencent SDK ABI" }
        listener.getMethod("onLocationChanged", location, Int::class.javaPrimitiveType, String::class.java)
        return Sdk(manager, listener, location)
    }
}
