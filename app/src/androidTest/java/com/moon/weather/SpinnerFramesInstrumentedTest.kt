package com.moon.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.moon.weather.ui.TwidgetLoadingSpinner
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

class SpinnerFramesInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun greenDotsRemainVisibleThroughoutRepeatedCycles() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF172B38)), contentAlignment = Alignment.Center) {
                TwidgetLoadingSpinner(Modifier.size(64.dp).testTag("spinner"))
            }
        }
        val frames = Bitmap.createBitmap(6 * 160, 8 * 186, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frames)
        canvas.drawColor(Color.rgb(23, 43, 56))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f }
        val start = SystemClock.uptimeMillis()
        var minimumGreenFraction = 1f
        repeat(48) { index ->
            val frame = compose.onNodeWithTag("spinner").captureToImage().asAndroidBitmap()
            var greenPixels = 0
            for (py in 0 until frame.height) for (px in 0 until frame.width) {
                val pixel = frame.getPixel(px, py)
                val green = Color.green(pixel)
                if (green > Color.blue(pixel) + 10 && green > Color.red(pixel) + 30 && green > 70) greenPixels++
            }
            minimumGreenFraction = minOf(minimumGreenFraction, greenPixels.toFloat() / (frame.width * frame.height))
            val x = index % 6 * 160
            val y = index / 6 * 186
            canvas.drawBitmap(frame, null, android.graphics.Rect(x, y, x + 160, y + 160), paint)
            canvas.drawText("${SystemClock.uptimeMillis() - start} ms", x + 8f, y + 178f, paint)
            frame.recycle()
            Thread.sleep(100)
        }
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "spinner-cycles.png")
            .outputStream().use { frames.compress(Bitmap.CompressFormat.PNG, 100, it) }
        frames.recycle()
        assertTrue("Sample at least two complete animation loops", SystemClock.uptimeMillis() - start >= 4_000)
        assertTrue("The green dots must never flicker out; minimum visible area was $minimumGreenFraction",
            minimumGreenFraction >= .008f)
    }
}
