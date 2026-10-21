package com.moon.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import com.moon.weather.ui.LocalWeatherPopupLayout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.weather.data.HourlyWeather
import com.moon.weather.ui.theme.ClarityCity
import com.moon.weather.ui.LocalWeatherGlassOrigin
import com.moon.weather.ui.frostedPanel
import com.moon.weather.utils.WeatherCodeMapper
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private data class HourDetail(
    val dayLabel: String,
    val time: String,
    val iconRes: Int,
    val iconDescription: String,
    val temp: Int,
    val feelsLike: Int,
    val precip: Int,
    val wind: Int,
    val humidity: Int
)

/** Scrollable hour-by-hour breakdown opened from the "Next 48 hours" link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyDetailSheet(
    hourlyData: HourlyWeather?,
    currentTime: String?,
    useCelsius: Boolean,
    useMilitaryTime: Boolean,
    currentWeather: com.moon.weather.data.CurrentWeather? = null,
    onDismiss: () -> Unit
) {
    // Capture the activity inset before entering the sheet's separate popup window.
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val rows = remember(hourlyData, currentTime, currentWeather, useCelsius, useMilitaryTime) {
        buildRows(hourlyData, currentWeather?.time ?: currentTime, useCelsius, useMilitaryTime,currentWeather)
    }
    val minimumDrag = with(LocalDensity.current) { 140.dp.toPx() }
    val resistance = remember(minimumDrag) { SheetDismissResistance(minimumDrag) }
    val confirmDismiss = remember(resistance) { { value: androidx.compose.material3.SheetValue -> resistance.confirm(value) } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirmDismiss)
    val scope = rememberCoroutineScope()
    SideEffect { resistance.sheet = sheetState }
    val popup = LocalWeatherPopupLayout.current
    // The popup can have a different density and viewport from its calling Compose subtree.
    // Carry window pixels across that boundary and place the whole sheet inside the safe pane.
    val popupModifier = if (popup == null) Modifier else Modifier.layout { measurable, constraints ->
        val paneWidth = (popup.pane.width * popup.density).roundToInt().coerceIn(0, constraints.maxWidth)
        val paneHeight = (popup.pane.height * popup.density).roundToInt().coerceIn(0, constraints.maxHeight)
        val sheet = measurable.measure(constraints.copy(minWidth = 0, maxWidth = paneWidth,
            minHeight = 0, maxHeight = paneHeight))
        val left = (popup.origin.x + popup.pane.left * popup.density).roundToInt()
        val bottom = (popup.origin.y + (popup.pane.top + popup.pane.height) * popup.density).roundToInt()
        layout(sheet.width, sheet.height) {
            sheet.place(left - (constraints.maxWidth - sheet.width) / 2, bottom - constraints.maxHeight)
        }
    }
    ModalBottomSheet(
        modifier = popupModifier,
        sheetMaxWidth = if (popup != null) Dp.Unspecified else 640.dp,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
        windowInsets = WindowInsets(0),
        contentColor = Color.White
    ) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        // The popup is a different layout tree from the city pager; use window coordinates.
        CompositionLocalProvider(LocalWeatherGlassOrigin provides null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)
            .testTag("hourly-detail-surface")
            .observeSheetDrag(resistance, scope)
            .frostedPanel(shape)
            .padding(bottom = navigationPadding)) {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 4.dp).background(Color.White.copy(alpha = .35f), RoundedCornerShape(2.dp)))
            }
            Text(
                text = "Next 48 hours",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                text = ForecastHeadline.create(hourlyData, currentTime, useCelsius),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = .15f), modifier = Modifier.padding(horizontal = 20.dp))
            LazyColumn(Modifier.weight(1f).testTag("hourly-detail-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(rows.size) { index ->
                    val row = rows[index]
                    val showHeader = row.dayLabel != rows.getOrNull(index - 1)?.dayLabel
                    if (showHeader) {
                        Text(
                            text = row.dayLabel,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                    }
                    HourRow(row)
                }
            }
        }
        }
    }
}

@Composable
private fun HourRow(row: HourDetail) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.time, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.width(62.dp))
        Image(
            bitmap = ImageBitmap.imageResource(id = row.iconRes),
            contentDescription = row.iconDescription,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${row.temp}°",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = ClarityCity
            )
            Text("Feels ${row.feelsLike}°", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${row.precip}% rain", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
            Text(
                "${row.wind} km/h · ${row.humidity}% hum",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

private fun buildRows(
    data: HourlyWeather?,
    currentTime: String?,
    useCelsius: Boolean,
    useMilitaryTime: Boolean,
    currentWeather: com.moon.weather.data.CurrentWeather?
): List<HourDetail> {
    if (data == null) return emptyList()
    val currentHour = currentTime?.takeIf { it.length >= 13 }?.take(13)?.plus(":00") ?: data.time.firstOrNull().orEmpty()
    var start = data.time.indexOf(currentHour)
    if (start == -1) start = data.time.indexOfFirst { it >= currentHour }.coerceAtLeast(0)
    val today = runCatching { LocalDate.parse(data.time.getOrNull(start)?.substringBefore('T')) }.getOrNull()
    fun conv(c: Double) = (if (useCelsius) c else c * 9 / 5 + 32).roundToInt()
    return (start until minOf(start + 48, data.time.size)).map { i ->
        val stamp = data.time[i]
        val hour = stamp.substringAfter('T').substringBefore(':').toIntOrNull() ?: 0
        val date = runCatching { LocalDate.parse(stamp.substringBefore('T')) }.getOrNull()
        val dayLabel = when {
            date == null || today == null -> ""
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        val time = when {
            i == start -> "Now"
            useMilitaryTime -> String.format(Locale.getDefault(), "%02d:00", hour)
            else -> "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"
        }
        val conditions = hourlyConditions(data,i,i == start,currentWeather)
        HourDetail(
            dayLabel = dayLabel,
            time = time,
            iconRes = WeatherCodeMapper.getIconRes(conditions.weatherCode,conditions.isDay),
            iconDescription = WeatherCodeMapper.getWeatherDescription(conditions.weatherCode),
            temp = conv(conditions.temperature),
            feelsLike = conv(conditions.apparentTemperature),
            precip = data.precipitationProbabilities.getOrNull(i) ?: 0,
            wind = conditions.windSpeed.roundToInt(),
            humidity = conditions.humidity.roundToInt()
        )
    }
}
