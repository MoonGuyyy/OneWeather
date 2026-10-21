package com.moon.weather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.weather.R
import com.moon.weather.ui.pressHaptic
import com.moon.weather.ui.theme.ClarityCity
import com.moon.weather.ui.weatherGlass

/** Uses the same cached backdrop blur as the forecast cards, including older Android versions. */
@Composable
internal fun WeatherRecoveryCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false,
    showRetryIcon: Boolean = true
) {
    // The outer scroll surface also keeps the action reachable at large font scales/in landscape.
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center) {
        FrostedGlassCard(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                .testTag("weather-recovery-card").semantics { liveRegion = LiveRegionMode.Polite },
            cornerRadius = 32.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ForecastCloudEmblem()
                Spacer(Modifier.height(24.dp))
                Text(title, color = Color.White, fontFamily = ClarityCity, fontSize = 26.sp,
                    lineHeight = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Text(message, color = Color.White.copy(alpha = .88f), fontFamily = ClarityCity,
                    fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                val buttonShape = RoundedCornerShape(24.dp)
                Button(
                    onClick = onAction,
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .weatherGlass(buttonShape, Color.White.copy(alpha = .06f))
                        .pressHaptic(enabled = !isWorking).testTag("weather-recovery-action"),
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = .14f), contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = .10f), disabledContentColor = Color.White.copy(alpha = .85f)),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(
                        Color.White.copy(alpha = .48f), Color.White.copy(alpha = .16f)))),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    } else if (showRetryIcon) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (isWorking) stringResource(R.string.weather_retrying) else actionLabel,
                        fontFamily = ClarityCity, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Small decorative line illustration, drawn locally with vectors. */
@Composable
private fun ForecastCloudEmblem() {
    Box(Modifier.size(80.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFFA4E8F5).copy(alpha = .26f), Color.White.copy(alpha = .07f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .38f), Color.White.copy(alpha = .08f))), CircleShape),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            val scale = size.width / 48f
            val cloud = Path().apply {
                moveTo(14f * scale, 33f * scale)
                cubicTo(4f * scale, 33f * scale, 4f * scale, 19f * scale, 14f * scale, 19f * scale)
                cubicTo(16f * scale, 7f * scale, 33f * scale, 7f * scale, 35f * scale, 21f * scale)
                cubicTo(46f * scale, 21f * scale, 46f * scale, 33f * scale, 36f * scale, 33f * scale)
                lineTo(14f * scale, 33f * scale)
            }
            drawPath(cloud, Color.White.copy(alpha = .94f), style = Stroke(2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color(0xFFC9F4FF).copy(alpha = .8f), 1.4f * scale, Offset(17f * scale, 40f * scale))
            drawCircle(Color(0xFFC9F4FF).copy(alpha = .5f), 1.4f * scale, Offset(25f * scale, 40f * scale))
            drawCircle(Color(0xFFC9F4FF).copy(alpha = .28f), 1.4f * scale, Offset(33f * scale, 40f * scale))
        }
    }
}
