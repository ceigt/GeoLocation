package io.github.ceigt.geolocation.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ceigt.geolocation.data.model.FavoriteLocation
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FavoritesIntegrityTest {
    @Test fun malformedRecordCannotEraseOtherFavoritesDuringAddOrDelete() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = base.getSharedPreferences("favorites_integrity_test", Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        val damaged = """[{"name":"valid","latitude":31,"longitude":121},{"name":"damaged","latitude":"invalid"}]"""
        val repository = PreferencesRepository(context)
        val favorite = FavoriteLocation("new", 30.0, 120.0)
        try {
            prefs.edit().putString(KEY_FAVORITES, damaged).commit()
            for (add in listOf(true, false)) {
                try {
                    if (add) repository.addFavorite(favorite) else repository.removeFavorite(favorite)
                    fail("Damaged data must block destructive edits")
                } catch (_: Exception) { }
                assertEquals(damaged, prefs.getString(KEY_FAVORITES, null))
            }
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun outOfRangeFavoriteIsNotAcceptedAsValidData() {
        try {
            JsonCodec.decodeFavorites("""[{"name":"invalid","latitude":91,"longitude":121}]""")
            fail("Expected invalid coordinate rejection")
        } catch (_: IllegalArgumentException) { }
    }
}
