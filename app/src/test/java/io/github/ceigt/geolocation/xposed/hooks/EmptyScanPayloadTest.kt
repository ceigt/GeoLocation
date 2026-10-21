package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.*
import org.junit.Test

class EmptyScanPayloadTest {
    class Slice(private val values: List<Any>) { fun getList(): List<Any> = values }
    class FactoryOnly private constructor() {
        constructor(ignored: List<Any>) : this() { error("Constructor unavailable on this ROM") }
        fun getList(): List<Any> = kotlin.collections.emptyList()
        companion object { @JvmStatic fun emptyList() = FactoryOnly() }
    }
    class Broken(private val ignored: List<Any>) { fun getList() = listOf("real-network") }

    @Test fun listAndSliceKeepDeclaredTypeAndContainNoNetworks() {
        assertTrue((emptyScanPayloadFactory(List::class.java)() as List<*>).isEmpty())
        assertTrue((emptyScanPayloadFactory(Slice::class.java)() as Slice).getList().isEmpty())
    }
    @Test fun eachBinderResponseGetsAnIndependentSlice() {
        val create = emptyScanPayloadFactory(Slice::class.java)
        assertNotSame(create(), create())
    }
    @Test fun staticFactorySurvivesUnavailableConstructor() {
        assertTrue((emptyScanPayloadFactory(FactoryOnly::class.java)() as FactoryOnly).getList().isEmpty())
    }
    @Test fun incompatibleContainerIsRejectedBeforeInstallation() {
        assertThrows(IllegalStateException::class.java) { emptyScanPayloadFactory(Broken::class.java) }
    }
}
