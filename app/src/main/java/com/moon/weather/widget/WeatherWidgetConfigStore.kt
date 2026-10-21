package com.moon.weather.widget

import android.content.Context
import com.moon.weather.data.WeatherLocation

enum class WidgetBackgroundStyle {
    ATMOSPHERIC,
    GLASS,
    /** Previous releases stored this name for blurred widgets. */
    BLURRED,
    DARK_BLUE,
    LIGHT_BLUE
}

enum class WidgetCardStyle { AUTO, WEATHER, SUNSET, FORECAST, WIND }

object WeatherWidgetConfigStore {
    private const val PREFS_NAME = "weather_widget_prefs"
    private const val KEY_BG_PREFIX = "bg_style_"
    private const val KEY_CARD_PREFIX = "card_style_"
    private const val KEY_BORDER_PREFIX = "show_border_"
    private const val KEY_LOCATION_PREFIX = "location_"
    private const val KEY_LAST_LOCATION_PREFIX = "last_location_"
    private const val LOCATION_CURRENT = "__CURRENT__"

    /** Persist the whole configuration before the widget receiver reads it. */
    fun saveConfiguration(context: Context, widgetId: Int, background: WidgetBackgroundStyle,
                          showBorder: Boolean, card: WidgetCardStyle, location: WeatherLocation?): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("$KEY_BG_PREFIX$widgetId", background.name)
            .putBoolean("$KEY_BORDER_PREFIX$widgetId", showBorder)
            .putString("$KEY_CARD_PREFIX$widgetId", card.name)
            .putString("$KEY_LOCATION_PREFIX$widgetId", location?.let(::encodeLocation) ?: LOCATION_CURRENT)
            .commit()

    fun getBackgroundStyle(context: Context, widgetId: Int): WidgetBackgroundStyle {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_BG_PREFIX$widgetId", WidgetBackgroundStyle.ATMOSPHERIC.name)
            ?: WidgetBackgroundStyle.ATMOSPHERIC.name
        if (raw == WidgetBackgroundStyle.BLURRED.name) return WidgetBackgroundStyle.GLASS
        return WidgetBackgroundStyle.entries.firstOrNull { it.name == raw } ?: WidgetBackgroundStyle.ATMOSPHERIC
    }

    fun getShowBorder(context: Context, widgetId: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("$KEY_BORDER_PREFIX$widgetId", false)

    fun getCardStyle(context: Context, widgetId: Int): WidgetCardStyle {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_CARD_PREFIX$widgetId", null)
        return WidgetCardStyle.entries.firstOrNull { it.name == raw } ?: WidgetPickerCatalog.defaultStyle(context, widgetId)
    }

    fun getLocationSelection(context: Context, widgetId: Int): WeatherLocation? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_LOCATION_PREFIX$widgetId", LOCATION_CURRENT)
            ?: LOCATION_CURRENT
        if (raw == LOCATION_CURRENT) return null
        return decodeLocation(raw)
    }

    fun removeWidget(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_BG_PREFIX$widgetId")
            .remove("$KEY_CARD_PREFIX$widgetId")
            .remove("$KEY_BORDER_PREFIX$widgetId")
            .remove("$KEY_LOCATION_PREFIX$widgetId")
            .remove("$KEY_LAST_LOCATION_PREFIX$widgetId")
            .apply()
    }

    fun saveLastResolvedLocation(context: Context, widgetId: Int, location: WeatherLocation) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_LAST_LOCATION_PREFIX$widgetId", encodeLocation(location))
            .apply()
    }

    fun getLastResolvedLocation(context: Context, widgetId: Int): WeatherLocation? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_LAST_LOCATION_PREFIX$widgetId", null)
            ?: return null
        return decodeLocation(raw)
    }

    fun saveCurrentLocation(context: Context, location: WeatherLocation) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("current_location", encodeLocation(location)).apply()
    }

    fun getCurrentLocation(context: Context): WeatherLocation? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("current_location", null)?.let(::decodeLocation)

    private fun encodeLocation(location: WeatherLocation): String {
        return "${location.name}~${location.lat}~${location.lon}~${location.country}"
    }

    private fun decodeLocation(raw: String): WeatherLocation? {
        val parts = raw.split("~")
        if (parts.size < 3) return null
        return WeatherLocation(
            name = parts[0],
            lat = parts[1].toDoubleOrNull() ?: return null,
            lon = parts[2].toDoubleOrNull() ?: return null,
            country = parts.getOrElse(3) { "" }
        )
    }
}
