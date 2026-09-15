package io.github.ceigt.geolocation.data

import android.content.SharedPreferences
import io.github.ceigt.geolocation.data.repository.PreferenceSync
import org.junit.Assert.*
import org.junit.Test

class PreferenceSyncTest {
    @Test fun offlineEditsWinWhileExistingRemoteSettingsSurviveUpgrade() {
        val local = MemoryPreferences()
        val remote = MemoryPreferences()
        remote.edit().putBoolean(KEY_IS_PLAYING, true).putLong(KEY_ALTITUDE, 123L).commit()
        local.edit().putBoolean(KEY_ENABLE_MOCK_PROVIDER, true).commit()
        PreferenceSync.edit(local, null) { putBoolean(KEY_IS_PLAYING, false) }
        PreferenceSync.bind(local, remote)
        assertFalse(remote.getBoolean(KEY_IS_PLAYING, true))
        assertEquals(123L, local.getLong(KEY_ALTITUDE, 0L))
        assertTrue(remote.getBoolean(KEY_ENABLE_MOCK_PROVIDER, false))
    }

    @Test fun offlineDeletionIsNotResurrectedOnReconnect() {
        val local = MemoryPreferences()
        val remote = MemoryPreferences()
        remote.edit().putString(KEY_LAST_CLICKED_LOCATION, "old").commit()
        PreferenceSync.edit(local, null) { remove(KEY_LAST_CLICKED_LOCATION) }
        PreferenceSync.bind(local, remote)
        assertFalse(local.contains(KEY_LAST_CLICKED_LOCATION))
        assertFalse(remote.contains(KEY_LAST_CLICKED_LOCATION))
    }

    @Test fun failedRemoteWriteRemainsPendingAndDoesNotCopyCredentials() {
        val local = MemoryPreferences()
        val remote = MemoryPreferences()
        local.edit().putString(KEY_AMAP_WEB_KEY, "private-test-key").commit()
        remote.rejectWrites = true
        PreferenceSync.edit(local, remote) { putFloat(KEY_SPEED, 3f) }
        assertEquals(3f, local.getFloat(KEY_SPEED, 0f))
        remote.rejectWrites = false
        PreferenceSync.bind(local, remote)
        assertEquals(3f, remote.getFloat(KEY_SPEED, 0f))
        assertFalse(remote.contains(KEY_AMAP_WEB_KEY))
    }

    @Test fun retryFlushWithoutReconnectCommitsPendingDeletionAndKeepsCredentialsLocal() {
        val local = MemoryPreferences()
        val remote = MemoryPreferences()
        remote.edit().putString(KEY_LAST_CLICKED_LOCATION, "old").commit()
        remote.rejectWrites = true
        PreferenceSync.edit(local, remote) { remove(KEY_LAST_CLICKED_LOCATION) }
        assertTrue(local.getStringSet(PreferenceSync.PENDING, mutableSetOf()).orEmpty().isNotEmpty())
        remote.rejectWrites = false
        PreferenceSync.flush(local, remote)
        assertFalse(remote.contains(KEY_LAST_CLICKED_LOCATION))
        assertFalse(local.contains(PreferenceSync.PENDING))
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()
        var rejectWrites = false
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun contains(key: String?) = key in values
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit() = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
            override fun putLong(key: String, value: Long) = apply { changes[key] = value }
            override fun putInt(key: String, value: Int) = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun putStringSet(key: String, value: MutableSet<String>?) = apply { changes[key] = value?.toSet() }
            override fun remove(key: String) = apply { changes[key] = null }
            override fun clear() = apply { values.keys.forEach { changes[it] = null } }
            override fun commit(): Boolean {
                if (rejectWrites) return false
                for ((key, value) in changes) if (value == null) values.remove(key) else values[key] = value
                return true
            }
            override fun apply() { commit() }
        }
    }
}
