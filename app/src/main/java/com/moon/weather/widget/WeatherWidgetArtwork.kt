package com.moon.weather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.moon.weather.R
import com.moon.weather.data.WeatherResponse
import com.moon.weather.ui.components.ForecastHeadline
import com.moon.weather.utils.WeatherCodeMapper
import com.moon.weather.utils.DaylightCurve
import com.moon.weather.utils.WindSpeed
import com.moon.weather.utils.Temperature
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A bitmap is used because launcher RemoteViews cannot draw arcs or gradients responsively. */
internal object WeatherWidgetArtwork {
    /** Matches AI Usage's translucent @android:id/background launcher blur contract. */
    fun glassTint(systemNight: Boolean): Int = if (systemNight) 0x6618181C else 0x66FFFFFF

    private fun isNearSunset(weather: WeatherResponse): Boolean {
        val now = weather.current?.time?.substringAfter('T')?.take(5) ?: return false
        val sunset = weather.daily?.sunsets?.firstOrNull()?.substringAfter('T')?.take(5) ?: return false
        fun minutes(time: String) = time.take(2).toIntOrNull()?.times(60)?.plus(time.drop(3).take(2).toIntOrNull() ?: 0)
        val delta = (minutes(sunset) ?: return false) - (minutes(now) ?: return false)
        return delta in -15..90
    }

    fun render(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        city: String,
        weather: WeatherResponse?,
        cardStyle: WidgetCardStyle,
        backgroundStyle: WidgetBackgroundStyle,
        useCelsius: Boolean = true,
        showBorder: Boolean = false,
        useMph: Boolean = false
    ): Bitmap {
        val w = widthDp.coerceIn(96, 600).toFloat()
        val h = heightDp.coerceIn(50, 600).toFloat()
        val density = context.resources.displayMetrics.density
        val scale = min(density, min(720f / w, 720f / h)).coerceAtLeast(0.5f)
        val bitmap = Bitmap.createBitmap((w * scale).roundToInt(), (h * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width / w, bitmap.height / h)
        val current = weather?.current
        val code = current?.weatherCode ?: 0
        val isDay = current?.isDay == 1
        val style = when (cardStyle) {
            WidgetCardStyle.AUTO -> when {
                h < 105f -> WidgetCardStyle.WEATHER
                w >= h * 1.45f -> WidgetCardStyle.SUNSET
                current != null && current.windSpeed >= 40 -> WidgetCardStyle.WIND
                else -> WidgetCardStyle.WEATHER
            }
            WidgetCardStyle.FORECAST -> if (h < 105f && w < 220f) WidgetCardStyle.WEATHER else WidgetCardStyle.FORECAST
            else -> cardStyle
        }
        val colors = when (backgroundStyle) {
            WidgetBackgroundStyle.DARK_BLUE -> intArrayOf(0xff092347.toInt(), 0xff164468.toInt(), 0xff223b54.toInt())
            WidgetBackgroundStyle.LIGHT_BLUE -> intArrayOf(0xff246aa0.toInt(), 0xff7ca4af.toInt(), 0xffd49f77.toInt())
            WidgetBackgroundStyle.GLASS -> intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT)
            WidgetBackgroundStyle.ATMOSPHERIC, WidgetBackgroundStyle.BLURRED -> when {
                !isDay && code in 51..99 -> intArrayOf(0xff0b1834.toInt(), 0xff243958.toInt(), 0xff27394c.toInt())
                !isDay -> intArrayOf(0xff142953.toInt(), 0xff573f61.toInt(), 0xff924a3a.toInt())
                code in 71..77 || code in 85..86 -> intArrayOf(0xff0b4162.toInt(), 0xff5d8398.toInt(), 0xff829da8.toInt())
                code in 51..99 -> intArrayOf(0xff07344e.toInt(), 0xff315b6c.toInt(), 0xff524b42.toInt())
                else -> intArrayOf(0xff093665.toInt(), 0xff0e4a77.toInt(), 0xff14638c.toInt())
            }
        }
        drawBackground(canvas, w, h, colors, style, backgroundStyle, code, isDay,
            weather?.let(::isNearSunset) == true)
        if (current == null) {
            drawMissingWeather(context, canvas, w, h, city)
            if (showBorder) drawBorder(canvas, w, h)
            return bitmap
        }
        val temp = "${temperature(current.temperature, useCelsius)}°"
        val condition = WeatherCodeMapper.getWeatherDescription(code)
        when (style) {
            WidgetCardStyle.WEATHER -> drawWeather(context, canvas, w, h, city, temp, code, isDay)
            WidgetCardStyle.SUNSET -> drawSunset(context, canvas, w, h, city, temp, condition, weather, useCelsius)
            WidgetCardStyle.FORECAST -> drawForecast(context, canvas, w, h, city, temp, weather, useCelsius)
            WidgetCardStyle.WIND -> drawWind(canvas, w, h, current.windSpeed, current.windDirection, city, useMph)
            WidgetCardStyle.AUTO -> Unit
        }
        if (showBorder) drawBorder(canvas, w, h)
        return bitmap
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float, colors: IntArray, style: WidgetCardStyle,
                               backgroundStyle: WidgetBackgroundStyle, code: Int, isDay: Boolean, nearSunset: Boolean) {
        val radius = WidgetShape.radius(h)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) }
        c.clipPath(clip)
        if (backgroundStyle != WidgetBackgroundStyle.GLASS) {
            c.drawRect(0f, 0f, w, h, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w * .38f, h, colors, null, Shader.TileMode.CLAMP)
            })
            if (style == WidgetCardStyle.SUNSET && isDay && code !in 51..99) {
                light(c, w * .48f, h * .76f, w * .66f, 0xb6ee865f.toInt(), 0x00ee865f)
                light(c, w * .75f, h * .67f, w * .44f, 0x82ffc76d.toInt(), 0x00ffc76d)
                light(c, w * .22f, h * 1.12f, w * .66f, 0x8cca5464.toInt(), 0x00ca5464)
                light(c, w * .55f, h * -.18f, w * .90f, 0x443c4a8a, 0x003c4a8a)
            } else if (!isDay && code in 51..99) {
                light(c, w * .24f, h * .85f, w * .72f, 0x526aa3ce, 0x006aa3ce)
                light(c, w * .74f, h * .52f, w * .64f, 0x4753679c, 0x0053679c)
            } else if (!isDay) {
                light(c, w * .18f, h * 1.0f, w * .70f, 0xb6ec673e.toInt(), 0x00ec673e)
                light(c, w * .65f, h * .66f, w * .55f, 0x5ccd3758, 0x00cd3758)
                light(c, w * .55f, h * -.12f, w * .8f, 0x383c286f, 0x003c286f)
            } else if (code in 51..99) {
                light(c, w * .18f, h * .85f, w * .75f, 0x70e49a70, 0x00e49a70)
                light(c, w * .74f, h * .30f, w * .7f, 0x4b76b4c2, 0x0076b4c2)
            } else {
                light(c, w * -.04f, h * .35f, w * .62f, 0x9ffff2a2.toInt(), 0x00fff2a2)
                light(c, w * .28f, h * 1.05f, w * .75f, 0x80b2dbb1.toInt(), 0x00b2dbb1)
                light(c, w * .82f, h * .78f, w * .52f, 0x30308abe, 0x00308abe)
            }
            if (style == WidgetCardStyle.FORECAST && isDay) {
                light(c, w * -.015f, h * .34f, h * .55f, 0xffffedaa.toInt(), 0x00ffedaA)
                c.drawCircle(w * -.015f, h * .34f, h * .105f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfffaf3c4.toInt() })
                bokeh(c, w * .08f, h * .73f, h * .035f, 0x44ffffff)
            }
            if (style == WidgetCardStyle.WEATHER && code in 51..67 && isDay && h >= 105f) {
                light(c, w * .02f, h * .95f, w * .68f, 0xfff5b42f.toInt(), 0x00f5b42f)
                bokeh(c, w * .15f, h * .9f, h * .075f, 0x22ffe3a2)
                bokeh(c, w * .26f, h * .94f, h * .044f, 0x33fff4bf)
                drawRainbow(c, w, h)
            }
        }
    }

    private fun drawBorder(c: Canvas, w: Float, h: Float) {
        val inset = 1f
        val radius = (WidgetShape.radius(h) - inset).coerceAtLeast(0f)
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 1f, 1f, 0f,
                    intArrayOf(0x00ffffff, 0x99ffffff.toInt(), 0x00ffffff),
                    floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP).apply {
                    // Normalize the diagonal so the shine stays at opposite corners on wide pills too.
                    setLocalMatrix(android.graphics.Matrix().apply { setScale(w, h) })
                }
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            })
    }

    private fun light(c: Canvas, x: Float, y: Float, radius: Float, inner: Int, outer: Int) {
        c.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, radius,
                intArrayOf(inner, colorWithAlpha(inner, .55f), colorWithAlpha(inner, .16f), outer),
                floatArrayOf(0f, .28f, .64f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun colorWithAlpha(color: Int, fraction: Float): Int =
        (color and 0x00ffffff) or (((Color.alpha(color) * fraction).roundToInt().coerceIn(0, 255)) shl 24)

    private fun bokeh(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        light(c, x, y, radius * 2.5f, color, Color.TRANSPARENT)
        c.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawRainbow(c: Canvas, w: Float, h: Float) {
        val colors = intArrayOf(0xffff7668.toInt(), 0xffffbb62.toInt(), 0xffffde86.toInt(),
            0xff8ed6aa.toInt(), 0xff80c8dc.toInt(), 0xff849dd5.toInt())
        colors.forEachIndexed { index, color ->
            val shift = index * h * .023f
            val path = Path().apply {
                moveTo(w * .43f, h * 1.18f + shift)
                cubicTo(w * .68f, h * .78f + shift, w * .94f, h * .65f + shift, w * 1.20f, h * .64f + shift)
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = h * .050f; strokeCap = Paint.Cap.ROUND
                this.color = colorWithAlpha(color, .055f)
                setShadowLayer(h * .028f, 0f, 0f, colorWithAlpha(color, .14f))
            })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = h * .024f; strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(w * .43f, h, w * 1.10f, h * .61f,
                    intArrayOf(colorWithAlpha(color, .05f), colorWithAlpha(color, .30f), colorWithAlpha(color, .14f)),
                    floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
            })
        }
    }

    private fun drawWeather(context: Context, c: Canvas, w: Float, h: Float, city: String,
                            temp: String, code: Int, isDay: Boolean) {
        val compact = h < 105f
        val iconId = WeatherCodeMapper.getIconRes(code, isDay)
        val moonTint = if (code == 0 && !isDay) 0xffbad7e5.toInt() else null
        if (!compact && w < 220f) {
            val iconSize = min(w * .33f, h * .31f)
            icon(context, c, iconId, (w - iconSize) / 2f, h * .07f, iconSize, moonTint)
            text(c, temp, w / 2f, h * .69f, min(50f, h * .30f), true,
                maxWidth = w * .84f, align = Paint.Align.CENTER, clarity = true)
            location(c, city, w * .085f, h * .84f, w * .83f,
                min(13f, w * .078f), maxLines = 2)
            return
        }
        val margin = if (compact) min(19f, w * .07f) else min(25f, w * .07f)
        val iconSpace = if (compact) min(h, w * .48f) else min(h * .72f, w * .45f)
        val iconSize = min(iconSpace * if (compact) .78f else .84f,
            if (compact) 56f else 68f)
        // One-row pills use the same inset on the icon's left, top and bottom.
        // Reserve only the icon and its gap, rather than a padded half-widget column.
        val iconLeft = if (compact) (h - iconSize) / 2f else margin + (iconSpace - iconSize) / 2f
        icon(context, c, iconId, iconLeft,
            (h - iconSize) / 2f, iconSize, moonTint)

        val rightX = if (compact) iconLeft + iconSize + 10f else margin + iconSpace + 12f
        val rightWidth = (w - rightX - margin).coerceAtLeast(22f)
        val tempSize = if (compact) min(28f, h * .36f) else minOf(82f, h * .36f, rightWidth * .55f)
        text(c, temp, rightX, if (compact) h * .48f else h * .50f,
            tempSize, true, maxWidth = rightWidth, clarity = true)

        val locationSize = if (compact) min(13f, h * .18f) else min(16f, h * .095f)
        location(c, city, rightX, if (compact) h * .75f else h * .70f,
            rightWidth, locationSize, maxLines = if (compact) 1 else 2)
    }

    private fun drawMissingWeather(context: Context, c: Canvas, w: Float, h: Float, city: String) {
        drawWeather(context, c, w, h, city, "--°", 3, true)
    }

    private fun drawSunset(context: Context, c: Canvas, w: Float, h: Float, city: String, temp: String, condition: String, weather: WeatherResponse, useCelsius: Boolean) {
        if (w < 230f || h < 135f) return drawCompactSunset(c, w, h, weather)
        val pad = min(25f, w * .06f)
        val labelShift = if (location(c, city, pad, 27f, w * .45f) > 1) 8f else 0f
        text(c, temp, pad, h * .46f + labelShift, min(78f, h * .32f), true, maxWidth = w * .42f, clarity = true)
        wrapText(c, condition, pad, h * .58f + labelShift, w * .42f, min(17f, h * .105f), 2, false)
        val sunset = weather.daily?.sunsets?.firstOrNull()?.substringAfter('T')?.take(5) ?: "--:--"
        val current = weather.current?.time?.substringAfter('T')?.take(5).orEmpty()
        val sunriseToday = weather.daily?.sunrises?.firstOrNull()?.substringAfter('T')?.take(5).orEmpty()
        val sunriseTomorrow = weather.daily?.sunrises?.getOrNull(1)?.substringAfter('T')?.take(5)
        val sun = sunMessage(current, sunriseToday, sunriseTomorrow, sunset)
        text(c, sun.headline, w - pad, h * .31f, min(26f, w * .07f), true, maxWidth = w * .52f, align = Paint.Align.RIGHT)
        text(c, sun.note, w - pad, h * .42f, min(18f, w * .053f), false, align = Paint.Align.RIGHT,
            color = if (sun.warm) 0xffffc46d.toInt() else Color.WHITE, maxWidth = w * .52f)
        val left = pad; val right = w - pad; val horizon = h * .72f; val peak = h * .60f
        val arc = Path()
        for (i in 0..60) {
            val u = i / 60f
            val x = left + (right - left) * u
            val y = horizon + h * .14f - (horizon + h * .14f - peak) * DaylightCurve.heightAt(u)
            if (i == 0) arc.moveTo(x, y) else arc.lineTo(x, y)
        }
        for ((width, alpha) in listOf(20f to .07f, 11f to .11f)) {
            c.drawPath(arc, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
                color = colorWithAlpha(0xffffae53.toInt(), alpha)
            })
        }
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4.4f; strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(left, 0f, right, 0f,
                DaylightCurve.colors,
                null, Shader.TileMode.CLAMP)
        }
        c.drawPath(arc, arcPaint)
        val sunrise = weather.daily?.sunrises?.firstOrNull()?.substringAfter('T')?.take(5).orEmpty()
        val progress = dayProgress(sunrise, sunset, current)
        val dotX = left + (right - left) * progress
        val dotY = horizon + h * .14f - (horizon + h * .14f - peak) * DaylightCurve.heightAt(progress)
        light(c, dotX, dotY, h * .20f, 0xb5ffe69a.toInt(), 0x00ffe69a)
        c.drawCircle(dotX, dotY, 6.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe8ecf0.toInt() })
    }

    private fun drawCompactSunset(c: Canvas, w: Float, h: Float, weather: WeatherResponse) {
        val now = weather.current?.time.orEmpty()
        val daily = weather.daily
        val today = daily?.time?.indexOf(now.substringBefore('T'))?.takeIf { it >= 0 } ?: 0
        val rise = daily?.sunrises?.getOrNull(today)?.substringAfter('T')?.take(5).orEmpty()
        val set = daily?.sunsets?.getOrNull(today)?.substringAfter('T')?.take(5).orEmpty()
        val clock = now.substringAfter('T', "").take(5)
        if (h >= 105f && w < 230f) {
            text(c, "Sunrise", w / 2f, h * .20f, 13f, true, align = Paint.Align.CENTER)
            text(c, rise.ifEmpty { "--:--" }, w / 2f, h * .36f, min(30f, w * .18f), true,
                maxWidth = w - 28f, align = Paint.Align.CENTER, clarity = true)
            drawFlatSunLine(c, 20f, w - 20f, h * .50f, min(22f, h * .15f), dayProgress(rise, set, clock))
            text(c, "Sunset", w / 2f, h * .70f, 13f, true, align = Paint.Align.CENTER)
            text(c, set.ifEmpty { "--:--" }, w / 2f, h * .86f, min(30f, w * .18f), true,
                maxWidth = w - 28f, align = Paint.Align.CENTER, clarity = true)
            return
        }
        if (w < 270f) {
            val beforeRise = clock.isNotEmpty() && rise.isNotEmpty() && clock < rise
            val afterSet = clock.isNotEmpty() && set.isNotEmpty() && clock >= set
            val nextRise = if (afterSet) daily?.sunrises?.getOrNull(today + 1)?.substringAfter('T')?.take(5) else rise
            val label = if (beforeRise || afterSet) "Sunrise: ${nextRise?.ifEmpty { "--:--" } ?: "--:--"}"
                else "Sunset: ${set.ifEmpty { "--:--" }}"
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = min(20f, h * .27f)
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
            val labelSize = labelPaint.textSize * min(1f, (w - 28f) / labelPaint.measureText(label))
            text(c, label, w / 2f, h / 2f + labelSize * .3f, labelSize, true,
                maxWidth = w - 28f, align = Paint.Align.CENTER)
            return
        }
        val pad = 18f
        val y = h / 2f
        text(c, rise.ifEmpty { "--:--" }, pad, y + 5f, min(17f, h * .24f), true)
        text(c, set.ifEmpty { "--:--" }, w - pad, y + 5f, min(17f, h * .24f), true, align = Paint.Align.RIGHT)
        val left = pad + 62f
        val right = w - pad - 62f
        drawFlatSunLine(c, left, right, y, min(22f, h * .28f), dayProgress(rise, set, clock))
    }

    private fun drawFlatSunLine(c: Canvas, left: Float, right: Float, y: Float, glowRadius: Float, progress: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4.4f; strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(left, y, right, y, DaylightCurve.colors, null, Shader.TileMode.CLAMP)
        }
        c.drawLine(left, y, right, y, stroke)
        val x = left + (right - left) * progress
        light(c, x, y, glowRadius, 0x88ffe69a.toInt(), 0x00ffe69a)
        c.drawCircle(x, y, 6.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe8ecf0.toInt() })
    }

    private fun drawCompactForecast(context: Context, c: Canvas, w: Float, h: Float, weather: WeatherResponse, useCelsius: Boolean) {
        val daily = weather.daily
        val grid = w < 270f && h >= 105f
        val count = when { grid -> 4; w < 220f -> 2; w < 270f -> 3; else -> 4 }
        val pad = min(18f, w * .06f)
        val col = (w - pad * 2f) / if (grid) 2 else count
        val rowHeight = if (grid) (h - pad * 2f) / 2f else h
        val first = daily?.time?.indexOf(weather.current?.time?.substringBefore('T'))?.takeIf { it >= 0 } ?: 0
        for (i in 0 until count) {
            val day = daily?.time?.getOrNull(first + i)
            val center = pad + col * ((if (grid) i % 2 else i) + .5f)
            val top = if (grid) pad + (i / 2) * rowHeight else 0f
            val label = day?.let { runCatching {
                LocalDateTime.parse("${it}T00:00").format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
            }.getOrNull() } ?: "--"
            text(c, label, center, top + rowHeight * if (grid) .19f else .24f,
                min(14f, col * .26f), true, align = Paint.Align.CENTER)
            val code = daily?.weatherCodes?.getOrNull(first + i)
            val iconSize = minOf(48f, col * .55f, rowHeight * if (grid) .42f else .34f)
            if (code != null) icon(context, c, WeatherCodeMapper.getIconRes(code, true),
                center - iconSize / 2f, top + rowHeight * if (grid) .28f else .32f, iconSize)
            val high = daily?.maxTemperatures?.getOrNull(first + i)?.let { "${temperature(it, useCelsius)}°" } ?: "--°"
            text(c, high, center, top + rowHeight * if (grid) .88f else .85f,
                min(19f, col * .34f), true, maxWidth = col * .9f, align = Paint.Align.CENTER, clarity = true)
        }
    }

    private fun drawCompactWind(c: Canvas, w: Float, h: Float, speed: Double, direction: Double, city: String, useMph: Boolean) {
        val r = minOf(27f, h * .34f, w * .20f)
        val cx = h / 2f
        val cy = h / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66ffffff; style = Paint.Style.STROKE; strokeWidth = 1f
        }
        c.drawCircle(cx, cy, r, ring)
        listOf("N", "E", "S", "W").forEachIndexed { i, label ->
            val a = i * PI / 2 - PI / 2
            text(c, label, cx + (r * .78f * cos(a)).toFloat(), cy + (r * .78f * sin(a)).toFloat() + 3f,
                min(9f, r * .35f), true, align = Paint.Align.CENTER,
                color = if (i == 0) 0xfff05863.toInt() else if (i == 2) 0xff4a7de4.toInt() else Color.WHITE)
        }
        c.save()
        c.rotate(direction.toFloat(), cx, cy)
        val pointer = Path().apply {
            moveTo(cx, cy - r * .55f); lineTo(cx - r * .17f, cy + r * .32f)
            lineTo(cx, cy + r * .16f); lineTo(cx + r * .17f, cy + r * .32f); close()
        }
        c.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe8ecf0.toInt() })
        c.restore()
        val x = cx + r + 12f
        val locationX = if (w >= 220f) w * .64f else null
        val width = ((locationX?.minus(12f) ?: (w - 14f)) - x).coerceAtLeast(18f)
        text(c, WindSpeed.value(speed, useMph).toString(), x, cy + 2f, min(34f, h * .43f), true, maxWidth = width, clarity = true)
        text(c, WindSpeed.unit(useMph), x, cy + min(20f, h * .27f), min(13f, h * .18f), true, maxWidth = width)
        if (locationX != null) location(c, city, locationX, cy + 3f, w - locationX - 18f,
            min(12f, h * .16f), maxLines = 2)
    }

    private fun drawForecast(context: Context, c: Canvas, w: Float, h: Float, city: String, temp: String, weather: WeatherResponse, useCelsius: Boolean) {
        if (h < 105f && w < 220f) return drawWeather(context, c, w, h, city, temp, weather.current?.weatherCode ?: 0, weather.current?.isDay == 1)
        if (w < 270f || h < 150f) return drawCompactForecast(context, c, w, h, weather, useCelsius)
        val pad = min(24f, w * .055f)
        val labelShift = if (location(c, city, pad, 27f, w * .40f) > 1) 8f else 0f
        text(c, temp, pad, h * .47f + labelShift, min(76f, h * .34f), true, maxWidth = w * .42f, clarity = true)
        val uv = weather.current?.uvIndex ?: 0.0
        if (uv >= 6) {
            text(c, "High UV today.", pad, h * .70f, 16f, true, maxWidth = w * .43f)
            text(c, "Use sun protection!", pad, h * .81f, 16f, true, maxWidth = w * .43f)
        } else {
            val message = ForecastHeadline.create(weather.hourly, weather.current?.time, useCelsius).substringBefore(". High")
            wrapText(c, message, pad, h * .64f, w * .42f, min(17f, w * .048f))
        }
        val daily = weather.daily ?: return
        val rightStart = w * .48f
        val colWidth = (w - rightStart - pad) / 4f
        for (i in 0..3) {
            val day = daily.time.getOrNull(i) ?: continue
            val center = rightStart + colWidth * (i + .5f)
            val dayName = runCatching { LocalDateTime.parse("${day}T00:00").format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) }.getOrDefault("--")
            text(c, dayName, center, h * .23f, min(16f, colWidth * .32f), true, align = Paint.Align.CENTER)
            val code = daily.weatherCodes.getOrNull(i) ?: 0
            val dayIconSize = min(42f, colWidth * .76f)
            val nightIconSize = min(38f, colWidth * .70f)
            icon(context, c, WeatherCodeMapper.getIconRes(code, true), center - dayIconSize / 2f, h * .30f, dayIconSize)
            icon(context, c, WeatherCodeMapper.getIconRes(code, false), center - nightIconSize / 2f, h * .53f, nightIconSize)
            val high = daily.maxTemperatures.getOrNull(i)?.let { "${temperature(it, useCelsius)}°" } ?: "--"
            text(c, high, center, h * .85f, min(21f, colWidth * .43f), true, maxWidth = colWidth * .9f, align = Paint.Align.CENTER, clarity = true)
        }
    }

    private fun drawWind(c: Canvas, w: Float, h: Float, speed: Double, direction: Double, city: String, useMph: Boolean) {
        if (h < 105f) return drawCompactWind(c, w, h, speed, direction, city, useMph)
        val pad = min(23f, w * .08f)
        text(c, "≋  Windy", pad, min(37f, h * .16f), min(24f, w * .10f), true)
        val cx = w / 2; val cy = h * .56f
        val r = min(w * .36f, h * .32f)
        c.drawCircle(cx, cy, r * 1.06f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r * 1.06f,
                intArrayOf(0x00121216, 0x00282d35, 0x4415171a, 0x00101114),
                floatArrayOf(0f, .70f, .91f, 1f), Shader.TileMode.CLAMP)
        })
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88ffffff.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.2f }
        c.drawCircle(cx, cy, r, ring)
        ring.color = 0x66ffffff; c.drawCircle(cx, cy, r * .79f, ring)
        ring.color = 0x30ffffff; c.drawCircle(cx, cy, r * .64f, ring)
        for (i in 0 until 48) {
            val a = i * PI / 24 - PI / 2
            val outer = r * .98f; val inner = r * if (i % 6 == 0) .89f else .95f
            ring.color = if (i % 6 == 0) 0x99ffffff.toInt() else 0x2bffffff
            c.drawLine(cx + (inner * cos(a)).toFloat(), cy + (inner * sin(a)).toFloat(),
                cx + (outer * cos(a)).toFloat(), cy + (outer * sin(a)).toFloat(), ring)
        }
        listOf("N", "E", "S", "W").forEachIndexed { i, s ->
            val a = i * PI / 2 - PI / 2
            val x = cx + (r * .79f * cos(a)).toFloat()
            val y = cy + (r * .79f * sin(a)).toFloat() + 5f
            text(c, s, x, y, 14f, true, align = Paint.Align.CENTER,
                color = if (i == 0) 0xfff05863.toInt() else if (i == 2) 0xff4a7de4.toInt() else Color.WHITE)
        }
        listOf("NE", "SE", "SW", "NW").forEachIndexed { i, label ->
            val a = (i * 90 + 45) * PI / 180 - PI / 2
            text(c, label, cx + (r * .79f * cos(a)).toFloat(), cy + (r * .79f * sin(a)).toFloat() + 4f,
                10f, false, align = Paint.Align.CENTER, color = 0x66ffffff)
        }
        drawCenteredWindReading(c, cx, cy, r, speed, useMph)
        val a = direction * PI / 180 - PI / 2
        val px = cx + (r * .91f * cos(a)).toFloat(); val py = cy + (r * .91f * sin(a)).toFloat()
        val pointer = Path().apply {
            moveTo(px + (9 * cos(a)).toFloat(), py + (9 * sin(a)).toFloat())
            lineTo(px + (5 * cos(a + 2.4)).toFloat(), py + (5 * sin(a + 2.4)).toFloat())
            lineTo(px + (5 * cos(a - 2.4)).toFloat(), py + (5 * sin(a - 2.4)).toFloat())
            close()
        }
        c.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff3b4148.toInt(); style = Paint.Style.FILL
            setShadowLayer(3f, 0f, 1f, 0xaa000000.toInt())
        })
        c.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffd9e1e8.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f })
    }

    /** Center the measured speed + unit block, including the gap, inside the compass. */
    private fun drawCenteredWindReading(c: Canvas, cx: Float, cy: Float, r: Float, speed: Double, useMph: Boolean) {
        val value = WindSpeed.value(speed, useMph).toString()
        val unit = WindSpeed.unit(useMph)
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = min(43f, r * .65f)
            typeface = fontBoldTypeface
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = min(13f, r * .20f)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        // Glyph bounds avoid the extra font leading around the larger number.
        val valueBounds = android.graphics.Rect()
        val unitBounds = android.graphics.Rect()
        valuePaint.getTextBounds(value, 0, value.length, valueBounds)
        unitPaint.getTextBounds(unit, 0, unit.length, unitBounds)
        val gap = r * .12f
        val top = cy - (valueBounds.height() + gap + unitBounds.height()) / 2f
        valuePaint.textAlign = Paint.Align.CENTER
        unitPaint.textAlign = Paint.Align.CENTER
        c.drawText(value, cx, top - valueBounds.top, valuePaint)
        c.drawText(unit, cx, top + valueBounds.height() + gap - unitBounds.top, unitPaint)
    }

    private fun location(c: Canvas, city: String, x: Float, y: Float, width: Float,
                         fontSize: Float = 12.5f, maxLines: Int = 2): Int {
        val pinColor = 0xbbe7edf4.toInt()
        val pin = Path().apply {
            moveTo(x + 4.5f, y + 1f)
            cubicTo(x + 3f, y - 1f, x, y - 4.3f, x, y - 7.5f)
            cubicTo(x, y - 13.5f, x + 9f, y - 13.5f, x + 9f, y - 7.5f)
            cubicTo(x + 9f, y - 4.3f, x + 6f, y - 1f, x + 4.5f, y + 1f)
            close()
        }
        c.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor; style = Paint.Style.STROKE; strokeWidth = 1.55f; strokeJoin = Paint.Join.ROUND
        })
        c.drawCircle(x + 4.5f, y - 7.5f, 1.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor; style = Paint.Style.STROKE; strokeWidth = 1.25f
        })
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            textSize = fontSize
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        return drawWrapped(c, city, x + 13f, y, (width - 13f).coerceAtLeast(10f), paint, maxLines, fontSize + 1f)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, bold: Boolean,
                     maxWidth: Float = Float.MAX_VALUE, align: Paint.Align = Paint.Align.LEFT,
                     color: Int = Color.WHITE, clarity: Boolean = false) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; textAlign = align
            typeface = if (clarity) {
                if (bold) fontBoldTypeface else fontTypeface
            } else {
                android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        val numericReading = value.any(Char::isDigit) || '°' in value || ':' in value
        val measuredWidth = p.measureText(value)
        if (numericReading && measuredWidth > maxWidth && maxWidth > 0f) {
            p.textSize *= maxWidth / measuredWidth
        }
        val shown = if (!numericReading && measuredWidth > maxWidth)
            TextUtils.ellipsize(value, p, maxWidth, TextUtils.TruncateAt.END).toString() else value
        c.drawText(shown, x, y, p)
    }

    private var fontTypeface: android.graphics.Typeface? = null
    private var fontBoldTypeface: android.graphics.Typeface? = null

    private fun wrapText(c: Canvas, value: String, x: Float, y: Float, width: Float, size: Float,
                         maxLines: Int = 3, bold: Boolean = true) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size
            typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        drawWrapped(c, value, x, y, width, paint, maxLines, size + 4f)
    }

    private fun drawWrapped(c: Canvas, value: String, x: Float, y: Float, width: Float,
                            paint: TextPaint, maxLines: Int, lineHeight: Float): Int {
        val words = value.trim().split(Regex("\\s+")).flatMap { word ->
            if (word.contains('-') && paint.measureText(word) > width) {
                word.split('-').mapIndexed { index, part ->
                    if (index < word.count { it == '-' }) "$part-" else part
                }
            } else listOf(word)
        }
        val lines = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            val candidate = when {
                line.isEmpty() -> word
                line.endsWith('-') -> "$line$word"
                else -> "$line $word"
            }
            if (paint.measureText(candidate) <= width) {
                line = candidate
            } else {
                if (line.isNotEmpty()) { lines += line; line = "" }
                var remainder = word
                while (paint.measureText(remainder) > width) {
                    val fit = paint.breakText(remainder, true, width, null).coerceAtLeast(1)
                    lines += remainder.take(fit)
                    remainder = remainder.drop(fit)
                }
                line = remainder
            }
        }
        if (line.isNotEmpty()) lines += line
        lines.take(maxLines).forEachIndexed { i, valueAtLine ->
            val content = if (i == maxLines - 1 && lines.size > maxLines)
                lines.drop(i).joinToString(" ") else valueAtLine
            val originalSize = paint.textSize
            val numericReading = content.any(Char::isDigit) || '°' in content || ':' in content
            if (numericReading && paint.measureText(content) > width && width > 0f) {
                paint.textSize *= width / paint.measureText(content)
            }
            val shown = if (numericReading) content else
                TextUtils.ellipsize(content, paint, width, TextUtils.TruncateAt.END).toString()
            c.drawText(shown, x, y + i * lineHeight, paint)
            paint.textSize = originalSize
        }
        return min(lines.size, maxLines)
    }

    private fun icon(context: Context, c: Canvas, id: Int, x: Float, y: Float, size: Float, tint: Int? = null) {
        val drawable = WeatherWidgetImages.drawable(context, id) ?: return
        val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: return
        val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: return
        val displaySize = min(size, 72f)
        val scale = displaySize / maxOf(intrinsicWidth, intrinsicHeight)
        val iconWidth = intrinsicWidth * scale
        val iconHeight = intrinsicHeight * scale
        val left = x + (size - iconWidth) / 2f
        val top = y + (size - iconHeight) / 2f
        tint?.let(drawable::setTint)
        drawable.setBounds(left.roundToInt(), top.roundToInt(), (left + iconWidth).roundToInt(), (top + iconHeight).roundToInt())
        drawable.draw(c)
    }

    private fun dayProgress(sunrise: String, sunset: String, current: String): Float {
        fun minute(s: String) = s.take(2).toIntOrNull()?.times(60)?.plus(s.drop(3).take(2).toIntOrNull() ?: 0) ?: 0
        val start = minute(sunrise); val end = minute(sunset)
        if (end <= start) return .5f
        return ((minute(current) - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    private class SunMessage(val headline: String, val note: String, val warm: Boolean)

    /** Picks a headline + playful note that fits where the sun currently is in its day. */
    private fun sunMessage(now: String, sunrise: String, sunriseTomorrow: String?, sunset: String): SunMessage {
        fun mins(s: String) = s.take(2).toIntOrNull()?.times(60)?.plus(s.drop(3).take(2).toIntOrNull() ?: 0)
        val n = mins(now); val rise = mins(sunrise); val set = mins(sunset)
        if (n == null || rise == null || set == null) return SunMessage("Sunset at $sunset", "Sun's doing its thing", false)
        // Rotates every half hour so the text feels alive without flickering on each refresh.
        fun pick(options: List<String>) = options[(n / 30) % options.size]
        val golden = set - 60
        return when {
            n < rise - 60 -> SunMessage("Sunrise at $sunrise", pick(listOf(
                "The sun's still asleep", "Night owls, this is you", "Shhh, the world is sleeping",
                "Dreamland hours", "Even the birds are snoozing")), false)
            n < rise -> SunMessage("Sunrise at $sunrise", pick(listOf(
                "Sunrise in ${rise - n} min!", "First light incoming", "Dawn is cracking in",
                "Here comes the sun")), true)
            n < rise + 90 -> SunMessage("Sunset at $sunset", pick(listOf(
                "Fresh morning light", "Rise and shine!", "The day just began",
                "Coffee and sunshine time")), false)
            n < golden - 180 -> SunMessage("Sunset at $sunset", pick(listOf(
                "Sun's climbing high", "Plenty of daylight left", "Soak up that vitamin D",
                "Peak sun energy", "Lots of day ahead")), false)
            n < golden -> SunMessage("Sunset at $sunset", pick(listOf(
                "Golden hour at ${minutesBefore(sunset, 60)}", "Golden hour at ${minutesBefore(sunset, 60)}",
                "Sun's heading west", "Afternoon glow building", "Camera roll warming up")), n >= golden - 60)
            n < set - 15 -> SunMessage("Sunset at $sunset", pick(listOf(
                "Golden hour time!", "Grab your camera!", "Magic light is on",
                "Everything looks better now", "Peak photo hour")), true)
            n < set -> SunMessage("Sunset in ${set - n} min", pick(listOf(
                "Last light, enjoy it", "Catch the show!", "Final golden moments")), true)
            n < set + 45 -> SunMessage("Sunrise at ${sunriseTomorrow ?: sunrise}", pick(listOf(
                "Sun just clocked out", "Twilight vibes", "Afterglow in the sky", "Blue hour is here")), false)
            else -> SunMessage("Sunrise at ${sunriseTomorrow ?: sunrise}", pick(listOf(
                "Sleep tight, see you soon", "Stars are on shift", "Night mode: on",
                "The sun will be back", "Good night, sunshine", "Moon's got this")), false)
        }
    }

    private fun minutesBefore(time: String, amount: Int): String {
        val hour = time.take(2).toIntOrNull() ?: return time
        val minute = time.drop(3).take(2).toIntOrNull() ?: return time
        val total = (hour * 60 + minute - amount).coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun temperature(value: Double, useCelsius: Boolean): Int =
        Temperature.value(value, useCelsius)

    fun initializeFont(context: Context) {
        if (fontTypeface == null) fontTypeface = ResourcesCompat.getFont(context, R.font.clarity_city_regular)
        if (fontBoldTypeface == null) fontBoldTypeface = ResourcesCompat.getFont(context, R.font.clarity_city_bold)
    }
}
