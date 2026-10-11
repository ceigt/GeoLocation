package io.github.ceigt.geolocation.manager.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ceigt.geolocation.data.model.FavoriteLocation
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)

    private val _favorites = MutableStateFlow<List<FavoriteLocation>>(emptyList())
    val favorites: StateFlow<List<FavoriteLocation>> get() = _favorites
    private val _saveError = MutableStateFlow(false)
    val saveError: StateFlow<Boolean> = _saveError
    fun clearSaveError() { _saveError.value = false }

    init {
        viewModelScope.launch {
            preferencesRepository.getFavoritesFlow().collectLatest { favorites ->
                _favorites.value = favorites
            }
        }
    }

    fun removeFavorite(favorite: FavoriteLocation) {
        write { preferencesRepository.removeFavorite(favorite) }
    }

    fun selectFavorite(favorite: FavoriteLocation, onSaved: () -> Unit = {}) {
        write {
            preferencesRepository.saveLastClickedLocation(favorite.latitude, favorite.longitude)
            onSaved()
        }
    }

    private fun write(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _saveError.value = true }
        }
    }
}
