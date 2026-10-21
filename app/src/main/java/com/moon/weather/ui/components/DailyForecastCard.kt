package com.moon.weather.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.imageResource
import com.moon.weather.utils.WeatherCodeMapper
import com.moon.weather.utils.Temperature
import com.moon.weather.ui.theme.ClarityCity

@Composable
fun DailyForecastCard(
    dailyData: com.moon.weather.data.DailyWeather? = null,
    useCelsius: Boolean = true,
    modifier: Modifier = Modifier
) {
    val count = dailyData?.let { minOf(7, it.time.size) } ?: 0
    fun temperature(raw: Double?): Int {
        return Temperature.value(raw ?: 0.0, useCelsius)
    }
    val highs = if (dailyData != null) (0 until count).map { temperature(dailyData.maxTemperatures.getOrNull(it)) }
        else listOf(34, 31)
    val lows = if (dailyData != null) (0 until count).map { temperature(dailyData.minTemperatures.getOrNull(it)) }
        else listOf(27, 23)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val temperatureStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontFamily = ClarityCity)
    // Size both columns from the complete forecast, including signs, degrees and font scale.
    // The day label gives up space before a temperature ever wraps.
    fun columnWidth(values: List<String>, weight: FontWeight): Dp = with(density) {
        (values.maxOfOrNull { measurer.measure(AnnotatedString(it),
            style = temperatureStyle.copy(fontWeight = weight), softWrap = false, maxLines = 1).size.width }
            ?: 0).toDp().plus(1.dp).coerceAtLeast(28.dp)
    }
    val highWidth = columnWidth(highs.map { "$it" }, FontWeight.Medium)
    val lowWidth = columnWidth(lows.map { "$it°" }, FontWeight.Normal)
    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (dailyData != null) {
                // Open-Meteo returns 7 days of forecast, usually starting from today.
                for (i in 0 until minOf(7, dailyData.time.size)) {
                    val dateString = dailyData.time.getOrNull(i) ?: ""
                    val dayOfWeek = try {
                        val date = java.time.LocalDate.parse(dateString)
                        if (i == 0) "Today" else if (i == 1) "Tomorrow" else date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                    } catch (e: Exception) {
                        if (i == 0) "Today" else if (i == 1) "Tomorrow" else "Day ${i+1}"
                    }

                    val iconRes = WeatherCodeMapper.getIconRes(dailyData.weatherCodes.getOrNull(i) ?: 0, isDay = true)
                    DailyRow(
                        day = dayOfWeek,
                        high = highs[i],
                        low = lows[i],
                        isToday = i == 0,
                        iconRes = iconRes,
                        highColumnWidth = highWidth,
                        lowColumnWidth = lowWidth
                    )
                    
                    if (i < minOf(7, dailyData.time.size) - 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                DailyRow(
                    day = "Today",
                    high = 34,
                    low = 27,
                    isToday = true,
                    iconRes = com.moon.weather.R.drawable.clear_day,
                    highColumnWidth = highWidth,
                    lowColumnWidth = lowWidth
                )
                Spacer(modifier = Modifier.height(16.dp))
                DailyRow(
                    day = "Tomorrow",
                    high = 31,
                    low = 23,
                    isToday = false,
                    iconRes = com.moon.weather.R.drawable.cloudy,
                    highColumnWidth = highWidth,
                    lowColumnWidth = lowWidth
                )
            }
        }
    }
}

@Composable
fun DailyRow(
    day: String,
    high: Int,
    low: Int,
    isToday: Boolean,
    iconRes: Int,
    modifier: Modifier = Modifier,
    highColumnWidth: Dp = Dp.Unspecified,
    lowColumnWidth: Dp = Dp.Unspecified
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day,
            color = if (isToday) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        
        // Weather icon
        androidx.compose.foundation.Image(
            bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = "$high",
            color = if (isToday) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = ClarityCity,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            modifier = if (highColumnWidth.isSpecified) Modifier.width(highColumnWidth)
                else Modifier.widthIn(min = 28.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "$low°",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontFamily = ClarityCity,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.End,
            modifier = if (lowColumnWidth.isSpecified) Modifier.width(lowColumnWidth)
                else Modifier.widthIn(min = 28.dp)
        )
    }
}
