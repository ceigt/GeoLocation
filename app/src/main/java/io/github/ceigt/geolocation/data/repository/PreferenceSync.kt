package io.github.ceigt.geolocation.data.repository

import android.content.SharedPreferences
import io.github.ceigt.geolocation.data.*

/** Durable offline writes; remote-only values are imported before pending local changes win. */
internal object PreferenceSync {
    internal const val PENDING = "pending_hook_settings"
    private val keys = setOf(
        KEY_IS_PLAYING, KEY_LAST_CLICKED_LOCATION, KEY_ENABLE_MOCK_PROVIDER,
        KEY_ENABLE_SYSTEM_HOOKS, KEY_TARGET_APPS, KEY_APP_COORDINATE_SYSTEMS,
        KEY_USE_ACCURACY, KEY_ACCURACY, KEY_USE_ALTITUDE, KEY_ALTITUDE,
        KEY_USE_RANDOMIZE, KEY_RANDOMIZE_RADIUS, KEY_USE_VERTICAL_ACCURACY, KEY_VERTICAL_ACCURACY,
        KEY_USE_MEAN_SEA_LEVEL, KEY_MEAN_SEA_LEVEL, KEY_USE_MEAN_SEA_LEVEL_ACCURACY,
        KEY_MEAN_SEA_LEVEL_ACCURACY, KEY_USE_SPEED, KEY_SPEED, KEY_USE_SPEED_ACCURACY,
        KEY_SPEED_ACCURACY, KEY_HIDE_FAKE_LOCATION_TOAST
    )

    @Synchronized
    fun edit(local: SharedPreferences, remote: SharedPreferences?, action: SharedPreferences.Editor.() -> Unit) {
        val editor = local.edit()
        val changed = mutableSetOf<String>()
        val recording = object : SharedPreferences.Editor by editor {
            override fun putBoolean(key: String, value: Boolean) = apply { changed += key; editor.putBoolean(key, value) }
            override fun putString(key: String, value: String?) = apply { changed += key; editor.putString(key, value) }
            override fun putLong(key: String, value: Long) = apply { changed += key; editor.putLong(key, value) }
            override fun putFloat(key: String, value: Float) = apply { changed += key; editor.putFloat(key, value) }
            override fun putInt(key: String, value: Int) = apply { changed += key; editor.putInt(key, value) }
            override fun putStringSet(key: String, value: MutableSet<String>?) = apply { changed += key; editor.putStringSet(key, value) }
            override fun remove(key: String) = apply { changed += key; editor.remove(key) }
            override fun clear(): SharedPreferences.Editor = error("Shared settings must be removed explicitly")
        }
        recording.action()
        require(changed.all { it in keys }) { "Only hook settings may be mirrored" }
        val pending = local.getStringSet(PENDING, emptySet()).orEmpty() + changed
        check(editor.putStringSet(PENDING, pending).commit()) { "Cannot persist settings" }
        if (remote != null) runCatching { flush(local, remote) }
    }

    @Synchronized
    fun bind(local: SharedPreferences, remote: SharedPreferences) {
        val pending = local.getStringSet(PENDING, emptySet()).orEmpty()
        val values = remote.all
        val editor = local.edit()
        for (key in keys - pending - KEY_ENABLE_MOCK_PROVIDER) put(editor, key, values[key])
        check(editor.commit()) { "Cannot cache remote settings" }
        // Mode has historically been manager-local, including on upgrades.
        val next = pending + KEY_ENABLE_MOCK_PROVIDER
        check(local.edit().putStringSet(PENDING, next).commit())
        flush(local, remote)
    }

    @Synchronized
    fun flush(local: SharedPreferences, remote: SharedPreferences) {
        val pending = local.getStringSet(PENDING, emptySet()).orEmpty().intersect(keys)
        if (pending.isEmpty()) return
        val values = local.all
        val editor = remote.edit()
        for (key in pending) put(editor, key, values[key])
        check(editor.commit()) { "Remote settings write failed" }
        check(local.edit().remove(PENDING).commit()) { "Cannot confirm settings synchronization" }
    }

    private fun put(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            else -> error("Unsupported shared preference type")
        }
    }
}
