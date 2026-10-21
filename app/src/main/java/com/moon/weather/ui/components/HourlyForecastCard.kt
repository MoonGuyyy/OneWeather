package com.moon.weather.ui.components

import com.moon.weather.ui.pressHaptic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ImageBitmap
import com.moon.weather.utils.WeatherCodeMapper
import com.moon.weather.utils.Temperature
import com.moon.weather.ui.theme.ClarityCity
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt

private val HourlyItemWidth = 46.dp
private val HourlyItemSpacing = 4.dp

data class HourlyWeatherItemUI(
    val time: String,
    val temp: Int,
    val tempCelsius: Int,
    val precipChance: Int,
    val iconRes: Int,
    val glowColor: Color,
    val description: String = "Weather Icon"
)

@Composable
fun HourlyForecastCard(
    hourlyData: com.moon.weather.data.HourlyWeather? = null,
    currentTime: String? = null,
    useCelsius: Boolean = true,
    useMilitaryTime: Boolean = true,
    modifier: Modifier = Modifier,
    currentWeather: com.moon.weather.data.CurrentWeather? = null,
    detailsState: androidx.compose.runtime.MutableState<Boolean>? = null
) {
    val items = remember(hourlyData, currentTime, currentWeather, useCelsius, useMilitaryTime) {
        if (hourlyData != null) {
            val list = mutableListOf<HourlyWeatherItemUI>()
            val currentHourStr = (currentWeather?.time ?: currentTime)?.takeIf { it.length >= 13 }?.take(13)?.plus(":00")
                ?: hourlyData.time.firstOrNull().orEmpty()
            
            var startIdx = hourlyData.time.indexOf(currentHourStr)
            if (startIdx == -1) {
                // Fallback: find the first time that is >= current time
                startIdx = hourlyData.time.indexOfFirst { it >= currentHourStr }.coerceAtLeast(0)
            }

            for (i in startIdx until minOf(startIdx + 48, hourlyData.time.size)) {
                val timeString = hourlyData.time.getOrNull(i) ?: ""
                val hourStr = timeString.substringAfter("T").substringBefore(":")
                val hourInt = hourStr.toIntOrNull() ?: 0
                
                val conditions = hourlyConditions(hourlyData,i,i == startIdx,currentWeather)
                val itemIsDay = conditions.isDay

                // Format time based on setting
                val displayTime = when {
                    i == startIdx -> "Now"
                    useMilitaryTime -> String.format(java.util.Locale.getDefault(), "%02d:00", hourInt)
                    else -> {
                        val amPm = if (hourInt < 12) "AM" else "PM"
                        val displayHour = when {
                            hourInt == 0 -> 12
                            hourInt > 12 -> hourInt - 12
                            else -> hourInt
                        }
                        "$displayHour $amPm"
                    }
                }

                val weatherCode = conditions.weatherCode
                val iconRes = WeatherCodeMapper.getIconRes(weatherCode, itemIsDay)
                val glowColor = when (weatherCode) {
                    in 95..99 -> Color(0xFFFFCE60)
                    in 71..77, 85, 86 -> Color(0xFFA4E5FF)
                    in 51..67, in 80..82 -> Color(0xFF64BCFF)
                    else -> if (itemIsDay) Color(0xFFFFD46B) else Color(0xFFB2CAFF)
                }

                // Convert temperature if needed
                val temp = Temperature.value(conditions.temperature, true)
                val displayTemp = Temperature.value(conditions.temperature, useCelsius)

                list.add(
                    HourlyWeatherItemUI(
                        time = displayTime,
                        temp = displayTemp,
                        tempCelsius = temp,
                        precipChance = hourlyData.precipitationProbabilities.getOrNull(i) ?: 0,
                        iconRes = iconRes,
                        glowColor = glowColor,
                        description = WeatherCodeMapper.getWeatherDescription(weatherCode)
                    )
                )
            }
            list
        } else {
            emptyList()
        }
    }

    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = remember(hourlyData, currentTime, useCelsius) {
                    ForecastHeadline.create(hourlyData, currentTime, useCelsius)
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 7.dp)
            )

            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                thickness = 0.5.dp, color = Color.White.copy(alpha = .35f)
            )
            Spacer(Modifier.height(7.dp))
            val scrollState = rememberLazyListState()
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val edge = (24.dp.toPx() / size.width).coerceIn(0f, .2f)
                        val mask = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            edge to Color.White,
                            (1f - edge) to Color.White,
                            1f to Color.Transparent
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(mask, blendMode = BlendMode.DstIn)
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GraphCanvas(data = items, listState = scrollState, modifier = Modifier.fillMaxSize())
                    LazyRow(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(HourlyItemSpacing)
                    ) {
                        items(items.size, key = { it }) { index ->
                            HourlyItem(item = items[index], modifier = Modifier.width(HourlyItemWidth).fillMaxHeight()
                                .testTag("hourly-item-$index"))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            val localDetailsState = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            var showDetails by (detailsState ?: localDetailsState)
            if (showDetails) {
                HourlyDetailSheet(
                    hourlyData = hourlyData,
                    currentTime = currentTime,
                    useCelsius = useCelsius,
                    useMilitaryTime = useMilitaryTime,
                    currentWeather = currentWeather,
                    onDismiss = { showDetails = false }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressHaptic().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showDetails = true }
                    )
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next 48 hours",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun GraphCanvas(data: List<HourlyWeatherItemUI>, listState: LazyListState, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return

    Spacer(modifier = modifier.drawWithCache {
        val minTemp = data.minOf { it.temp }
        val maxTemp = data.maxOf { it.temp }
        val tempRange = (maxTemp - minTemp).coerceAtLeast(1)
        val widthPerItem = HourlyItemWidth.toPx() + HourlyItemSpacing.toPx()
        val halfWidth = HourlyItemWidth.toPx() / 2f

        val graphTopMargin = 24.dp.toPx()
        val iconHeight = 32.dp.toPx()
        val iconSpacing = 4.dp.toPx()
        val tempTextHeight = 20.dp.toPx()
        val tempBottomSpacing = 10.dp.toPx()
        val graphTop = graphTopMargin + iconHeight + iconSpacing + tempTextHeight + tempBottomSpacing
        val graphHeight = minOf(38.dp.toPx(), (size.height - 30.dp.toPx() - graphTop).coerceAtLeast(0f))

        val yPositions = data.map { item ->
            val normalizedY = 1f - ((item.temp - minTemp).toFloat() / tempRange)
            graphTop + normalizedY * graphHeight
        }
        val segmentColors = data.map { getTemperatureColor(it.tempCelsius) }
        val curves = hourlyTemperatureCurve(yPositions)
        val segment = Path()
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dotRadius = 4.dp.toPx()
        val dotColor = Color.White.copy(alpha = 0.7f)
        onDrawBehind {
            val scrollOffset = listState.firstVisibleItemIndex * widthPerItem + listState.firstVisibleItemScrollOffset
            val first = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
            val last = ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first + 8) + 1)
                .coerceAtMost(data.lastIndex)
            for (index in first..last) {
                val x = index * widthPerItem + halfWidth - scrollOffset
                if (index > 0) {
                    val previousX = x - widthPerItem
                    val curve = curves[index - 1]
                    segment.reset()
                    segment.moveTo(previousX, curve.startY)
                    segment.cubicTo(previousX + widthPerItem / 3f, curve.control1Y,
                        x - widthPerItem / 3f, curve.control2Y, x, curve.endY)
                    drawPath(segment, segmentColors[index], style = stroke)
                }
            }
            // Paint markers after every curve so the next segment cannot cover them.
            for (index in first..last) {
                val x = index * widthPerItem + halfWidth - scrollOffset
                drawCircle(dotColor, radius = dotRadius, center = Offset(x, yPositions[index]))
            }
        }
    })
}

private fun getTemperatureColor(temperature: Int): Color {
    // Use absolute temperature ranges with smooth transitions (in Celsius)
    return when {
        temperature < 0 -> Color(0xFF4FC3F7) // Light Blue - Freezing
        temperature < 10 -> {
            // Transition from Light Blue to Blue
            val progress = (temperature - 0) / 10f
            lerp(Color(0xFF4FC3F7), Color(0xFF29B6F6), progress)
        }
        temperature < 20 -> {
            // Transition from Blue to Green
            val progress = (temperature - 10) / 10f
            lerp(Color(0xFF29B6F6), Color(0xFF66BB6A), progress)
        }
        temperature < 30 -> {
            // Transition from Green to Orange
            val progress = (temperature - 20) / 10f
            lerp(Color(0xFF66BB6A), Color(0xFFFFA726), progress)
        }
        temperature < 40 -> {
            // Transition from Orange to Red
            val progress = (temperature - 30) / 10f
            lerp(Color(0xFFFFA726), Color(0xFFEF5350), progress)
        }
        else -> Color(0xFFEF5350) // Red - Very Hot
    }
}

// Linear interpolation between two colors
private fun lerp(startColor: Color, endColor: Color, progress: Float): Color {
    return Color(
        red = lerp(startColor.red, endColor.red, progress),
        green = lerp(startColor.green, endColor.green, progress),
        blue = lerp(startColor.blue, endColor.blue, progress),
        alpha = lerp(startColor.alpha, endColor.alpha, progress)
    )
}

private fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

@Composable
fun HourlyItem(item: HourlyWeatherItemUI, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = item.time,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .drawWithCache {
                    val radius = 16.dp.toPx()
                    val glow = Brush.radialGradient(
                        0f to item.glowColor.copy(alpha = 0.15f),
                        .2f to item.glowColor.copy(alpha = 0.12f),
                        .45f to item.glowColor.copy(alpha = 0.065f),
                        .7f to item.glowColor.copy(alpha = 0.018f),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius
                    )
                    onDrawBehind { drawCircle(glow, radius = radius) }
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = item.iconRes),
                contentDescription = item.description,
                modifier = Modifier.size(24.dp),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${item.temp}°",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = ClarityCity
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            RainDropMeter(item.precipChance, Modifier.size(10.dp, 13.dp))
            Text("${item.precipChance}%", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
        }

    }
}




