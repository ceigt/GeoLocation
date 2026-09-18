//SettingsViewModel.kt
package io.github.ceigt.geolocation.manager.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ceigt.geolocation.data.*
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import io.github.ceigt.geolocation.manager.App
import kotlinx.coroutines.flow.*

/** One-shot messages surfaced to the settings UI. */
sealed interface SystemHooksEvent {
    data object UnsupportedSystemVersion : SystemHooksEvent
    data object ModuleNotActive : SystemHooksEvent
    data object TargetAppScopeRequired : SystemHooksEvent
    data class ScopeSetupRequired(val missingPackages: List<String>) : SystemHooksEvent
}

enum class LocationMode {
    APPLICATION_HOOK,
    SYSTEM_HOOK,
    MOCK_PROVIDER
}

internal fun resolveLocationMode(
    mockProviderEnabled: Boolean,
    systemHooksEnabled: Boolean
): LocationMode = when {
    mockProviderEnabled -> LocationMode.MOCK_PROVIDER
    systemHooksEnabled -> LocationMode.SYSTEM_HOOK
    else -> LocationMode.APPLICATION_HOOK
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    private val _saveError = MutableStateFlow(false)
    val saveError = _saveError.asStateFlow()
    fun clearSaveError() { _saveError.value = false }
    private val writer = SettingsWriter { error ->
        android.util.Log.e("SettingsViewModel", "Setting save failed: ${error.javaClass.simpleName}")
        _saveError.value = true
    }
    private fun enqueueSave(operation: suspend () -> Unit) { writer.enqueue(operation) }
    override fun onCleared() { writer.close(); super.onCleared() }

    private inner class Preference<T>(
        initialValue: T,
        flow: Flow<T>,
        private val saveOperation: suspend (T) -> Unit,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        val state = flow.stateIn(scope, SharingStarted.Eagerly, initialValue)
        fun setValue(value: T) = enqueueSave { saveOperation(value) }
    }

    // Preferences for Accuracy
    private val _useAccuracyPreference = Preference(
        DEFAULT_USE_ACCURACY,
        preferencesRepository.getUseAccuracyFlow(),
        preferencesRepository::saveUseAccuracy,
        viewModelScope
    )
    val useAccuracy: StateFlow<Boolean> = _useAccuracyPreference.state

    private val _accuracyPreference = Preference(
        DEFAULT_ACCURACY,
        preferencesRepository.getAccuracyFlow(),
        preferencesRepository::saveAccuracy,
        viewModelScope
    )
    val accuracy: StateFlow<Double> = _accuracyPreference.state

    // Preferences for Altitude
    private val _useAltitudePreference = Preference(
        DEFAULT_USE_ALTITUDE,
        preferencesRepository.getUseAltitudeFlow(),
        preferencesRepository::saveUseAltitude,
        viewModelScope
    )
    val useAltitude: StateFlow<Boolean> = _useAltitudePreference.state

    private val _altitudePreference = Preference(
        DEFAULT_ALTITUDE,
        preferencesRepository.getAltitudeFlow(),
        preferencesRepository::saveAltitude,
        viewModelScope
    )
    val altitude: StateFlow<Double> = _altitudePreference.state

    // Preferences for Randomize
    private val _useRandomizePreference = Preference(
        DEFAULT_USE_RANDOMIZE,
        preferencesRepository.getUseRandomizeFlow(),
        preferencesRepository::saveUseRandomize,
        viewModelScope
    )
    val useRandomize: StateFlow<Boolean> = _useRandomizePreference.state

    private val _randomizeRadiusPreference = Preference(
        DEFAULT_RANDOMIZE_RADIUS,
        preferencesRepository.getRandomizeRadiusFlow(),
        preferencesRepository::saveRandomizeRadius,
        viewModelScope
    )
    val randomizeRadius: StateFlow<Double> = _randomizeRadiusPreference.state

    // Preferences for Vertical Accuracy
    private val _useVerticalAccuracyPreference = Preference(
        DEFAULT_USE_VERTICAL_ACCURACY,
        preferencesRepository.getUseVerticalAccuracyFlow(),
        preferencesRepository::saveUseVerticalAccuracy,
        viewModelScope
    )
    val useVerticalAccuracy: StateFlow<Boolean> = _useVerticalAccuracyPreference.state

    private val _verticalAccuracyPreference = Preference(
        DEFAULT_VERTICAL_ACCURACY,
        preferencesRepository.getVerticalAccuracyFlow(),
        preferencesRepository::saveVerticalAccuracy,
        viewModelScope
    )
    val verticalAccuracy: StateFlow<Float> = _verticalAccuracyPreference.state

    // Preferences for Mean Sea Level
    private val _useMeanSeaLevelPreference = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL,
        preferencesRepository.getUseMeanSeaLevelFlow(),
        preferencesRepository::saveUseMeanSeaLevel,
        viewModelScope
    )
    val useMeanSeaLevel: StateFlow<Boolean> = _useMeanSeaLevelPreference.state

    private val _meanSeaLevelPreference = Preference(
        DEFAULT_MEAN_SEA_LEVEL,
        preferencesRepository.getMeanSeaLevelFlow(),
        preferencesRepository::saveMeanSeaLevel,
        viewModelScope
    )
    val meanSeaLevel: StateFlow<Double> = _meanSeaLevelPreference.state

    // Preferences for Mean Sea Level Accuracy
    private val _useMeanSeaLevelAccuracyPreference = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getUseMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveUseMeanSeaLevelAccuracy,
        viewModelScope
    )
    val useMeanSeaLevelAccuracy: StateFlow<Boolean> = _useMeanSeaLevelAccuracyPreference.state

    private val _meanSeaLevelAccuracyPreference = Preference(
        DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveMeanSeaLevelAccuracy,
        viewModelScope
    )
    val meanSeaLevelAccuracy: StateFlow<Float> = _meanSeaLevelAccuracyPreference.state

    // Preferences for Speed
    private val _useSpeedPreference = Preference(
        DEFAULT_USE_SPEED,
        preferencesRepository.getUseSpeedFlow(),
        preferencesRepository::saveUseSpeed,
        viewModelScope
    )
    val useSpeed: StateFlow<Boolean> = _useSpeedPreference.state

    private val _speedPreference = Preference(
        DEFAULT_SPEED,
        preferencesRepository.getSpeedFlow(),
        preferencesRepository::saveSpeed,
        viewModelScope
    )
    val speed: StateFlow<Float> = _speedPreference.state

    // Preferences for Speed Accuracy
    private val _useSpeedAccuracyPreference = Preference(
        DEFAULT_USE_SPEED_ACCURACY,
        preferencesRepository.getUseSpeedAccuracyFlow(),
        preferencesRepository::saveUseSpeedAccuracy,
        viewModelScope
    )
    val useSpeedAccuracy: StateFlow<Boolean> = _useSpeedAccuracyPreference.state

    private val _speedAccuracyPreference = Preference(
        DEFAULT_SPEED_ACCURACY,
        preferencesRepository.getSpeedAccuracyFlow(),
        preferencesRepository::saveSpeedAccuracy,
        viewModelScope
    )
    val speedAccuracy: StateFlow<Float> = _speedAccuracyPreference.state

    // Preferences for Hide Fake Location Toast
    private val _hideFakeLocationToastPreference = Preference(
        DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        preferencesRepository.getHideFakeLocationToastFlow(),
        preferencesRepository::saveHideFakeLocationToast,
        viewModelScope
    )
    val hideFakeLocationToast: StateFlow<Boolean> = _hideFakeLocationToastPreference.state

    // Preference for External Broadcast Control
    private val _enableBroadcastControlPreference = Preference(
        DEFAULT_ENABLE_BROADCAST_CONTROL,
        preferencesRepository.getEnableBroadcastControlFlow(),
        preferencesRepository::saveEnableBroadcastControl,
        viewModelScope
    )
    val enableBroadcastControl: StateFlow<Boolean> = _enableBroadcastControlPreference.state

    private val _enableMockProviderPreference = Preference(
        DEFAULT_ENABLE_MOCK_PROVIDER,
        preferencesRepository.getEnableMockProviderFlow(),
        preferencesRepository::saveEnableMockProvider,
        viewModelScope
    )
    val enableMockProvider: StateFlow<Boolean> = _enableMockProviderPreference.state

    // Preference for System-Level Hooks. The switch only changes after the required scopes are
    // already present in the Xposed manager.
    val enableSystemHooks: StateFlow<Boolean> = preferencesRepository.getEnableSystemHooksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ENABLE_SYSTEM_HOOKS)

    val locationMode: StateFlow<LocationMode> = combine(
        enableMockProvider,
        enableSystemHooks
    ) { mockProviderEnabled, systemHooksEnabled ->
        resolveLocationMode(mockProviderEnabled, systemHooksEnabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocationMode.APPLICATION_HOOK)

    private val _systemHooksEvents = MutableSharedFlow<SystemHooksEvent>(extraBufferCapacity = 1)
    val systemHooksEvents: SharedFlow<SystemHooksEvent> = _systemHooksEvents.asSharedFlow()

    // Preference for Language
    private val _languageTagPreference = Preference(
        DEFAULT_LANGUAGE_TAG,
        preferencesRepository.getLanguageTagFlow(),
        preferencesRepository::saveLanguageTag,
        viewModelScope
    )
    val languageTag: StateFlow<String> = _languageTagPreference.state

    private val _baiduMapAkPreference = Preference(
        DEFAULT_BAIDU_MAP_AK,
        preferencesRepository.getBaiduMapAkFlow(),
        preferencesRepository::saveBaiduMapAk,
        viewModelScope
    )
    val baiduMapAk: StateFlow<String> = _baiduMapAkPreference.state

    val mapProvider: StateFlow<MapProvider> = preferencesRepository.getMapProviderFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapProvider.BAIDU)

    private val _amapWebKeyPreference = Preference(
        DEFAULT_AMAP_WEB_KEY,
        preferencesRepository.getAmapWebKeyFlow(),
        preferencesRepository::saveAmapWebKey,
        viewModelScope
    )
    val amapWebKey: StateFlow<String> = _amapWebKeyPreference.state

    private val _amapSecurityCodePreference = Preference(
        DEFAULT_AMAP_SECURITY_CODE,
        preferencesRepository.getAmapSecurityCodeFlow(),
        preferencesRepository::saveAmapSecurityCode,
        viewModelScope
    )
    val amapSecurityCode: StateFlow<String> = _amapSecurityCodePreference.state

    private val _googleMapsApiKeyPreference = Preference(
        DEFAULT_GOOGLE_MAPS_API_KEY,
        preferencesRepository.getGoogleMapsApiKeyFlow(),
        preferencesRepository::saveGoogleMapsApiKey,
        viewModelScope
    )
    val googleMapsApiKey: StateFlow<String> = _googleMapsApiKeyPreference.state

    // Setter methods for all preferences
    fun setUseAccuracy(value: Boolean) = _useAccuracyPreference.setValue(value)
    fun setAccuracy(value: Double) = _accuracyPreference.setValue(value)
    fun setUseAltitude(value: Boolean) = _useAltitudePreference.setValue(value)
    fun setAltitude(value: Double) = _altitudePreference.setValue(value)
    fun setUseRandomize(value: Boolean) = _useRandomizePreference.setValue(value)
    fun setRandomizeRadius(value: Double) = _randomizeRadiusPreference.setValue(value)
    fun setUseVerticalAccuracy(value: Boolean) = _useVerticalAccuracyPreference.setValue(value)
    fun setVerticalAccuracy(value: Float) = _verticalAccuracyPreference.setValue(value)
    fun setUseMeanSeaLevel(value: Boolean) = _useMeanSeaLevelPreference.setValue(value)
    fun setMeanSeaLevel(value: Double) = _meanSeaLevelPreference.setValue(value)
    fun setUseMeanSeaLevelAccuracy(value: Boolean) = _useMeanSeaLevelAccuracyPreference.setValue(value)
    fun setMeanSeaLevelAccuracy(value: Float) = _meanSeaLevelAccuracyPreference.setValue(value)
    fun setUseSpeed(value: Boolean) = _useSpeedPreference.setValue(value)
    fun setSpeed(value: Float) = _speedPreference.setValue(value)
    fun setUseSpeedAccuracy(value: Boolean) = _useSpeedAccuracyPreference.setValue(value)
    fun setSpeedAccuracy(value: Float) = _speedAccuracyPreference.setValue(value)
    fun setHideFakeLocationToast(value: Boolean) = _hideFakeLocationToastPreference.setValue(value)
    fun setEnableBroadcastControl(value: Boolean) = _enableBroadcastControlPreference.setValue(value)
    fun setLanguageTag(value: String) = _languageTagPreference.setValue(value)
    fun setBaiduMapAk(value: String) = _baiduMapAkPreference.setValue(value.trim())
    fun setMapProvider(value: MapProvider) = enqueueSave {
        preferencesRepository.saveMapProvider(value)
    }
    fun setAmapWebKey(value: String) = _amapWebKeyPreference.setValue(value.trim())
    fun setAmapSecurityCode(value: String) = _amapSecurityCodePreference.setValue(value.trim())
    fun setGoogleMapsApiKey(value: String) = _googleMapsApiKeyPreference.setValue(value.trim())

    private val modeSelection = LatestSelection<LocationMode>(
        ::enqueueSave,
        { mode -> mode != LocationMode.SYSTEM_HOOK || validateSystemScope() },
        { mode -> preferencesRepository.saveLocationMode(
            mockProvider = mode == LocationMode.MOCK_PROVIDER,
            systemHooks = mode == LocationMode.SYSTEM_HOOK
        ) }
    )

    fun selectLocationMode(mode: LocationMode) = modeSelection.select(mode)

    private fun validateSystemScope(): Boolean {
        if (!SystemHookSupport.supports(android.os.Build.VERSION.SDK_INT)) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.UnsupportedSystemVersion)
            return false
        }
        val service = App.service
        if (service == null) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.ModuleNotActive)
            return false
        }
        // The writer performs this blocking Binder query off the UI thread.
        val currentScope = try { service.scope.toSet() }
        catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _systemHooksEvents.tryEmit(SystemHooksEvent.ModuleNotActive)
            return false
        }
        val missing = SYSTEM_HOOK_PACKAGES.filterNot(currentScope::contains)
        if (missing.isNotEmpty()) {
            _systemHooksEvents.tryEmit(SystemHooksEvent.ScopeSetupRequired(missing))
            return false
        }
        return true
    }
}
