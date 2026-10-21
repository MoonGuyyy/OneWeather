package com.moon.weather.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size

/** The mask defines both the solid paint and its touch region, including its curved edges. */
@Composable
internal fun NavbarTouchShield(mask: ImageBitmap, modifier: Modifier = Modifier) {
    val shape = remember(mask) { OpaqueNavbarShape(mask) }
    Box(modifier.clip(shape).pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    })
}

private class OpaqueNavbarShape(mask: ImageBitmap) : Shape {
    private val top = FloatArray(mask.width)
    private val bottom = FloatArray(mask.width)
    init {
        val pixels = mask.toPixelMap()
        for (x in 0 until mask.width) {
            top[x] = (0 until mask.height).first { y -> pixels[x, y].alpha >= .98f } / (mask.height - 1f)
            bottom[x] = (mask.height - 1 downTo 0).first { y -> pixels[x, y].alpha >= .98f } / (mask.height - 1f)
        }
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        path.moveTo(0f, top[0] * size.height)
        for (x in 1..top.lastIndex) path.lineTo(x * size.width / top.lastIndex, top[x] * size.height)
        for (x in bottom.lastIndex downTo 0) path.lineTo(x * size.width / bottom.lastIndex, bottom[x] * size.height)
        path.close()
        return Outline.Generic(path)
    }
}
