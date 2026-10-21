package com.moon.weather.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.moon.weather.R
import kotlin.math.roundToInt

/** The small bitmap shadow is prepared once, then its opacity follows the pill animation. */
@Composable
internal fun ShadowedLocationPin(shadowAlpha: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val glyphSize = (24f * density).roundToInt().coerceAtLeast(1)
    val shadow = remember(context, density) {
        val source = ContextCompat.getDrawable(context, R.drawable.ic_location_pin)!!
            .toBitmap(glyphSize, glyphSize)
        val offset = IntArray(2)
        val bitmap = source.extractAlpha(Paint().apply {
            maskFilter = BlurMaskFilter(2f * density, BlurMaskFilter.Blur.NORMAL)
        }, offset)
        source.recycle()
        Triple(bitmap.asImageBitmap(), offset[0], offset[1])
    }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            if (shadowAlpha > .01f) {
                val scale = size.width / glyphSize
                drawImage(shadow.first,
                    dstOffset = IntOffset((shadow.second * scale).roundToInt(),
                        ((shadow.third + density) * scale).roundToInt()),
                    dstSize = IntSize((shadow.first.width * scale).roundToInt(),
                        (shadow.first.height * scale).roundToInt()),
                    alpha = shadowAlpha.coerceIn(0f, 1f),
                    colorFilter = ColorFilter.tint(Color.Black), filterQuality = FilterQuality.Medium)
            }
        }
        Icon(painterResource(R.drawable.ic_location_pin), contentDescription = "Location",
            tint = Color.White, modifier = Modifier.matchParentSize())
    }
}
