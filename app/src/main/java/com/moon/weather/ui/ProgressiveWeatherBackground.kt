package com.moon.weather.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/** Prepared off the UI thread: a sharp upper scene with a smoothly increasing blur radius. */
internal object ProgressiveWeatherBackground {
    fun prepare(source: Bitmap, screenSize: IntSize): Bitmap {
        // Keep native detail without allocating an oversized bitmap for high density screens.
        val targetAspect = screenSize.width.toFloat() / screenSize.height
        val cropWidth = min(source.width, (source.height * targetAspect).roundToInt()).coerceAtLeast(1)
        val cropHeight = min(source.height, (source.width / targetAspect).roundToInt()).coerceAtLeast(1)
        val scale = min(1f, min(screenSize.width.toFloat() / cropWidth, screenSize.height.toFloat() / cropHeight))
        val width = (cropWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (cropHeight * scale).roundToInt().coerceAtLeast(1)
        val left = (source.width - cropWidth) / 2
        val top = (source.height - cropHeight) / 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(source, Rect(left, top, left + cropWidth, top + cropHeight),
            Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val horizontal = IntArray(pixels.size)
        val radii = IntArray(height) { y ->
            val progress = ((y.toFloat() / height - .46f) / (.82f - .46f)).coerceIn(0f, 1f)
            val smooth = progress * progress * (3f - 2f * progress)
            (width * .014f * smooth).roundToInt()
        }
        val red = IntArray(maxOf(width, height) + 1)
        val green = IntArray(red.size)
        val blue = IntArray(red.size)
        // Two box passes soften the kernel. Prefix sums keep every pixel O(1), even at full blur.
        repeat(2) {
            for (y in 0 until height) {
                red[0] = 0; green[0] = 0; blue[0] = 0
                val row = y * width
                for (x in 0 until width) {
                    val color = pixels[row + x]
                    red[x + 1] = red[x] + (color shr 16 and 255)
                    green[x + 1] = green[x] + (color shr 8 and 255)
                    blue[x + 1] = blue[x] + (color and 255)
                }
                for (x in 0 until width) {
                    val start = (x - radii[y]).coerceAtLeast(0)
                    val end = (x + radii[y] + 1).coerceAtMost(width)
                    horizontal[row + x] = average(red, green, blue, start, end)
                }
            }
            for (x in 0 until width) {
                red[0] = 0; green[0] = 0; blue[0] = 0
                for (y in 0 until height) {
                    val color = horizontal[y * width + x]
                    red[y + 1] = red[y] + (color shr 16 and 255)
                    green[y + 1] = green[y] + (color shr 8 and 255)
                    blue[y + 1] = blue[y] + (color and 255)
                }
                for (y in 0 until height) {
                    val start = (y - radii[y]).coerceAtLeast(0)
                    val end = (y + radii[y] + 1).coerceAtMost(height)
                    pixels[y * width + x] = average(red, green, blue, start, end)
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun average(red: IntArray, green: IntArray, blue: IntArray, start: Int, end: Int): Int {
        val count = end - start
        return -0x1000000 or ((red[end] - red[start]) / count shl 16) or
            ((green[end] - green[start]) / count shl 8) or ((blue[end] - blue[start]) / count)
    }
}
