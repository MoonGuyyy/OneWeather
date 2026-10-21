package com.moon.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun UVIndexHumidityRow(currentWeather: com.moon.weather.data.CurrentWeather?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UVIndexCard(
            uvIndex = currentWeather?.uvIndex?.roundToInt() ?: 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        HumidityCard(
            humidity = currentWeather?.humidity?.toInt() ?: 0,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun UVIndexCard(uvIndex: Int, modifier: Modifier = Modifier) {
    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "UV index",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val uvLevel = when {
                uvIndex <= 2 -> "Low"
                uvIndex <= 5 -> "Moderate"
                uvIndex <= 7 -> "High"
                uvIndex <= 10 -> "Very High"
                else -> "Extreme"
            }
            
            Text(
                text = "$uvLevel right now",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "$uvLevel ($uvIndex)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // UV Bar: always fully shown (full gradient), with a moving numbered marker.
            val clampedUv = uvIndex.coerceIn(0, 11)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val markerDiameter = 22.dp
                val barHeight = 6.dp
                val barShape = RoundedCornerShape(3.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(barShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50), // Green
                                    Color(0xFFFFEB3B), // Yellow
                                    Color(0xFFFF9800), // Orange
                                    Color(0xFF9C27B0)  // Purple
                                )
                            )
                        )
                )

                val markerColor = when {
                    clampedUv <= 2 -> Color(0xFF4CAF50)
                    clampedUv <= 5 -> Color(0xFFFFEB3B)
                    clampedUv <= 7 -> Color(0xFFFF9800)
                    else -> Color(0xFF9C27B0)
                }

                // Align *center* of marker with UV position along the bar, clamped to edges.
                val fraction = clampedUv / 11f
                val maxLeft = maxWidth - markerDiameter
                val desiredLeft = (maxWidth * fraction) - (markerDiameter / 2f)
                val markerLeft: Dp = desiredLeft.coerceIn(0.dp, maxLeft)
                Box(
                    modifier = Modifier
                        .offset(x = markerLeft)
                        .size(markerDiameter)
                        .clip(RoundedCornerShape(50))
                        .graphicsLayer {
                            // Subtle depth without an outline.
                            shadowElevation = 6f
                            shape = RoundedCornerShape(50)
                            clip = true
                        }
                        .background(markerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = clampedUv.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HumidityCard(humidity: Int, modifier: Modifier = Modifier) {
    FrostedGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Humidity",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Current humidity",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "$humidity%",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Humidity Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                val fraction = (humidity.coerceIn(0, 100) / 100f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            // Subtle blue gradient for depth.
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF4FC3F7), // lighter
                                    Color(0xFF03A9F4), // base
                                    Color(0xFF0288D1)  // slightly deeper
                                )
                            )
                        )
                )
            }
        }
    }
}
