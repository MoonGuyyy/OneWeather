package com.moon.weather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moon.weather.data.GeocodingResult
import com.moon.weather.data.LocationRepository
import com.moon.weather.data.RetrofitInstance
import com.moon.weather.data.SettingsRepository
import com.moon.weather.data.AppearanceMode
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.moon.weather.data.samePlace

class WeatherViewModel internal constructor(application: Application, private val sources: WeatherViewModelSources) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, WeatherViewModelSources.android(application))

    private val repo = LocationRepository(application)
    private val settingsRepo = SettingsRepository(application)

    private val _weatherCache = MutableStateFlow<Map<WeatherLocation, WeatherResponse>>(emptyMap())
    val weatherCache: StateFlow<Map<WeatherLocation, WeatherResponse>> = _weatherCache.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _locationsLoaded = MutableStateFlow(false)
    val locationsLoaded = _locationsLoaded.asStateFlow()
    private var pendingLoads = 0
    private val weatherRequests = mutableMapOf<WeatherLocation, Job>()
    private val _loadingLocations = MutableStateFlow<Set<WeatherLocation>>(emptySet())
    val loadingLocations = _loadingLocations.asStateFlow()
    private val _weatherErrors = MutableStateFlow<Map<WeatherLocation, String>>(emptyMap())
    val weatherErrors = _weatherErrors.asStateFlow()
    private val _isLocating = MutableStateFlow(false)
    val isLocating = _isLocating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _gpsLocation = MutableStateFlow<WeatherLocation?>(null)
    val gpsLocation: StateFlow<WeatherLocation?> = _gpsLocation.asStateFlow()

    private val _savedLocations = MutableStateFlow<List<WeatherLocation>>(emptyList())
    val savedLocations: StateFlow<List<WeatherLocation>> = _savedLocations.asStateFlow()
    private val _widgetLocation = MutableStateFlow<WeatherLocation?>(null)

    val activeLocations: StateFlow<List<WeatherLocation>> = combine(_gpsLocation, _savedLocations, _widgetLocation) { gps, saved, widget ->
        val list = mutableListOf<WeatherLocation>()
        if (gps != null) list.add(gps)
        list.addAll(saved)
        if (widget != null && list.none { kotlin.math.abs(it.lat - widget.lat) < 0.01 && kotlin.math.abs(it.lon - widget.lon) < 0.01 }) {
            list.add(widget)
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openWidgetLocation(location: WeatherLocation) {
        if (_widgetLocation.value != location) _widgetLocation.value = location
        fetchWeatherForLocation(location)
    }

    fun clearWidgetLocation() { _widgetLocation.value = null }

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            sources.savedLocations.catch {
                _error.value = "Could not read saved cities. Search for a city to continue."
                emit(emptyList())
            }.collect { locations ->
                _savedLocations.value = locations
                locations.forEach { fetchWeatherForLocation(it) }
                _locationsLoaded.value = true
                updateLoading()
            }
        }
    }

    fun fetchCurrentLocationWeather() {
        if (_isLocating.value) return
        _isLocating.value = true
        beginLoading()
        viewModelScope.launch {
            _error.value = null
            try {
                val location = withTimeoutOrNull(sources.requestTimeoutMillis) { sources.locate() }
                if (location == null) {
                    _error.value = "Location is unavailable. Search for a city instead."
                    return@launch
                }
                val previous = _gpsLocation.value
                if (_widgetLocation.value?.let { it.samePlace(location) || previous?.samePlace(it) == true } == true) {
                    clearWidgetLocation()
                }
                _gpsLocation.value = location
                fetchWeatherForLocation(location, forceRefresh = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = applicationMessage(com.moon.weather.R.string.weather_location_unavailable)
            } finally {
                _isLocating.value = false
                endLoading()
            }
        }
    }

    fun fetchWeatherForLocation(location: WeatherLocation, forceRefresh: Boolean = false): Job? {
        if (!forceRefresh && _weatherCache.value.containsKey(location)) {
            if (_gpsLocation.value == location) viewModelScope.launch {
                publishCurrentWeather(location, _weatherCache.value.getValue(location))
            }
            return null
        }
        weatherRequests[location]?.let { return it }
        beginLoading()
        _loadingLocations.value = _loadingLocations.value + location
        _weatherErrors.value = _weatherErrors.value - location

        val request = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val response = withTimeoutOrNull(sources.requestTimeoutMillis) { sources.weather(location) }
                    ?: throw java.io.IOException("Weather request timed out. Please retry.")
                _weatherCache.value = _weatherCache.value.toMutableMap().apply {
                    put(location, response)
                }
                if (_gpsLocation.value == location) viewModelScope.launch { publishCurrentWeather(location, response) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _weatherErrors.value = _weatherErrors.value + (location to applicationMessage(
                    if (e is java.io.IOException) com.moon.weather.R.string.weather_connection_unavailable
                    else com.moon.weather.R.string.weather_service_unavailable))
            } finally {
                weatherRequests.remove(location)
                _loadingLocations.value = _loadingLocations.value - location
                endLoading()
            }
        }
        weatherRequests[location] = request
        request.start()
        return request
    }

    suspend fun refreshWeatherForLocation(location: WeatherLocation) {
        fetchWeatherForLocation(location, forceRefresh = true)?.join()
    }

    private fun applicationMessage(resource: Int): String = getApplication<Application>().getString(resource)

    private suspend fun publishCurrentWeather(location: WeatherLocation, weather: WeatherResponse) {
        try {
            withContext(Dispatchers.IO) { sources.publish(location, weather) }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { android.util.Log.w("WeatherWidget", "Could not publish foreground weather", error) }
    }

    private fun beginLoading() {
        pendingLoads++
        updateLoading()
    }

    private fun endLoading() {
        pendingLoads--
        updateLoading()
    }

    private fun updateLoading() {
        _isLoading.value = !_locationsLoaded.value || pendingLoads > 0
    }

    fun searchCity(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            try {
                val response = RetrofitInstance.geocodingApi.searchCity(query)
                _searchResults.value = response.results ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun saveLocation(location: WeatherLocation) {
        viewModelScope.launch {
            repo.saveLocation(location)
        }
    }

    fun removeLocation(location: WeatherLocation) {
        viewModelScope.launch {
            repo.removeLocation(location)
        }
    }

    fun reorderLocations(locations: List<WeatherLocation>) {
        _savedLocations.value = locations
        viewModelScope.launch {
            repo.reorderLocations(locations)
        }
    }

    // Settings - expose as StateFlow with initial values
    val useCelsius: StateFlow<Boolean> = settingsRepo.useCelsius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val useMilitaryTime: StateFlow<Boolean> = settingsRepo.useMilitaryTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val useMph: StateFlow<Boolean> = settingsRepo.useMph
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val appearanceMode: StateFlow<AppearanceMode> = settingsRepo.appearanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppearanceMode.SYSTEM)

    fun setUseCelsius(useCelsius: Boolean) {
        viewModelScope.launch {
            settingsRepo.setUseCelsius(useCelsius)
        }
    }

    fun setUseMilitaryTime(useMilitaryTime: Boolean) {
        viewModelScope.launch {
            settingsRepo.setUseMilitaryTime(useMilitaryTime)
        }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch { settingsRepo.setAppearanceMode(mode) }
    }

    fun setUseMph(useMph: Boolean) {
        viewModelScope.launch {
            settingsRepo.setUseMph(useMph)
            val context = getApplication<android.app.Application>()
            context.sendBroadcast(android.content.Intent(context, com.moon.weather.widget.WeatherWidgetProvider::class.java)
                .setAction(com.moon.weather.widget.WeatherWidgetProvider.ACTION_AUTO_UPDATE_WIDGETS))
        }
    }

}
