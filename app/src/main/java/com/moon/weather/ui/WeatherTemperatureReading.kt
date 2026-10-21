package com.moon.weather.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.moon.weather.ui.theme.ClarityCity

/** Keep signs, three-digit temperatures and degrees together on narrow/large-text windows. */
@Composable
internal fun WeatherTemperatureReading(temperature: Int, shadow: Shadow) {
    val text = "$temperature°"
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = TextStyle(fontFamily = ClarityCity, fontWeight = FontWeight.Normal, shadow = shadow)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = with(density) { maxWidth.roundToPx() }
        var fontSize = 96
        while (fontSize > 1 && measurer.measure(AnnotatedString(text),
            style = baseStyle.copy(fontSize = fontSize.sp), softWrap = false, maxLines = 1).size.width > available) fontSize--
        Text(text, color = Color.White, style = baseStyle.copy(fontSize = fontSize.sp, lineHeight = fontSize.sp),
            maxLines = 1, softWrap = false, modifier = Modifier.testTag("current-temperature"))
    }
}
