package com.moon.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.moon.weather.ui.theme.LocalWeatherDarkMode

/** Page-local coordinates keep glass stationary while the city pager moves. */
internal val LocalWeatherGlassOrigin = compositionLocalOf<LayoutCoordinates?> { null }

/** Snow uses the same cool glass as night, independently of app appearance. */
internal fun weatherTileTint(isDay: Boolean, weatherCode: Int = 0): Color {
    val snow = atmosphereKind(weatherCode) in listOf(AtmosphereKind.SNOW,AtmosphereKind.SNOW_GRAINS,AtmosphereKind.SNOW_SHOWERS)
    return (if (isDay && !snow) Color(0xFF4E2C19) else Color(0xFF93CBF2)).copy(alpha = 0.25f)
}

internal val LocalWeatherGlassTint = compositionLocalOf { weatherTileTint(isDay = true) }

/** Neighbouring page's artwork plus how far (0..1) the pager is towards it, read at draw time. */
internal class GlassCrossfade(val adjacent: WeatherArtwork?, val adjacentTint: Color? = null, val fraction: () -> Float)

internal val LocalWeatherGlassCrossfade = compositionLocalOf<GlassCrossfade?> { null }

/** Draws the cached blurred weather image in screen coordinates behind content. */
@Composable
fun Modifier.weatherGlass(shape: Shape, tint: Color, alpha: Float = 1f, showSheen: Boolean = true, crossfadeTint: Boolean = false): Modifier {
    val artwork = LocalWeatherArtwork.current
    val crossfade = LocalWeatherGlassCrossfade.current
    val pageOrigin = LocalWeatherGlassOrigin.current
    val darkMode = LocalWeatherDarkMode.current
    val sheen = remember(darkMode) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (darkMode) 0.09f else 0.17f),
            1f to Color.White.copy(alpha = if (darkMode) 0.02f else 0.04f)
        )
    }
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { coordinates ->
            val nextPosition = if (pageOrigin?.isAttached == true) {
                pageOrigin.localPositionOf(coordinates, Offset.Zero)
            } else {
                coordinates.positionInWindow()
            }
            if (position != nextPosition) position = nextPosition
        }
        .clip(shape)
        .drawWithContent {
            if (alpha > 0.001f) {
                if (artwork != null) {
                    drawImage(
                        image = artwork.blurred,
                        srcSize = IntSize(artwork.blurred.width, artwork.blurred.height),
                        dstOffset = IntOffset(-position.x.roundToInt(), -position.y.roundToInt()),
                        dstSize = artwork.screenSize,
                        filterQuality = FilterQuality.Medium,
                        alpha = alpha
                    )
                }
                val adjacent = crossfade?.adjacent
                if (adjacent != null && adjacent !== artwork) {
                    val blend = crossfade.fraction().coerceIn(0f, 1f)
                    if (blend > 0.001f) {
                        drawImage(
                            image = adjacent.blurred,
                            srcSize = IntSize(adjacent.blurred.width, adjacent.blurred.height),
                            dstOffset = IntOffset(-position.x.roundToInt(), -position.y.roundToInt()),
                            dstSize = adjacent.screenSize,
                            filterQuality = FilterQuality.Medium,
                            alpha = alpha * blend
                        )
                    }
                }
                val surfaceTint = if (crossfadeTint && crossfade?.adjacentTint != null) {
                    lerp(tint, crossfade.adjacentTint, crossfade.fraction().coerceIn(0f, 1f))
                } else tint
                if (surfaceTint.alpha > 0f) drawRect(surfaceTint.copy(alpha = surfaceTint.alpha * alpha))
                if (showSheen) drawRect(sheen, alpha = alpha)
            }
            drawContent()
        }
}

