package com.moon.weather.ui

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal enum class AtmosphereKind {
    CLEAR, MAINLY_CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, FREEZING_RAIN,
    RAIN_SHOWERS, SNOW, SNOW_GRAINS, SNOW_SHOWERS, THUNDER, HAIL
}

internal fun atmosphereKind(code: Int): AtmosphereKind = when (code) {
    1 -> AtmosphereKind.MAINLY_CLEAR
    2 -> AtmosphereKind.PARTLY_CLOUDY
    3 -> AtmosphereKind.CLOUDY
    45, 48 -> AtmosphereKind.FOG
    51, 53, 55 -> AtmosphereKind.DRIZZLE
    56, 57, 66, 67 -> AtmosphereKind.FREEZING_RAIN
    61, 63, 65 -> AtmosphereKind.RAIN
    80, 81, 82 -> AtmosphereKind.RAIN_SHOWERS
    71, 73, 75 -> AtmosphereKind.SNOW
    77 -> AtmosphereKind.SNOW_GRAINS
    85, 86 -> AtmosphereKind.SNOW_SHOWERS
    95 -> AtmosphereKind.THUNDER
    96, 99 -> AtmosphereKind.HAIL
    else -> AtmosphereKind.CLEAR
}

private fun wrap(value: Float): Float = value - kotlin.math.floor(value)
private data class AirParticle(val x: Float, val y: Float, val speed: Float, val radius: Float, val phase: Float)
private data class CloudLobe(val centre: Offset, val radius: Float, val brush: Brush, val shade: Brush)

/** Draw-only clock: capped at 30fps, paused offscreen and when system animations are disabled. */
@Composable
internal fun WeatherAtmosphere(
    weatherCode: Int,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    adjacentWeatherCode: Int = weatherCode,
    adjacentIsDay: Boolean = isDay,
    blend: () -> Float = { 0f },
    enabled: Boolean = true,
    timeOverrideSeconds: Float? = null,
    lightningSeed: Int? = null
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val seconds = remember { mutableFloatStateOf(0f) }
    val stormVisible = atmosphereKind(weatherCode) in listOf(AtmosphereKind.THUNDER,AtmosphereKind.HAIL) ||
        atmosphereKind(adjacentWeatherCode) in listOf(AtmosphereKind.THUNDER,AtmosphereKind.HAIL)
    val storm = remember(stormVisible,lightningSeed) { LightningStorm(lightningSeed ?: Random.nextInt()) }
    val stormStart = remember(stormVisible,lightningSeed) { seconds.floatValue }
    LaunchedEffect(lifecycle, enabled, timeOverrideSeconds) {
        if (!enabled || timeOverrideSeconds != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return@repeatOnLifecycle
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last == 0L) last = now
                    if (now - last >= 33_333_333L) {
                        seconds.floatValue += (now - last) / 1_000_000_000f
                        last = now
                    }
                }
            }
        }
    }
    val particles = remember {
        val random = Random(7301)
        List(80) { AirParticle(random.nextFloat(),random.nextFloat(),.65f + random.nextFloat() * .6f,
            .6f + random.nextFloat() * .9f,random.nextFloat() * (2 * PI).toFloat()) }
    }
    Spacer(modifier.testTag("weather-atmosphere").graphicsLayer().drawWithCache {
        val w = size.width
        val h = size.height
        // Soft overlapping gradients form wispy, low-opacity clouds without hard outlines.
        val cloudWidth = w * .22f
        val lobes = listOf(
            Offset(-.3f, .01f) to .36f, Offset(-.1f,-.06f) to .43f,
            Offset(.16f,0f) to .38f, Offset(.36f,.06f) to .29f,
            Offset(0f,.13f) to .46f
        ).map { (position, radius) ->
            val centre = position * cloudWidth
            val r = radius * cloudWidth
            CloudLobe(centre,r,Brush.radialGradient(listOf(Color.White.copy(alpha = .16f),
                Color.White.copy(alpha = .065f),Color.Transparent), centre,r),
                Brush.radialGradient(listOf(Color(0xFF36516D).copy(alpha = .075f),Color.Transparent), centre+Offset(0f,r*.35f),r))
        }
        val rayPaths = List(4) { index ->
            val angle = (.40f + index * .24f)
            val length = h * .68f
            val spread = .025f + index * .003f
            Path().apply {
                moveTo(0f,0f)
                lineTo(cos(angle-spread)*length,sin(angle-spread)*length)
                lineTo(cos(angle+spread)*length,sin(angle+spread)*length)
                close()
            }
        }
        val rayBrush = Brush.radialGradient(listOf(Color(0xFFFFF3CF).copy(alpha = .20f),
            Color(0xFFFFF3CF).copy(alpha = .075f), Color.Transparent), Offset.Zero, h * .65f)
        val mistBrush = Brush.radialGradient(listOf(Color.White.copy(alpha = .105f),Color.Transparent),Offset.Zero,w*.6f)
        val lightning = LightningRenderer(w,h,density)
        val kind = atmosphereKind(weatherCode)
        val neighbour = atmosphereKind(adjacentWeatherCode)
        val currentRain = rainMotion(weatherCode)
        val adjacentRain = rainMotion(adjacentWeatherCode)
        onDrawBehind {
            val time = timeOverrideSeconds ?: seconds.floatValue
            val fraction = blend().coerceIn(0f,1f)
            val stormTime = if (timeOverrideSeconds != null) time else time - stormStart
            val strike = if (stormVisible) storm.strikeAt(stormTime) else null
            fun DrawScope.clouds(count: Int, opacity: Float) {
                repeat(count) { index ->
                    val drift = wrap(.18f + index * .32f + time / (180f + index*35f))
                    val x = drift * (w + cloudWidth*2) - cloudWidth
                    val y = h * (.16f + index*.105f) + sin(time/30f+index) * h*.004f
                    withTransform({ translate(x,y); scale(1.15f,.52f,Offset.Zero) }) {
                        lobes.forEach {
                            drawCircle(it.shade,it.radius,it.centre+Offset(0f,it.radius*.35f),alpha = opacity)
                            drawCircle(it.brush,it.radius,it.centre,alpha = opacity)
                        }
                    }
                }
            }
            fun DrawScope.precipitation(snow: Boolean, count: Int, opacity: Float, grains: Boolean = false,
                                        rain: RainMotion? = null) {
                repeat(count) { index ->
                    val particle = particles[index]
                    val position = if (snow) {
                        val y = (wrap(particle.y + time * particle.speed / 19f) * 1.12f - .06f) * h
                        val sway = sin(time*.32f + particle.phase) * w*.025f
                        Offset(wrap(particle.x + sway/w) * w, y)
                    } else rainPosition(particle.x, particle.y, particle.speed, time, w, h, rain!!)
                    val x = position.x
                    val y = position.y
                    val fade = (1f - (y/h - .5f).coerceIn(0f,.5f)*1.7f).coerceIn(0f,1f)
                    val alpha = opacity * fade * if (snow) .38f else (.32f + .10f * rain!!.intensity)
                    if (snow) {
                        val radius = (if (grains) .65f else particle.radius).dp.toPx()
                        drawCircle(Color.White.copy(alpha = alpha*.3f),radius*2,Offset(x,y))
                        drawCircle(Color.White.copy(alpha = alpha),radius,Offset(x,y))
                    } else {
                        val length = (6f + particle.radius*6f).dp.toPx() * rain!!.streakScale
                        drawLine(Color(0xFFD8E9F6).copy(alpha = alpha),Offset(x,y),Offset(x-length*rain.slant,y+length),
                            strokeWidth = .9.dp.toPx(),cap = StrokeCap.Round)
                    }
                }
            }
            fun DrawScope.sky(clearDay: Boolean, opacity: Float) {
                if (clearDay) {
                    withTransform({ translate(w*.035f,h*.11f); rotate(sin(time/25f)*2.5f,Offset.Zero) }) {
                        rayPaths.forEach { drawPath(it,rayBrush,alpha = opacity*(.8f+.2f*sin(time/18f))) }
                    }
                } else {
                    repeat(12) { index ->
                        val particle = particles[index]
                        val twinkle = .5f + .5f*sin(time/(7f+index*.7f)+particle.phase)
                        drawCircle(Color(0xFFE4F0FF).copy(alpha = opacity*(.13f+.19f*twinkle)),
                            (.5f+particle.radius*.3f).dp.toPx(),Offset(particle.x*w, (.05f+particle.y*.35f)*h))
                    }
                }
            }
            fun DrawScope.atmosphere(weather: AtmosphereKind, day: Boolean, opacity: Float, rain: RainMotion?) {
                if (opacity <= .001f) return
                fun rainfall(opacityFactor: Float = 1f) {
                    if (rain != null) precipitation(false, rain.count, opacity * opacityFactor, rain = rain)
                }
                when (weather) {
                    AtmosphereKind.CLEAR -> sky(day,opacity)
                    AtmosphereKind.MAINLY_CLEAR -> { sky(day,opacity*.8f); clouds(1,opacity*.65f) }
                    AtmosphereKind.PARTLY_CLOUDY -> { sky(day,opacity*.35f); clouds(2,opacity) }
                    AtmosphereKind.CLOUDY -> clouds(3,opacity)
                    AtmosphereKind.FOG -> {
                        repeat(4) { index ->
                            withTransform({ translate(w*(.2f+index*.2f)+sin(time/45f+index)*w*.08f,h*(.18f+index*.12f));
                                scale(1.4f,.22f,Offset.Zero) }) { drawCircle(mistBrush,w*.6f,Offset.Zero,alpha = opacity) }
                        }
                    }
                    AtmosphereKind.DRIZZLE -> { clouds(2,opacity*.6f); rainfall(.85f) }
                    AtmosphereKind.RAIN -> { clouds(2,opacity*.7f); rainfall() }
                    AtmosphereKind.RAIN_SHOWERS -> { clouds(2,opacity*.6f); rainfall(.85f+.15f*sin(time/12f)) }
                    AtmosphereKind.FREEZING_RAIN -> { rainfall(); precipitation(true,10,opacity*.6f,true) }
                    AtmosphereKind.SNOW -> { clouds(2,opacity*.5f); precipitation(true,32,opacity) }
                    AtmosphereKind.SNOW_GRAINS -> precipitation(true,26,opacity,true)
                    AtmosphereKind.SNOW_SHOWERS -> { clouds(2,opacity*.5f); precipitation(true,26,opacity*(.8f+.2f*sin(time/15f))) }
                    AtmosphereKind.THUNDER, AtmosphereKind.HAIL -> {
                        clouds(3,opacity*.8f); rainfall(.9f)
                        if (weather == AtmosphereKind.HAIL) precipitation(true,16,opacity*.75f,true)
                        if (strike != null) lightning.draw(this,strike,strike.brightnessAt(stormTime),opacity)
                    }
                }
            }
            if (kind == neighbour && isDay == adjacentIsDay && currentRain == adjacentRain) atmosphere(kind,isDay,1f,currentRain)
            else {
                atmosphere(kind,isDay,1f-fraction,currentRain)
                atmosphere(neighbour,adjacentIsDay,fraction,adjacentRain)
            }
        }
    })
}
