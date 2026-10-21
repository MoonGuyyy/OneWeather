package com.moon.weather

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.widget.*
import com.moon.weather.utils.WeatherCodeMapper
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LockScreenWeatherInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bothLockScreenWidgetsOpenFromTheirIconTemperatureAndBackgroundBeforeWeatherLoads() {
        for (wide in listOf(false, true)) {
            val views = WeatherLockScreenWidgets.views(context, wide, null, true)
            val open = WeatherWidgetProvider().attachOpenAction(context, views,
                if (wide) -91002 else -91001, null, lockScreen = true)
            try {
                assertTrue("The widget must launch an activity", open.isActivity)
                assertEquals(context.packageName, open.creatorPackage)
                val view = views.apply(context, null)
                assertTrue("The whole widget must open the app", view.hasOnClickListeners())
                assertTrue("The icon must open the app", view.findViewById<View>(R.id.lock_weather_icon).hasOnClickListeners())
                assertTrue("The temperature must open the app", view.findViewById<View>(R.id.lock_weather_temperature).hasOnClickListeners())
                assertEquals("--°", view.findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
            } finally { open.cancel() }
        }
    }

    @Test
    fun bothSizesShowLiveWhiteWeatherAndRespectTemperatureUnits() {
        val weather = WeatherWidgetPreviewPublisher.sampleWeather
        for (wide in listOf(false, true)) {
            val views = WeatherLockScreenWidgets.views(context, wide, weather, true)
            val view = views.apply(context, null)
            assertEquals("17°", view.findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
            assertEquals(Color.WHITE, view.findViewById<TextView>(R.id.lock_weather_temperature).currentTextColor)
            assertEquals(Color.WHITE, view.findViewById<ImageView>(R.id.lock_weather_icon).imageTintList!!.defaultColor)
            val density = context.resources.displayMetrics.density
            val width = ((if (wide) 124 else 56) * density).toInt()
            val height = (56 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var visiblePixels = 0
            for (y in 0 until height) for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    visiblePixels++
                    assertWhitePixel(pixel)
                }
            }
            assertTrue(visiblePixels > 100)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            val preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(preview).apply { drawColor(Color.rgb(18, 32, 55)); drawBitmap(bitmap, 0f, 0f, null) }
            File(context.getExternalFilesDir(null), "lock-weather-${if (wide) "2x1" else "1x1"}.png").outputStream().use {
                preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val fahrenheit = WeatherLockScreenWidgets.views(context, wide, weather, false).apply(context, null)
            assertEquals("63°", fahrenheit.findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
            val negative = WeatherLockScreenWidgets.views(context, wide,
                weather.copy(current = weather.current!!.copy(temperature = -12.0, weatherCode = 75)), true).apply(context, null)
            assertEquals("-12°", negative.findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
            assertTrue(negative.findViewById<ImageView>(R.id.lock_weather_icon).contentDescription.isNotEmpty())
            val missing = WeatherLockScreenWidgets.views(context, wide, null, true).apply(context, null)
            assertEquals("--°", missing.findViewById<TextView>(R.id.lock_weather_temperature).text.toString())
        }
    }

    @Test
    fun eachWeatherFamilyUsesAWhiteSymbolAndDayNightChangesTheClearIcon() {
        assertNotEquals(WeatherLockScreenWidgets.icon(0, true), WeatherLockScreenWidgets.icon(0, false))
        assertEquals(R.drawable.isolated_storm_day, WeatherLockScreenWidgets.icon(95, true))
        assertEquals(R.drawable.isolated_snow_night, WeatherLockScreenWidgets.icon(86, false))
        val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        for (code in codes) for (isDay in listOf(false, true)) {
            assertEquals("Use the exact icon used in the forecast",
                WeatherCodeMapper.getIconRes(code, isDay), WeatherLockScreenWidgets.icon(code, isDay))
        }
        for (icon in codes.flatMap { listOf(WeatherLockScreenWidgets.icon(it, true), WeatherLockScreenWidgets.icon(it, false)) }.distinct()) {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val original = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val drawable = context.getDrawable(icon)!!.mutate()
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(Canvas(original))
            drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            drawable.draw(Canvas(bitmap))
            var visible = 0
            for (y in 0..95) for (x in 0..95) {
                val pixel = bitmap.getPixel(x, y)
                assertTrue("Keep the original icon's shape and transparency",
                    kotlin.math.abs(Color.alpha(original.getPixel(x, y)) - Color.alpha(pixel)) <= 1)
                if (Color.alpha(pixel) > 0) { visible++; assertWhitePixel(pixel) }
            }
            assertTrue(visible > 100)
        }
    }

    private fun assertWhitePixel(pixel: Int) {
        // PNG interpolation can round premultiplied channels at translucent edges.
        assertEquals("The tint must remove all original color", Color.red(pixel), Color.green(pixel))
        assertEquals(Color.red(pixel), Color.blue(pixel))
        if (Color.alpha(pixel) == 255) assertTrue("Opaque areas must stay white", Color.red(pixel) >= 254)
    }

    @Test
    fun bothReceiversRegisterSamsungMonotoneLockScreenMetadata() {
        for (type in listOf(WeatherLockScreenSmallProvider::class.java, WeatherLockScreenWideProvider::class.java)) {
            @Suppress("DEPRECATION")
            val receiver = context.packageManager.getReceiverInfo(ComponentName(context, type), PackageManager.GET_META_DATA)
            assertTrue(receiver.enabled && receiver.exported)
            assertTrue(receiver.metaData.getInt("android.appwidget.provider") != 0)
            assertTrue(receiver.metaData.getInt("samsung.appwidget.monotone.info") != 0)
            assertTrue(WeatherLockScreenWidgets.isProvider(type.name))
        }
        assertFalse(WeatherLockScreenWidgets.isProvider(WeatherWidgetCompactProvider::class.java.name))
    }
}
