package io.github.ceigt.geolocation.data.repository

import android.content.SharedPreferences

/** Android updates memory before reporting disk failure; restore only this write's keys. */
internal fun commitOrRestore(
    prefs: SharedPreferences,
    editor: SharedPreferences.Editor,
    previous: Map<String, *>,
    keys: Set<String>
) {
    if (editor.commit()) return
    val restore = prefs.edit()
    for (key in keys) {
        when (val value = previous[key]) {
            null -> restore.remove(key)
            is Boolean -> restore.putBoolean(key, value)
            is String -> restore.putString(key, value)
            is Long -> restore.putLong(key, value)
            is Float -> restore.putFloat(key, value)
            is Int -> restore.putInt(key, value)
            is Set<*> -> restore.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    // A second disk failure is still reported. Restoring memory prevents a false UI value.
    restore.commit()
    error("Cannot persist setting; previous values restored in memory")
}
