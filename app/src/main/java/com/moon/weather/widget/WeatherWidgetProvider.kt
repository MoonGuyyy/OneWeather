package com.moon.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import com.moon.weather.MainActivity
import com.moon.weather.R
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import com.moon.weather.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

open class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshWidgets(context, appWidgetManager, appWidgetIds)
    }

    private fun refreshWidgets(context: Context, appWidgetManager: AppWidgetManager,
                               appWidgetIds: IntArray) {
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        WeatherWidgetRefreshScheduler.enqueueRefresh(context)
        val pending = goAsync()
        updateScope.launch {
            try {
                withTimeoutOrNull(WeatherWidgetRefreshScheduler.RECEIVER_BUDGET_MS) {
                    refreshWidgetIds(context, appWidgetManager, appWidgetIds)
                }
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
        WeatherWidgetRefreshScheduler.enqueueRefresh(context)
        val pending = goAsync()
        updateScope.launch {
            try { withTimeoutOrNull(WeatherWidgetRefreshScheduler.RECEIVER_BUDGET_MS) {
                updateSingleWidget(context, appWidgetManager, appWidgetId, newOptions)
            } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("WeatherWidget", "Could not resize widget $appWidgetId", error) }
            finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CONFIG_CHANGED) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()
            ids.forEach { cachedWeather.remove(it) }
            refreshWidgets(context, AppWidgetManager.getInstance(context), ids)
        } else if (intent.action == ACTION_AUTO_UPDATE_WIDGETS) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = allWidgetIds(context, manager)
            if (ids.isNotEmpty()) onUpdate(context, manager, ids)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWidgetPreviewPublisher.publishIfNeeded(context)
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWidgetRefreshScheduler.ensureScheduled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            WeatherWidgetConfigStore.removeWidget(context, it)
            WeatherWidgetSnapshotStore.remove(context, it)
            cachedWeather.remove(it)
            generations.remove(it)
        }
    }

    private suspend fun updateSingleWidget(context: Context, manager: AppWidgetManager, id: Int,
                                           options: Bundle?,
                                           fetch: suspend () -> Pair<WeatherLocation, WeatherResponse>? = {
                                               WeatherWidgetDataSource.fetchWeather(context, id, background = true)
                                           }): Boolean {
        val generation = sequence.incrementAndGet()
        generations[id] = generation
        val selection = WeatherWidgetConfigStore.getLocationSelection(context, id)
        val cached = listOfNotNull(cachedWeather[id], WeatherWidgetSnapshotStore.load(context, id))
            .maxByOrNull { it.savedAt }?.also {
            cachedWeather[id] = it
        }
        if (cached != null) renderAndUpdate(context, manager, id, options, cached.result, generation)
        else if (WeatherLockScreenWidgets.isProvider(manager.getAppWidgetInfo(id)?.provider?.className)) {
            // Install the open action immediately, even before the first weather reading arrives.
            renderAndUpdate(context, manager, id, options, null, generation)
        }
        if (cached != null && System.currentTimeMillis() - cached.savedAt in 0 until 5 * 60_000L) return true
        val result = fetch()
        if (result?.second?.current != null && generations[id] == generation &&
            selection == WeatherWidgetConfigStore.getLocationSelection(context, id)) {
            val updated = CachedWidgetWeather(result, System.currentTimeMillis())
            cachedWeather[id] = updated
            WeatherWidgetSnapshotStore.save(context, id, result, updated.savedAt)
            if (WeatherWidgetConfigStore.getLocationSelection(context, id) == null) {
                WeatherWidgetSnapshotStore.saveCurrent(context, result, updated.savedAt)
            }
            renderAndUpdate(context, manager, id, options, result, generation)
            return true
        }
        return false
    }

    private suspend fun renderAndUpdate(
        context: Context, manager: AppWidgetManager, id: Int, options: Bundle?,
        result: Pair<WeatherLocation, WeatherResponse>?, generation: Long
    ) {
        if (generations[id] != generation) return
        val views = buildViews(context, manager, id, options, result?.first, result?.second,
            SettingsRepository(context).useCelsius.first(), useMph = SettingsRepository(context).useMph.first())
        if (generations[id] != generation) return
        manager.updateAppWidget(id, views)
    }

    private fun buildViews(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?,
                           location: WeatherLocation?, weather: WeatherResponse?, useCelsius: Boolean,
                           cityNameOverride: String? = null, useMph: Boolean = false): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && options != null) {
            val sizes = BundleCompat.getParcelableArrayList(options,
                AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            if (!sizes.isNullOrEmpty()) {
                return RemoteViews(sizes.distinct().associateWith { size ->
                    buildViewForSize(context, id, size.width.roundToInt(), size.height.roundToInt(),
                        location, weather, useCelsius, cityNameOverride, useMph)
                })
            }
        }
        val (widthDp, heightDp) = widgetDimensions(context, manager, id, options)
        return buildViewForSize(context, id, widthDp, heightDp, location, weather, useCelsius,
            cityNameOverride, useMph)
    }

    private fun buildViewForSize(context: Context, id: Int, widthDp: Int, heightDp: Int,
                                 location: WeatherLocation?, weather: WeatherResponse?,
                                 useCelsius: Boolean, cityNameOverride: String?, useMph: Boolean): RemoteViews {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className
        if (WeatherLockScreenWidgets.isProvider(provider)) {
            val selectedLocation = location ?: WeatherWidgetConfigStore.getLastResolvedLocation(context, id)
            return WeatherLockScreenWidgets.views(context, provider == WeatherLockScreenWideProvider::class.java.name,
                weather, useCelsius).also { attachOpenAction(context, it, id, selectedLocation, lockScreen = true) }
        }
        val views = RemoteViews(context.packageName, R.layout.widget_weather_artwork)
        val backgroundStyle = WeatherWidgetConfigStore.getBackgroundStyle(context, id)
        val selectedLocation = location ?: WeatherWidgetConfigStore.getLocationSelection(context, id)
            ?: WeatherWidgetConfigStore.getLastResolvedLocation(context, id)
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setInt(android.R.id.background, "setBackgroundColor",
                if (backgroundStyle == WidgetBackgroundStyle.GLASS) WeatherWidgetArtwork.glassTint(isNight)
                else android.graphics.Color.TRANSPARENT)
            views.setViewOutlinePreferredRadius(android.R.id.background,
                WidgetShape.radius(heightDp.toFloat()),
                TypedValue.COMPLEX_UNIT_DIP)
        } else {
            views.setInt(android.R.id.background, "setBackgroundResource",
                if (backgroundStyle == WidgetBackgroundStyle.GLASS) {
                    if (heightDp < 105) R.drawable.widget_blur_pill else R.drawable.widget_blur_rounded
                } else android.R.color.transparent)
        }
        WeatherWidgetArtwork.initializeFont(context)
        val artwork = WeatherWidgetArtwork.render(
            context, widthDp, heightDp,
            cityNameOverride ?: selectedLocation?.name ?: context.getString(R.string.app_name),
            weather,
            WeatherWidgetConfigStore.getCardStyle(context, id),
            backgroundStyle,
            useCelsius,
            WeatherWidgetConfigStore.getShowBorder(context, id),
            useMph
        )
        views.setImageViewBitmap(R.id.widget_artwork, artwork)
        attachOpenAction(context, views, id, selectedLocation)
        return views
    }

    private fun widgetDimensions(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?): Pair<Int, Int> {
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = options?.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val height = options?.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val info = manager.getAppWidgetInfo(id)
        val density = context.resources.displayMetrics.density
        return (width.takeIf { it > 0 } ?: info?.minWidth?.let { (it / density).toInt() } ?: 300) to
            (height.takeIf { it > 0 } ?: info?.minHeight?.let { (it / density).toInt() } ?: 150)
    }

    internal fun attachOpenAction(context: Context, views: RemoteViews, id: Int,
                                  location: WeatherLocation?, lockScreen: Boolean = false): PendingIntent {
        val intent = openIntent(context, id, location)
        val open = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(android.R.id.background, open)
        if (lockScreen) {
            views.setOnClickPendingIntent(R.id.lock_weather_icon, open)
            views.setOnClickPendingIntent(R.id.lock_weather_temperature, open)
        }
        return open
    }

    internal fun openIntent(context: Context, id: Int, location: WeatherLocation?): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (WeatherWidgetConfigStore.getLocationSelection(context, id) == null) {
                putExtra(MainActivity.EXTRA_WIDGET_USE_CURRENT_LOCATION, true)
            } else location?.let {
                putExtra(MainActivity.EXTRA_WIDGET_LOCATION_LAT, it.lat)
                putExtra(MainActivity.EXTRA_WIDGET_LOCATION_LON, it.lon)
                putExtra(MainActivity.EXTRA_WIDGET_LOCATION_NAME, it.name)
            }
        }

    companion object {
        const val ACTION_CONFIG_CHANGED = "com.moon.weather.widget.ACTION_CONFIG_CHANGED"

        /** Reuse the foreground app's reading without another GPS or network request. */
        suspend fun publishCurrentWeather(context: Context, location: WeatherLocation, weather: WeatherResponse) {
            if (weather.current == null) return
            WeatherWidgetConfigStore.saveCurrentLocation(context, location)
            val updated = CachedWidgetWeather(location to weather, System.currentTimeMillis())
            WeatherWidgetSnapshotStore.saveCurrent(context, updated.result, updated.savedAt)
            val manager = AppWidgetManager.getInstance(context)
            val provider = WeatherWidgetProvider()
            allWidgetIds(context, manager).filter {
                WeatherWidgetConfigStore.getLocationSelection(context, it) == null
            }.forEach { id ->
                val generation = sequence.incrementAndGet()
                generations[id] = generation
                cachedWeather[id] = updated
                WeatherWidgetConfigStore.saveLastResolvedLocation(context, id, location)
                WeatherWidgetSnapshotStore.save(context, id, updated.result, updated.savedAt)
                provider.renderAndUpdate(context, manager, id, manager.getAppWidgetOptions(id),
                    updated.result, generation)
            }
        }
        fun updateConfiguredWidgetNow(context: Context, id: Int, cityName: String?,
                                      weather: WeatherResponse?, useCelsius: Boolean, useMph: Boolean = false) {
            generations[id] = sequence.incrementAndGet()
            cachedWeather.remove(id)
            val selectedLocation = WeatherWidgetConfigStore.getLocationSelection(context, id)
                ?: WeatherWidgetConfigStore.getLastResolvedLocation(context, id)
            val snapshot = WeatherWidgetSnapshotStore.load(context, id)
            val displayWeather = weather?.takeIf { it.current != null } ?: snapshot?.result?.second ?: return
            val displayLocation = selectedLocation ?: snapshot?.result?.first
            if (weather?.current != null && displayLocation != null) {
                val updated = CachedWidgetWeather(displayLocation to weather, System.currentTimeMillis())
                cachedWeather[id] = updated
                WeatherWidgetSnapshotStore.save(context, id, updated.result, updated.savedAt)
            }
            val manager = AppWidgetManager.getInstance(context)
            val views = WeatherWidgetProvider().buildViews(context, manager, id,
                manager.getAppWidgetOptions(id), displayLocation,
                displayWeather, useCelsius, cityName, useMph)
            manager.updateAppWidget(id, views)
        }
        private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cachedWeather = ConcurrentHashMap<Int, CachedWidgetWeather>()
        private val generations = ConcurrentHashMap<Int, Long>()
        private val sequence = AtomicLong()
        private val backgroundRefresh = Mutex()
        const val ACTION_AUTO_UPDATE_WIDGETS = "com.moon.weather.widget.ACTION_AUTO_UPDATE_WIDGETS"

        private val providerClasses = arrayOf(
            WeatherWidgetProvider::class.java,
            WeatherWidgetCompactProvider::class.java,
            WeatherWidgetSquareProvider::class.java,
            WeatherWidgetStripProvider::class.java,
            WeatherLockScreenSmallProvider::class.java,
            WeatherLockScreenWideProvider::class.java
        )

        internal fun allWidgetIds(context: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(context)): IntArray =
            providerClasses.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).asIterable() }.toIntArray()

        /** Shared by the short alarm attempt and durable worker; failure keeps the last reading. */
        internal suspend fun refreshAllWidgets(context: Context,
            fetch: suspend (Context, Int) -> Pair<WeatherLocation, WeatherResponse>? = { app, id ->
                WeatherWidgetDataSource.fetchWeather(app, id, background = true)
            }): Boolean = backgroundRefresh.withLock {
                refreshWidgetIds(context, AppWidgetManager.getInstance(context), allWidgetIds(context), fetch)
            }

        private suspend fun refreshWidgetIds(context: Context, manager: AppWidgetManager,
            ids: IntArray,
            fetch: suspend (Context, Int) -> Pair<WeatherLocation, WeatherResponse>? = { app, id ->
                WeatherWidgetDataSource.fetchWeather(app, id, background = true)
            }): Boolean = coroutineScope {
            val provider = WeatherWidgetProvider()
            ids.map { id -> async {
                try { provider.updateSingleWidget(context, manager, id, manager.getAppWidgetOptions(id)) {
                    fetch(context, id)
                } }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    Log.w("WeatherWidget", "Could not update widget $id", error)
                    false
                }
            } }.awaitAll().all { it }
        }
    }
}
