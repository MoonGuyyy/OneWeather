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

@Composable
fun PressureVisibilityRow(currentWeather: com.moon.weather.data.CurrentWeather?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PressureCard(
            pressure = currentWeather?.pressure?.toInt() ?: 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        VisibilityCard(
            visibility = currentWeather?.visibility?.toInt() ?: 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun PressureCard(pressure: Int, modifier: Modifier = Modifier) {
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
                    bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.moon.weather.R.drawable.clear_day),
                    contentDescription = "Pressure",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pressure",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "$pressure mb",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val pressureDescription = when {
                pressure < 1000 -> "Low"
                pressure < 1020 -> "Normal"
                pressure < 1040 -> "High"
                else -> "Very High"
            }
            
            Text(
                text = pressureDescription,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun VisibilityCard(visibility: Int, modifier: Modifier = Modifier) {
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
                    bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = com.moon.weather.R.drawable.cloudy),
                    contentDescription = "Visibility",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Visibility",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Convert meters to kilometers with two decimal places
            val visibilityKm = visibility / 1000.0
            
            Text(
                text = String.format(java.util.Locale.getDefault(), "%.2f km", visibilityKm),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val visibilityDescription = when {
                visibilityKm < 1 -> "Very poor"
                visibilityKm < 5 -> "Poor"
                visibilityKm < 10 -> "Moderate"
                visibilityKm < 20 -> "Good"
                else -> "Excellent"
            }
            
            Text(
                text = visibilityDescription,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}
