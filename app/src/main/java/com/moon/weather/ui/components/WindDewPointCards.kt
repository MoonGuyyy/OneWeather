package com.moon.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.weather.utils.WindSpeed

@Composable
fun WindDewPointRow(currentWeather: com.moon.weather.data.CurrentWeather?, modifier: Modifier = Modifier, useMph: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WindCard(
            windSpeed = currentWeather?.windSpeed?.toInt() ?: 0,
            windDirection = currentWeather?.windDirection?.toInt() ?: 0,
            useMph = useMph,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        DewPointCard(
            dewPoint = currentWeather?.dewPoint?.toInt() ?: 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun WindCard(windSpeed: Int, windDirection: Int, modifier: Modifier = Modifier, useMph: Boolean = false) {
    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.moon.weather.R.drawable.windy),
                    contentDescription = "Wind",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Wind",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "${WindSpeed.value(windSpeed.toDouble(), useMph)} ${WindSpeed.unit(useMph)}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val windDescription = when {
                windSpeed < 5 -> "Calm"
                windSpeed < 20 -> "Light breeze"
                windSpeed < 40 -> "Moderate wind"
                windSpeed < 60 -> "Strong wind"
                else -> "Very strong"
            }
            
            Text(
                text = windDescription,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DewPointCard(dewPoint: Int, modifier: Modifier = Modifier) {
    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.moon.weather.R.drawable.light_rain_day),
                    contentDescription = "Dew Point",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dew Point",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "$dewPoint°",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val dewPointDescription = when {
                dewPoint < 10 -> "Very comfortable"
                dewPoint < 15 -> "Comfortable"
                dewPoint < 20 -> "Slightly humid"
                dewPoint < 25 -> "Humid"
                else -> "Very humid"
            }
            
            Text(
                text = dewPointDescription,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}
