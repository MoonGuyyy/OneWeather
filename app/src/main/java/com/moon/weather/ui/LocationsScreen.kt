package com.moon.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.moon.weather.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.weather.data.WeatherLocation
import com.moon.weather.data.WeatherResponse
import com.moon.weather.ui.theme.LocalWeatherDarkMode
import com.moon.weather.utils.WeatherCodeMapper
import androidx.core.view.WindowCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.moon.weather.utils.Temperature

@Composable
internal fun LocationsScreen(
    locations: List<WeatherLocation>, gpsLocation: WeatherLocation?,
    weather: Map<WeatherLocation, WeatherResponse>, useCelsius: Boolean, useMilitaryTime: Boolean,
    onLocationClick: (WeatherLocation) -> Unit, onDelete: (WeatherLocation) -> Unit,
    onReorder: (List<WeatherLocation>) -> Unit, onUseGps: () -> Unit,
    onSearch: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit
) {
    val dark = LocalWeatherDarkMode.current
    val activity = LocalContext.current.activity()
    DisposableEffect(activity, dark) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
        }
    }
    val background = if (dark) Color.Black else Color(0xFFF2F2F2)
    val text = if (dark) Color.White else Color(0xFF080808)
    val secondary = if (dark) Color(0xFF99999E) else Color(0xFF77777D)
    val card = if (dark) Color(0xFF171719) else Color.White
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF3E7BFA))
    var editing by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize().testTag("locations-screen"), color = background) {
    WeatherContentFrame(fillAvailableWidth = true) {
    Column(Modifier.fillMaxSize().testTag("locations-content").windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.pressHaptic(), onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = text, modifier = Modifier.size(32.dp)) }
            Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp), color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            IconButton(modifier = Modifier.pressHaptic(), onClick = onSearch) { Icon(Icons.Default.Add, "Add location", tint = text, modifier = Modifier.size(28.dp)) }
            IconButton(modifier = Modifier.pressHaptic(), onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = text, modifier = Modifier.size(26.dp)) }
        }
        ReorderableLocationsList(
            locations = locations, onLocationClick = onLocationClick,
            onDeleteLocation = onDelete, onReorderLocations = onReorder,
            modifier = Modifier.weight(1f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val edge = (14.dp.toPx() / size.height).coerceAtMost(.1f)
                    val mask = Brush.verticalGradient(0f to Color.Transparent, edge to Color.White, 1f to Color.White)
                    onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
                },
            header = gpsLocation?.let { gps -> {
                Box(Modifier.fillMaxWidth().pressHaptic().clickable(onClick = { onLocationClick(gps) })) {
                    LocationWeatherCard(gps, weather[gps], true, useCelsius, useMilitaryTime, card, text, secondary)
                }
            } },
            cardContent = { location, lifted ->
                LocationWeatherCard(location, weather[location], false, useCelsius, useMilitaryTime,
                    if (lifted) (if (dark) Color(0xFF29292D) else Color(0xFFE9EDF5)) else card,
                    text, secondary, if (editing) ({ onDelete(location) }) else null)
            },
            footer = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    if (gpsLocation == null) TextButton(modifier = Modifier.pressHaptic(), onClick = onUseGps, colors = buttonColors) { Text("Use current location") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(modifier = Modifier.pressHaptic(), onClick = onSearch, colors = buttonColors) { Text("Add location") }
                        TextButton(modifier = Modifier.pressHaptic(), onClick = { editing = !editing }, colors = buttonColors) { Text(if (editing) "Done" else "Edit locations") }
                    }
                    Text("Touch and hold a city to reorder it.", color = secondary, fontSize = 14.sp)
                    Spacer(Modifier.height(32.dp))
                    Text("Weather data by Open-Meteo", color = secondary, fontSize = 12.sp)
                }
            }
        )
    }
    }
    }
}

@Composable
private fun LocationWeatherCard(
    location: WeatherLocation, weather: WeatherResponse?, gps: Boolean, celsius: Boolean, military: Boolean,
    card: Color, text: Color, secondary: Color, onDelete: (() -> Unit)? = null
) {
    fun degrees(value: Double?) = value?.let { "${Temperature.value(it, celsius)}°" } ?: "--°"
    val current = weather?.current
    val date = remember(current?.time, military) {
        current?.time?.let { time -> runCatching {
            LocalDateTime.parse(time).format(DateTimeFormatter.ofPattern(
                if (military) "EEE, MMMM d 'at' HH:mm" else "EEE, MMMM d 'at' h:mm a", Locale.getDefault()))
        }.getOrNull() }.orEmpty()
    }
    val day = weather?.daily?.time?.indexOf(current?.time?.substringBefore('T'))?.takeIf { it >= 0 } ?: 0
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(card).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (gps) { Icon(Icons.Default.LocationOn, null, tint = text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                Text(location.name, color = text, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (location.country.isNotBlank()) Text(location.country, color = secondary, fontSize = 14.sp, lineHeight = 18.sp)
            Text(if (date.isEmpty()) "Loading weather…" else date, color = secondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null) Image(painterResource(WeatherCodeMapper.getIconRes(current.weatherCode, current.isDay == 1)),
                    WeatherCodeMapper.getWeatherDescription(current.weatherCode), Modifier.size(36.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(6.dp))
                Text(degrees(current?.temperature), color = text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text("${degrees(weather?.daily?.maxTemperatures?.getOrNull(day))} / ${degrees(weather?.daily?.minTemperatures?.getOrNull(day))}",
                color = secondary, fontSize = 13.sp)
        }
        if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.pressHaptic().size(40.dp)) {
            Icon(Icons.Default.Delete, "Remove ${location.name}", tint = secondary)
        }
    }
}
