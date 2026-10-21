package com.moon.weather

import androidx.compose.ui.unit.IntSize
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.moon.weather.ui.ProgressiveWeatherBackground
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.WeatherArtworkLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherArtworkInstrumentedTest {
    @Test
    fun progressiveBlurKeepsTheTopSharpAndReducesDetailTowardTheBottom() {
        val source = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(200 * 400) { index ->
            if ((index % 200 / 4 + index / 200 / 4) % 2 == 0) Color.WHITE else Color.BLACK
        }
        source.setPixels(pixels, 0, 200, 0, 0, 200, 400)
        val result = ProgressiveWeatherBackground.prepare(source, IntSize(200, 400))
        for (y in 0 until 184) for (x in 0 until 200) {
            assertEquals(source.getPixel(x, y), result.getPixel(x, y))
        }
        fun detail(startY: Int): Double {
            var sum = 0L
            for (y in startY until startY + 20) for (x in 10 until 190) {
                sum += kotlin.math.abs(Color.red(result.getPixel(x, y)) - Color.red(result.getPixel(x + 1, y)))
            }
            return sum.toDouble() / (20 * 180)
        }
        val sharp = detail(80)
        val transition = detail(240)
        val bottom = detail(350)
        assertTrue(transition < sharp * .8)
        assertTrue(bottom < transition)
        assertTrue(bottom < sharp * .15)
        source.recycle()
        result.recycle()
    }

    @Test
    fun exportPreparedBackgroundForVisualReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val artwork = WeatherArtworkLoader.load(context.resources, R.drawable.sunnyday, IntSize(1080, 2340))!!
        File(context.getExternalFilesDir(null), "background-progressive-blur.png").outputStream().use {
            artwork.background.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val source = BitmapFactory.decodeResource(context.resources, R.drawable.sunnyday)
        File(context.getExternalFilesDir(null), "background-original.png").outputStream().use {
            source.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        source.recycle()
    }

    @Test
    fun prefetchAndForegroundShareThePreparedBackground() = runBlocking {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val size = IntSize(360, 780)
        val artwork = coroutineScope {
            val prefetch = async { WeatherArtworkLoader.load(resources, R.drawable.sunnyday, size) }
            val foreground = async { WeatherArtworkLoader.load(resources, R.drawable.sunnyday, size) }
            prefetch.await().also { assertSame(it, foreground.await()) }
        }
        assertNotNull(artwork)
        assertSame(artwork, WeatherArtworkLoader.cached(R.drawable.sunnyday, size))
        assertSame(artwork, WeatherArtworkLoader.load(resources, R.drawable.sunnyday, size))
        val rainy = WeatherArtworkLoader.load(resources, R.drawable.rainyday, size)
        assertNotNull(rainy)
        assertNotSame(artwork, rainy)
        assertSame(rainy, WeatherArtworkLoader.cached(R.drawable.rainyday, size))
    }
}
