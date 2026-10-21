package com.moon.weather.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.SizeF
import com.moon.weather.R

/** Four launcher categories. Picker sizes are independent of unrestricted home-screen resizing. */
internal object WidgetPickerCatalog {
    val compact = SizeF(180f, 80f)
    val square = SizeF(170f, 170f)
    val strip = SizeF(340f, 80f)
    val large = SizeF(340f, 180f)
    data class Entry(val provider: Class<out WeatherWidgetProvider>, val style: WidgetCardStyle,
        val sizes: List<SizeF>, val defaultSize: SizeF, val previewLayout: Int)
    val entries = listOf(
        Entry(WeatherWidgetCompactProvider::class.java, WidgetCardStyle.WEATHER, listOf(compact, square), compact, R.layout.widget_weather_preview_compact),
        Entry(WeatherWidgetProvider::class.java, WidgetCardStyle.FORECAST, listOf(compact, square, strip, large), large, R.layout.widget_weather_preview),
        Entry(WeatherWidgetStripProvider::class.java, WidgetCardStyle.SUNSET, listOf(compact, strip, square, large), large, R.layout.widget_weather_preview_strip),
        Entry(WeatherWidgetSquareProvider::class.java, WidgetCardStyle.WIND, listOf(compact, square), square, R.layout.widget_weather_preview_square)
    )
    fun entry(info: AppWidgetProviderInfo?) = entries.firstOrNull { it.provider.name == info?.provider?.className }
    fun defaultStyle(context: Context, id: Int): WidgetCardStyle =
        entry(android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(id))?.style ?: WidgetCardStyle.WEATHER
}
