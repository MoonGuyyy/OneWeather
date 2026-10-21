package com.moon.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.moon.weather.R
import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.DailyWeather
import com.moon.weather.data.WeatherResponse

/** Each category publishes real, size-specific artwork instead of stretching one preview. */
internal object WeatherWidgetPreviewPublisher {
    private const val REVISION = 8
    private const val PREFS = "weather_widget_picker_previews"
    val sampleWeather = WeatherResponse(
        CurrentWeather("2026-10-03T12:00", 17.0, 2, 1, 55.0, 18.0, 110.0, 1013.0, 6.0, 10000.0, 18.0, 10.0), null,
        DailyWeather(listOf("2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06"),
            listOf(0, 2, 61, 3), listOf(19.0, 22.0, 18.0, 16.0), listOf(10.0, 12.0, 9.0, 8.0),
            List(4) { "2026-10-03T07:10" }, List(4) { "2026-10-03T19:10" }, List(4) { 6.0 }, List(4) { 10 })
    )
    fun artwork(context: Context, entry: WidgetPickerCatalog.Entry, size: SizeF) =
        WeatherWidgetArtwork.render(context, size.width.toInt(), size.height.toInt(), "Paris", sampleWeather,
            entry.style, WidgetBackgroundStyle.ATMOSPHERIC)

    fun preview(context: Context, entry: WidgetPickerCatalog.Entry): RemoteViews {
        WeatherWidgetArtwork.initializeFont(context)
        return RemoteViews(entry.sizes.associateWith { size ->
            RemoteViews(context.packageName, R.layout.widget_picker_artwork).apply {
                setImageViewBitmap(R.id.widget_artwork, artwork(context, entry, size))
            }
        })
    }
    fun publishIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        @Suppress("DEPRECATION")
        val updated = appContext.packageManager.getPackageInfo(appContext.packageName, 0).lastUpdateTime
        WidgetPickerCatalog.entries.forEach { entry ->
            val key = "${entry.provider.simpleName}_revision_$REVISION"
            if (prefs.getLong(key, 0L) == updated) return@forEach
            val published = runCatching {
                manager.setWidgetPreview(ComponentName(appContext, entry.provider),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, preview(appContext, entry))
            }.onFailure { Log.w("WeatherWidget", "Could not publish ${entry.provider} picker preview", it) }.getOrDefault(false)
            if (published) prefs.edit().putLong(key, updated).apply()
        }
    }
}
