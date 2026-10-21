package com.moon.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.weather.ui.theme.ClarityCity

/** Original stacked readings above both forecast columns, kept clear of book-posture hinges. */
@Composable
internal fun WeatherCurrentConditions(
    temperature: Int, description: String, maxTemperature: String, minTemperature: String,
    feelsLike: Int, expanded: Boolean, gap: Dp, bottomSpace: Dp
) {
    val shadow = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 4f), 12f)
    val reducedShadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 2f), 6f)
    Column(Modifier.fillMaxWidth().testTag("weather-current-conditions")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f)) {
                WeatherTemperatureReading(temperature, reducedShadow)
                Column(Modifier.testTag("weather-current-details")) {
                    Text(description, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = reducedShadow))
                    Spacer(Modifier.height(8.dp))
                    Text("↑ $maxTemperature°   ↓ $minTemperature°", color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, fontFamily = ClarityCity, style = TextStyle(shadow = shadow))
                    Text("Feels like $feelsLike°", color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, fontFamily = ClarityCity, style = TextStyle(shadow = shadow))
                }
            }
            if (expanded) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(bottomSpace))
    }
}
