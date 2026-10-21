package com.moon.weather

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moon.weather.data.AppearanceMode
import com.moon.weather.ui.WeatherScreen
import com.moon.weather.ui.WeatherViewModel
import com.moon.weather.ui.theme.WeatherTheme
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import com.moon.weather.widget.WeatherWidgetRefreshScheduler
import com.moon.weather.widget.WeatherWidgetBatterySettings
import com.moon.weather.widget.WeatherWidgetProvider

class MainActivity : ComponentActivity() {
    private var widgetNavigation by mutableStateOf(WidgetNavigation())
    private val widgetBackgroundPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Recheck actual Android state; the permission screen does not return a grant result.
        WeatherWidgetRefreshScheduler.ensureScheduled(this)
        WeatherWidgetRefreshScheduler.enqueueRefresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WeatherWidgetPreviewPublisher.publishIfNeeded(this)
        widgetNavigation = navigationFrom(intent, savedInstanceState?.getLong(STATE_NAVIGATION_ID) ?: 1L)

        enableEdgeToEdge()
        setContent {
            val weatherViewModel: WeatherViewModel = viewModel()
            val appearance by weatherViewModel.appearanceMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance) {
                AppearanceMode.SYSTEM -> systemDark
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            WeatherTheme(darkTheme = darkTheme) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    widgetLocationLat = widgetNavigation.lat,
                    widgetLocationLon = widgetNavigation.lon,
                    widgetLocationName = widgetNavigation.name,
                    widgetUseCurrentLocation = widgetNavigation.useCurrentLocation,
                    widgetNavigationId = widgetNavigation.id
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetNavigation = navigationFrom(intent, widgetNavigation.id + 1)
    }

    override fun onResume() {
        super.onResume()
        // Repair scheduling after an upgrade or a user removes background restrictions.
        WeatherWidgetRefreshScheduler.ensureScheduled(this)
        if (WeatherWidgetProvider.allWidgetIds(this).isNotEmpty()) {
            WeatherWidgetBatterySettings.request(this, automatic = true) { widgetBackgroundPermission.launch(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_NAVIGATION_ID, widgetNavigation.id)
        super.onSaveInstanceState(outState)
    }

    private fun navigationFrom(intent: Intent?, id: Long): WidgetNavigation = WidgetNavigation(
        lat = intent?.getDoubleExtra(EXTRA_WIDGET_LOCATION_LAT, Double.NaN),
        lon = intent?.getDoubleExtra(EXTRA_WIDGET_LOCATION_LON, Double.NaN),
        name = intent?.getStringExtra(EXTRA_WIDGET_LOCATION_NAME),
        useCurrentLocation = intent?.getBooleanExtra(EXTRA_WIDGET_USE_CURRENT_LOCATION, false) == true,
        id = id
    ).also {
        // A widget tap is a one-time request, not a destination to replay after recreation.
        intent?.removeExtra(EXTRA_WIDGET_LOCATION_LAT)
        intent?.removeExtra(EXTRA_WIDGET_LOCATION_LON)
        intent?.removeExtra(EXTRA_WIDGET_LOCATION_NAME)
        intent?.removeExtra(EXTRA_WIDGET_USE_CURRENT_LOCATION)
    }

    private data class WidgetNavigation(
        val lat: Double? = null,
        val lon: Double? = null,
        val name: String? = null,
        val useCurrentLocation: Boolean = false,
        val id: Long = 0
    )


    companion object {
        private const val STATE_NAVIGATION_ID = "widget_navigation_id"
        const val EXTRA_WIDGET_LOCATION_LAT = "extra_widget_location_lat"
        const val EXTRA_WIDGET_LOCATION_LON = "extra_widget_location_lon"
        const val EXTRA_WIDGET_LOCATION_NAME = "extra_widget_location_name"
        const val EXTRA_WIDGET_USE_CURRENT_LOCATION = "extra_widget_use_current_location"
    }
}
