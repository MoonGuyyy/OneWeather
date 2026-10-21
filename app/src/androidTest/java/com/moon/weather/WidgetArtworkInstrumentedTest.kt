package com.moon.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.data.CurrentWeather
import com.moon.weather.data.DailyWeather
import com.moon.weather.data.HourlyWeather
import com.moon.weather.data.WeatherResponse
import com.moon.weather.data.WeatherLocation
import com.moon.weather.widget.WeatherWidgetArtwork
import com.moon.weather.widget.WeatherWidgetImages
import android.graphics.BitmapFactory
import com.moon.weather.widget.WidgetBackgroundStyle
import com.moon.weather.widget.WidgetCardStyle
import java.io.File
import android.widget.RemoteViews
import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Process
import com.moon.weather.widget.WeatherWidgetProvider
import com.moon.weather.widget.WeatherWidgetCompactProvider
import com.moon.weather.widget.WeatherWidgetSquareProvider
import com.moon.weather.widget.WeatherWidgetStripProvider
import com.moon.weather.widget.WeatherWidgetPreviewPublisher
import com.moon.weather.widget.WeatherWidgetConfigStore
import com.moon.weather.widget.WeatherWidgetSnapshotStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WidgetArtworkInstrumentedTest {
    @Test fun homeWidgetRoundsTheSameFractionalReadingAsTheAppAndLockWidgets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WeatherWidgetArtwork.initializeFont(context)
        val sample = WeatherWidgetPreviewPublisher.sampleWeather
        fun render(temperature: Double, celsius: Boolean) = WeatherWidgetArtwork.render(
            context, 180, 80, "Fixture", sample.copy(current = sample.current!!.copy(temperature = temperature)),
            WidgetCardStyle.WEATHER, WidgetBackgroundStyle.DARK_BLUE, useCelsius = celsius)
        for (celsius in listOf(false, true)) {
            assertTrue("14.9 must display as the same temperature as 15.0",
                render(14.9, celsius).sameAs(render(15.0, celsius)))
            assertTrue("A changed reading must change the displayed temperature",
                !render(14.9, celsius).sameAs(render(14.1, celsius)))
        }
    }

    @Test
    fun squareForecastUsesAllFourDaysInReadingOrder() {
        verifyFourDayGrid(170, 170, "forecast-grid-widget")
    }

    @Test
    fun threeByTwoForecastUsesAllFourDaysInReadingOrder() {
        verifyFourDayGrid(260, 170, "forecast-grid-3x2-widget")
    }

    private fun verifyFourDayGrid(width: Int, height: Int, exportName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WeatherWidgetArtwork.initializeFont(context)
        val weather = WeatherWidgetPreviewPublisher.sampleWeather
        fun render(reading: WeatherResponse, celsius: Boolean = true) = WeatherWidgetArtwork.render(
            context, width, height, "Paris", reading, WidgetCardStyle.FORECAST,
            WidgetBackgroundStyle.DARK_BLUE, useCelsius = celsius)
        val baseline = render(weather)
        for (day in 0..3) {
            val daily = weather.daily!!
            val changed = render(weather.copy(daily = daily.copy(maxTemperatures =
                daily.maxTemperatures.mapIndexed { index, value -> if (index == day) 96.0 else value })))
            val changedQuadrants = mutableSetOf<Int>()
            for (y in 0 until baseline.height) for (x in 0 until baseline.width) {
                if (baseline.getPixel(x, y) != changed.getPixel(x, y)) {
                    changedQuadrants += (if (y >= baseline.height / 2) 2 else 0) +
                        (if (x >= baseline.width / 2) 1 else 0)
                }
            }
            assertTrue("Day ${day + 1} must appear in its own quadrant", changedQuadrants == setOf(day))
        }
        assertTrue("The four-day grid respects temperature units", !baseline.sameAs(render(weather, false)))
        val entry = com.moon.weather.widget.WidgetPickerCatalog.entries.single { it.style == WidgetCardStyle.FORECAST }
        val preview = WeatherWidgetPreviewPublisher.artwork(context, entry, com.moon.weather.widget.WidgetPickerCatalog.square)
        File(context.getExternalFilesDir(null), "forecast-grid-picker.png").outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(context.getExternalFilesDir(null), "$exportName.png").outputStream().use {
            baseline.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun widgetImagesUseHighResolutionTransparentPngs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WeatherWidgetImages.resources.forEach { (original, generated) ->
            assertTrue(original != generated)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(context.resources, generated, bounds)
            assertTrue(maxOf(bounds.outWidth, bounds.outHeight) >= 1024)
            assertTrue(minOf(bounds.outWidth, bounds.outHeight) >= 512)
            val drawable = WeatherWidgetImages.drawable(context, original)!!
            val bitmap = drawable.bitmap
            assertTrue(bitmap.hasAlpha())
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(0, 0)) < 16)
            // Widgets display at most 72dp; loading an entire large PNG wastes memory.
            val needed = kotlin.math.ceil(72f * context.resources.displayMetrics.density).toInt().coerceAtLeast(256)
            assertTrue(maxOf(bitmap.width, bitmap.height) <= needed * 2)
        }
    }

    @Test
    fun lastRealReadingSurvivesAndDoesNotLeakToAnotherCity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = 987654
        val paris = WeatherLocation("Paris", 48.8566, 2.3522)
        val london = WeatherLocation("London", 51.5072, -0.1276)
        val reading = WeatherResponse(
            CurrentWeather("2026-10-02T17:30", 25.0, 2, 1, 55.0, 8.0, 110.0,
                1013.0, 6.0, 10000.0, 26.0, 14.0), null, null
        )
        try {
            assertTrue(WeatherWidgetConfigStore.saveConfiguration(context, id,
                WidgetBackgroundStyle.GLASS, false, WidgetCardStyle.WEATHER, paris))
            WeatherWidgetSnapshotStore.save(context, id, paris to reading, 123L)
            val restored = WeatherWidgetSnapshotStore.load(context, id)
            assertTrue(restored?.result?.first == paris)
            assertTrue(restored?.result?.second?.current?.temperature == 25.0)
            assertTrue(restored?.savedAt == 123L)

            assertTrue(WeatherWidgetConfigStore.saveConfiguration(context, id,
                WidgetBackgroundStyle.GLASS, false, WidgetCardStyle.WEATHER, london))
            assertTrue(WeatherWidgetSnapshotStore.load(context, id) == null)
        } finally {
            WeatherWidgetSnapshotStore.remove(context, id)
            WeatherWidgetConfigStore.removeWidget(context, id)
        }
    }

    @Test
    fun pickerPreviewsKeepTheirShapeWhenHostBoundsDiffer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            R.layout.widget_weather_preview_compact to (180f / 80f),
            R.layout.widget_weather_preview_square to 1f,
            R.layout.widget_weather_preview_strip to (340f / 180f),
            R.layout.widget_weather_preview to (340f / 180f)
        ).forEach { (layout, expectedAspect) ->
            val view = RemoteViews(context.packageName, layout).apply(context, null)
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 400)
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var left = 600; var right = -1; var top = 400; var bottom = -1
            for (y in 0 until 400) for (x in 0 until 600) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            assertTrue(right > left && bottom > top)
            assertTrue(abs((right - left + 1f) / (bottom - top + 1f) - expectedAspect) < .08f)
        }
    }

    @Test
    fun rendersAllLayoutsAtSmallAndLargeSizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val current = CurrentWeather("2026-10-02T17:30", 25.0, 2, 1, 55.0, 48.0, 110.0, 1013.0, 6.0, 10000.0, 26.0, 14.0)
        val hourly = HourlyWeather(
            (0 until 72).map { "2026-10-%02dT%02d:00".format(2 + it / 24, it % 24) },
            List(72) { 25.0 }, List(72) { 0 }, List(72) { 2 },
            List(72) { 55.0 }, List(72) { 20.0 }, List(72) { 110.0 },
            List(72) { 1013.0 }, List(72) { 6.0 }, List(72) { 10000.0 },
            List(72) { 26.0 }, List(72) { 14.0 }
        )
        val daily = DailyWeather(
            listOf("2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05"),
            listOf(0, 2, 61, 3), listOf(25.0, 28.0, 19.0, 17.0),
            listOf(14.0, 15.0, 11.0, 9.0),
            List(4) { "2026-10-02T07:10" }, List(4) { "2026-10-02T19:10" },
            List(4) { 6.0 }, List(4) { 10 }
        )
        val weather = WeatherResponse(current, hourly, daily)
        WeatherWidgetArtwork.initializeFont(context)
        listOf(
            Triple(WidgetCardStyle.WEATHER, 170, 270),
            Triple(WidgetCardStyle.SUNSET, 340, 180),
            Triple(WidgetCardStyle.FORECAST, 340, 180),
            Triple(WidgetCardStyle.WIND, 180, 180),
            Triple(WidgetCardStyle.AUTO, 110, 60)
        ).forEach { (style, width, height) ->
            val bitmap = WeatherWidgetArtwork.render(context, width, height, "Paris", weather, style, WidgetBackgroundStyle.ATMOSPHERIC)
            assertTrue(bitmap.width in 1..720 && bitmap.height in 1..720)
            File(context.getExternalFilesDir(null), "widget-${style.name.lowercase()}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val glass = WeatherWidgetArtwork.render(context, 340, 180, "Paris", weather, WidgetCardStyle.SUNSET, WidgetBackgroundStyle.GLASS)
        assertTrue(android.graphics.Color.alpha(glass.getPixel(170, 90)) < 255)
        assertTrue(WeatherWidgetArtwork.glassTint(false) != WeatherWidgetArtwork.glassTint(true))
        val rainyGlass = WeatherWidgetArtwork.render(context, 340, 180, "Paris",
            weather.copy(current = current.copy(weatherCode = 61)), WidgetCardStyle.SUNSET, WidgetBackgroundStyle.GLASS)
        assertTrue(glass.getPixel(glass.width / 2, glass.height - 15) ==
            rainyGlass.getPixel(rainyGlass.width / 2, rainyGlass.height - 15))
        val bordered = WeatherWidgetArtwork.render(context, 340, 180, "Paris", weather,
            WidgetCardStyle.SUNSET, WidgetBackgroundStyle.GLASS, showBorder = true)
        assertTrue(glass.getPixel(glass.width / 2, 2) != bordered.getPixel(bordered.width / 2, 2))
        File(context.getExternalFilesDir(null), "widget-glass.png").outputStream().use {
            glass.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val rain = WeatherWidgetArtwork.render(context, 170, 270, "New York",
            weather.copy(current = current.copy(weatherCode = 61)),
            WidgetCardStyle.WEATHER, WidgetBackgroundStyle.ATMOSPHERIC)
        File(context.getExternalFilesDir(null), "widget-rainbow.png").outputStream().use {
            rain.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val longCity = WeatherWidgetArtwork.render(context, 340, 180, "San Fernando del Valle de Catamarca", weather,
            WidgetCardStyle.SUNSET, WidgetBackgroundStyle.ATMOSPHERIC)
        File(context.getExternalFilesDir(null), "widget-long-city.png").outputStream().use {
            longCity.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val views = RemoteViews(context.packageName, R.layout.widget_weather_artwork)
        views.setInt(android.R.id.background, "setBackgroundColor", WeatherWidgetArtwork.glassTint(false))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            views.setViewOutlinePreferredRadius(android.R.id.background, 30f, android.util.TypedValue.COMPLEX_UNIT_DIP)
        }
        val glassView = views.apply(context, null)
        assertTrue(glassView.background != null && glassView.clipToOutline)
        assertTrue(RemoteViews(context.packageName, R.layout.widget_weather_preview).apply(context, null) != null)
        val provider = AppWidgetManager.getInstance(context).installedProviders.first {
            it.provider == ComponentName(context, WeatherWidgetProvider::class.java)
        }
        assertTrue(provider.initialLayout == R.layout.widget_weather_artwork)
        assertTrue(provider.previewImage != 0)
        val pickerImage = provider.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
        assertTrue(pickerImage != null && pickerImage.intrinsicWidth > 0 && pickerImage.intrinsicHeight > 0)
        if (android.os.Build.VERSION.SDK_INT >= 31) assertTrue(provider.previewLayout != 0)
        val sizePreviews = listOf(
            WeatherWidgetCompactProvider::class.java,
            WeatherWidgetSquareProvider::class.java,
            WeatherWidgetStripProvider::class.java
        ).map { type ->
            val sized = AppWidgetManager.getInstance(context).installedProviders.first {
                it.provider == ComponentName(context, type)
            }
            val expectedInitialLayout = if (type == WeatherWidgetCompactProvider::class.java)
                R.layout.widget_weather_artwork_pill else R.layout.widget_weather_artwork
            assertTrue(sized.initialLayout == expectedInitialLayout)
            assertTrue(sized.previewImage != 0 && sized.previewLayout != 0)
            sized.previewImage
        }
        assertTrue((sizePreviews + provider.previewImage).distinct().size == 4)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            WeatherWidgetPreviewPublisher.publishIfNeeded(context)
            val manager = AppWidgetManager.getInstance(context)
            listOf(
                WeatherWidgetCompactProvider::class.java,
                WeatherWidgetSquareProvider::class.java,
                WeatherWidgetStripProvider::class.java,
                WeatherWidgetProvider::class.java
            ).forEach { type ->
                val component = ComponentName(context, type)
                val info = manager.installedProviders.first { it.provider == component }
                if (info.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0) {
                    assertTrue(manager.getWidgetPreview(component, Process.myUserHandle(),
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != null)
                } else {
                    // Generated previews are rate limited; the picker must retain a static fallback.
                    assertTrue(info.previewLayout != 0 && info.previewImage != 0)
                }
            }
        }
    }

    @Test
    fun compactWidgetsKeepTheirSelectedContentWhenResized() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WeatherWidgetArtwork.initializeFont(context)
        val current = CurrentWeather("2026-10-03T12:00", 17.0, 0, 1, 55.0, 48.0, 110.0,
            1013.0, 6.0, 10000.0, 18.0, 14.0)
        val daily = DailyWeather(
            listOf("2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06"),
            listOf(0, 2, 61, 3), listOf(17.0, 22.0, 19.0, 15.0), listOf(10.0, 12.0, 11.0, 8.0),
            listOf("2026-10-03T07:10", "2026-10-04T07:12", "2026-10-05T07:14", "2026-10-06T07:16"),
            listOf("2026-10-03T19:10", "2026-10-04T19:08", "2026-10-05T19:06", "2026-10-06T19:04"),
            List(4) { 6.0 }, List(4) { 10 }
        )
        val weather = WeatherResponse(current, null, daily)
        fun render(style: WidgetCardStyle, width: Int, height: Int, reading: WeatherResponse = weather): Bitmap =
            WeatherWidgetArtwork.render(context, width, height, "Paris", reading, style, WidgetBackgroundStyle.DARK_BLUE)
        fun export(name: String, bitmap: Bitmap) {
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val temperature = render(WidgetCardStyle.WEATHER, 180, 80)
        val wind = render(WidgetCardStyle.WIND, 180, 80)
        val sunset = render(WidgetCardStyle.SUNSET, 180, 80)
        assertTrue("A resized compass must retain wind information", !temperature.sameAs(wind))
        assertTrue("A resized daylight widget must retain sun times", !temperature.sameAs(sunset))
        assertTrue("Only the 2x1 forecast falls back to current weather",
            temperature.sameAs(render(WidgetCardStyle.FORECAST, 180, 80)))
        assertTrue("Three-column forecast retains upcoming days",
            !render(WidgetCardStyle.FORECAST, 260, 80).sameAs(render(WidgetCardStyle.WEATHER, 260, 80)))
        export("compact-current", temperature)
        export("compact-wind", wind)
        export("compact-sunset", sunset)
        export("strip-sunset", render(WidgetCardStyle.SUNSET, 340, 80))
        export("compact-forecast", render(WidgetCardStyle.FORECAST, 170, 170))
        export("strip-forecast", render(WidgetCardStyle.FORECAST, 340, 80))
        export("square-sunset", render(WidgetCardStyle.SUNSET, 170, 170))
        export("square-wind", render(WidgetCardStyle.WIND, 170, 170))
        export("short-square-wind", render(WidgetCardStyle.WIND, 170, 120))
        export("wide-wind", render(WidgetCardStyle.WIND, 340, 80))
        export("three-wide-wind", render(WidgetCardStyle.WIND, 260, 80))
        val mphWind = WeatherWidgetArtwork.render(context, 170, 170, "Paris", weather,
            WidgetCardStyle.WIND, WidgetBackgroundStyle.DARK_BLUE, useMph = true)
        assertTrue("Wind unit changes alter the displayed reading", !mphWind.sameAs(render(WidgetCardStyle.WIND, 170, 170)))
        assertTrue("48 km/h is approximately 30 mph", com.moon.weather.utils.WindSpeed.value(48.0, true) == 30)
        export("square-wind-mph", mphWind)
        val windColors = WeatherWidgetArtwork.render(context, 170, 170, "Paris", weather,
            WidgetCardStyle.WIND, WidgetBackgroundStyle.ATMOSPHERIC)
        val weatherColors = WeatherWidgetArtwork.render(context, 170, 170, "Paris", weather,
            WidgetCardStyle.WEATHER, WidgetBackgroundStyle.ATMOSPHERIC)
        assertTrue("Wind widgets follow the same weather background palette",
            windColors.getPixel(windColors.width / 2, 8) == weatherColors.getPixel(weatherColors.width / 2, 8))
        val dawn = weather.copy(current = current.copy(time = "2026-10-03T05:00", isDay = 0))
        val night = weather.copy(current = current.copy(time = "2026-10-03T22:00", isDay = 0))
        val beforeRise = render(WidgetCardStyle.SUNSET, 180, 80, dawn)
        val tomorrowRise = render(WidgetCardStyle.SUNSET, 180, 80, night)
        assertTrue("Before dawn and after sunset use their respective sunrise times", !beforeRise.sameAs(tomorrowRise))
        export("compact-sunrise-today", beforeRise)
        export("compact-sunrise-tomorrow", tomorrowRise)
    }

    @Test
    fun exportCatalogPreviewsAndVerifyCategorySizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WeatherWidgetArtwork.initializeFont(context)
        val entries = com.moon.weather.widget.WidgetPickerCatalog.entries
        assertTrue(entries.size == 4)
        assertTrue(entries.map { it.style }.distinct().size == 4)
        assertTrue(entries.map { it.sizes.size } == listOf(2, 4, 4, 2))
        val manager = AppWidgetManager.getInstance(context)
        entries.forEach { entry ->
            val info = manager.installedProviders.first { it.provider == ComponentName(context, entry.provider) }
            assertTrue(info.resizeMode == AppWidgetProviderInfo.RESIZE_BOTH)
            val name = when (entry.style) {
                WidgetCardStyle.WEATHER -> "compact"
                WidgetCardStyle.WIND -> "square"
                WidgetCardStyle.SUNSET -> "strip"
                else -> "large"
            }
            val bitmap = com.moon.weather.widget.WeatherWidgetPreviewPublisher.artwork(context, entry, entry.defaultSize)
            File(context.getExternalFilesDir(null), "catalog-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            entry.sizes.forEach { size ->
                val preview = com.moon.weather.widget.WeatherWidgetPreviewPublisher.artwork(context, entry, size)
                assertTrue(abs(preview.width.toFloat() / preview.height - size.width / size.height) < .02f)
                File(context.getExternalFilesDir(null), "catalog-${entry.style.name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}.png").outputStream().use {
                    preview.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        com.moon.weather.widget.WeatherWidgetPreviewPublisher.publishIfNeeded(context)
    }

    @Test
    fun exportSizeSpecificPickerArtwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val current = CurrentWeather("2026-10-02T22:30", 13.0, 0, 0, 55.0, 8.0, 110.0, 1013.0, 0.0, 10000.0, 13.0, 9.0)
        val weather = WeatherResponse(current, null, null)
        WeatherWidgetArtwork.initializeFont(context)
        listOf(
            Triple("compact", 180, 80),
            Triple("square", 160, 160),
            Triple("strip", 340, 80),
            Triple("large", 340, 180)
        ).forEach { (name, width, height) ->
            val artwork = WeatherWidgetArtwork.render(context, width, height,
                "Sint-Pieters-Leeuw", weather, WidgetCardStyle.WEATHER,
                WidgetBackgroundStyle.GLASS)
            val bitmap = Bitmap.createBitmap(artwork.width, artwork.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = if (height < 105) bitmap.height / 2f else 24f * context.resources.displayMetrics.density
            val clip = Path().apply {
                addRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                    radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(clip)
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, 0f, bitmap.height.toFloat(),
                        0xff142d5a.toInt(), 0xff294d78.toInt(), Shader.TileMode.CLAMP)
                })
            canvas.drawBitmap(artwork, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
            File(context.getExternalFilesDir(null), "picker-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
