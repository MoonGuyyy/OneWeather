package com.moon.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay
import com.moon.weather.utils.DaylightCurve

@Composable
fun SunriseSunsetCard(
    dailyWeather: com.moon.weather.data.DailyWeather?,
    currentTime: String? = null,
    modifier: Modifier = Modifier
) {
    val sunrise = dailyWeather?.sunrises?.getOrNull(0)
    val sunset = dailyWeather?.sunsets?.getOrNull(0)
    
    val initialNow = remember(currentTime) {
        try {
            if (currentTime != null) LocalDateTime.parse(currentTime) else LocalDateTime.now()
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }
    
    var now by remember(initialNow) { mutableStateOf(initialNow) }
    LaunchedEffect(initialNow) {
        while (true) {
            delay(15_000)
            now = now.plusSeconds(15)
        }
    }
    val dayProgress = remember(sunrise, sunset, now) { calculateDayProgress(sunrise, sunset, now) }
    val sunAlpha = remember(sunrise, sunset, now) { calculateSunAlpha(sunrise, sunset, now) }

    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Sunrise & Sunset",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Keep the app's curve shape with the widget's palette and glowing sun.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 12.dp)
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val t = dayProgress.coerceIn(0f, 1f)
                    val xStart = size.width * 0.02f
                    val xEnd = size.width * 0.98f
                    val yBase = size.height * 0.82f
                    val amplitude = size.height * 0.42f
                    val steps = 120

                    fun pointAt(progress: Float): Offset {
                        val p = progress.coerceIn(0f, 1f)
                        val x = xStart + (xEnd - xStart) * p
                        // Full cosine arch: rise to noon peak, then descend to sunset.
                        val y = yBase - amplitude * DaylightCurve.heightAt(p)
                        return Offset(x, y)
                    }

                    val fullPath = Path().apply {
                        val p0 = pointAt(0f)
                        moveTo(p0.x, p0.y)
                        for (i in 1..steps) {
                            val p = pointAt(i / steps.toFloat())
                            lineTo(p.x, p.y)
                        }
                    }

                    // Match the widget's violet/coral/gold curve and soft, pale sun.
                    for ((width, alpha) in listOf(20.dp to .07f, 11.dp to .11f)) {
                        drawPath(
                            fullPath,
                            Color(0xFFFFAE53).copy(alpha = alpha),
                            style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    drawPath(
                        fullPath,
                        brush = Brush.horizontalGradient(
                            DaylightCurve.colors.map { Color(it) },
                            startX = xStart,
                            endX = xEnd
                        ),
                        style = Stroke(4.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    val sunCenter = pointAt(t)
                    if (sunAlpha > 0f) {
                        val glowRadius = 32.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color(0xFFFFE69A).copy(alpha = .71f * sunAlpha),
                                    .28f to Color(0xFFFFE69A).copy(alpha = .39f * sunAlpha),
                                    .64f to Color(0xFFFFE69A).copy(alpha = .114f * sunAlpha),
                                    1f to Color(0xFFFFE69A).copy(alpha = 0f)
                                ),
                                center = sunCenter,
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = sunCenter
                        )
                        drawCircle(
                            Color(0xFFE8ECF0).copy(alpha = sunAlpha),
                            radius = 6.2.dp.toPx(),
                            center = sunCenter
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sunrise
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.moon.weather.R.drawable.clear_day),
                        contentDescription = "Sunrise",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = sunrise?.substringAfter('T')?.take(5) ?: "--:--",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Sunrise",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Sunset
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.moon.weather.R.drawable.clear_day),
                        contentDescription = "Sunset",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = sunset?.substringAfter('T')?.take(5) ?: "--:--",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Sunset",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun calculateDayProgress(sunrise: String?, sunset: String?, now: LocalDateTime): Float {
    if (sunrise.isNullOrBlank() || sunset.isNullOrBlank()) return 0.5f
    val sunriseTime = try { LocalDateTime.parse(sunrise).toLocalTime() } catch (e: Exception) { LocalTime.of(6, 0) }
    val sunsetTime = try { LocalDateTime.parse(sunset).toLocalTime() } catch (e: Exception) { LocalTime.of(18, 0) }
    val currentTime = now.toLocalTime()
    if (currentTime.isBefore(sunriseTime)) return 0f
    if (currentTime.isAfter(sunsetTime)) return 1f
    val sunriseMinutes = sunriseTime.hour * 60 + sunriseTime.minute
    val sunsetMinutes = sunsetTime.hour * 60 + sunsetTime.minute
    val currentMinutes = currentTime.hour * 60 + currentTime.minute
    val total = (sunsetMinutes - sunriseMinutes).coerceAtLeast(1)
    return ((currentMinutes - sunriseMinutes).toFloat() / total).coerceIn(0f, 1f)
}

private fun calculateSunAlpha(sunrise: String?, sunset: String?, now: LocalDateTime): Float {
    if (sunrise.isNullOrBlank() || sunset.isNullOrBlank()) return 1f
    val sunriseDateTime = try { LocalDateTime.parse(sunrise) } catch (e: Exception) { null }
    val sunsetDateTime = try { LocalDateTime.parse(sunset) } catch (e: Exception) { null }
    if (sunriseDateTime == null || sunsetDateTime == null) return 1f
    if (now.isBefore(sunriseDateTime)) return 0f
    if (now.isBefore(sunsetDateTime)) return 1f
    // Fade sun out smoothly right after sunset
    val elapsedSeconds = java.time.Duration.between(sunsetDateTime, now).seconds
    return (1f - (elapsedSeconds / 1800f)).coerceIn(0f, 1f)
}
