package com.moon.weather.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moon.weather.ui.LocalWeatherGlassTint
import com.moon.weather.ui.weatherGlass
import com.moon.weather.ui.theme.LocalWeatherDarkMode

/** Glass card backed by the weather image blurred once for the whole screen. */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    alpha: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val darkMode = LocalWeatherDarkMode.current
    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (if (darkMode) 0.25f else 0.42f) * alpha),
            Color.White.copy(alpha = (if (darkMode) 0.07f else 0.12f) * alpha)
        )
    )
    Box(
        modifier = modifier
            .weatherGlass(shape, LocalWeatherGlassTint.current, alpha = alpha, showSheen = false, crossfadeTint = true)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            ),
        content = content
    )
}
