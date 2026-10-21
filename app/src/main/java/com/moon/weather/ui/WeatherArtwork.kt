package com.moon.weather.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max
import kotlin.math.roundToInt

data class WeatherArtwork(
    val background: ImageBitmap,
    val blurred: ImageBitmap,
    val screenSize: IntSize
)

val LocalWeatherArtwork = compositionLocalOf<WeatherArtwork?> { null }

/** Prepares the progressive background and glass texture once, away from the UI thread. */
object WeatherArtworkLoader {
    private val cache = LruCache<String, WeatherArtwork>(3)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // A 2.5K background and its blur work buffers are substantial on older tablets.
    private val decodePermits = Semaphore(1)
    private val loading = mutableMapOf<String, Deferred<WeatherArtwork?>>()

    fun cached(resourceId: Int, screenSize: IntSize): WeatherArtwork? =
        synchronized(cache) { cache.get("$resourceId:${screenSize.width}x${screenSize.height}") }

    suspend fun load(resources: Resources, resourceId: Int, screenSize: IntSize): WeatherArtwork? {
        if (screenSize.width <= 0 || screenSize.height <= 0) return null
        val key = "$resourceId:${screenSize.width}x${screenSize.height}"
        val pending = synchronized(cache) {
            cache.get(key)?.let { return it }
            loading.getOrPut(key) {
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        decodePermits.withPermit {
                            val source = BitmapFactory.decodeResource(resources, resourceId) ?: return@withPermit null
                            val blurred = makeBlurredCopy(source, screenSize)
                            val background = ProgressiveWeatherBackground.prepare(source, screenSize)
                            source.recycle()
                            background.prepareToDraw()
                            blurred.prepareToDraw()
                            val artwork = WeatherArtwork(background.asImageBitmap(), blurred.asImageBitmap(), screenSize)
                            synchronized(cache) { cache.put(key, artwork) }
                            artwork
                        }
                    } finally {
                        synchronized(cache) { loading.remove(key) }
                    }
                }
            }
        }
        pending.start()
        return pending.await()
    }

    private fun makeBlurredCopy(source: Bitmap, screenSize: IntSize): Bitmap {
        val width = max(1, screenSize.width / 5)
        val height = max(1, screenSize.height / 5)
        val sourceAspect = source.width.toFloat() / source.height
        val targetAspect = width.toFloat() / height
        val cropWidth = if (sourceAspect > targetAspect) (source.height * targetAspect).roundToInt() else source.width
        val cropHeight = if (sourceAspect < targetAspect) (source.width / targetAspect).roundToInt() else source.height
        val cropped = Bitmap.createBitmap(
            source,
            (source.width - cropWidth) / 2,
            (source.height - cropHeight) / 2,
            cropWidth.coerceAtLeast(1),
            cropHeight.coerceAtLeast(1)
        )
        val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
        if (cropped !== source && cropped !== scaled) cropped.recycle()

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== cropped && scaled !== source) scaled.recycle()

        // A radius-12 box has variance 52. Sigma 7.2 gives comparable softness,
        // with a true Gaussian falloff rather than equally weighted neighbours.
        val output = gaussianBlurPixels(pixels, width, height, sigma = 7.2f)
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}
