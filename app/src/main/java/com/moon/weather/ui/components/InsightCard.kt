package com.moon.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.moon.weather.data.WeatherResponse
import com.moon.weather.ui.hapticFeedbackAction

/** The glass stays still; only the forecast insight content pages. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InsightCard(weather: WeatherResponse, useCelsius: Boolean, useMph: Boolean = false, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val insights = remember(weather, useCelsius, useMph) { WeatherInsights.create(weather, useCelsius, useMph) }
    if (insights.isEmpty()) return
    key(insights.map { it.category }) {
        val pager = rememberPagerState { insights.size }
        val tick = hapticFeedbackAction {}
        LaunchedEffect(pager) {
            var previous = pager.settledPage
            snapshotFlow { pager.settledPage }.collect {
                if (it != previous) { tick(); previous = it }
            }
        }
        FrostedGlassCard(modifier.testTag("weather-insight-card")) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val valueWidth = minOf(96.dp, (maxWidth - 40.dp) * .4f)
                val messageWidth = with(density) { (maxWidth - 40.dp - 12.dp - valueWidth).roundToPx().coerceAtLeast(1) }
                val labelWidth = with(density) { valueWidth.roundToPx().coerceAtLeast(1) }
                val messageStyle = baseStyle.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                val labelStyle = baseStyle.copy(fontSize = 12.sp, lineHeight = 15.sp)
                val requiredBodyHeight = insights.maxOf { insight ->
                    val messageHeight = measurer.measure(insight.message, style = messageStyle,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = messageWidth)).size.height
                    val labelHeight = measurer.measure(insight.label, style = labelStyle,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = labelWidth)).size.height
                    maxOf(messageHeight, with(density) { 32.sp.roundToPx() } + labelHeight)
                }
                val pagerHeight = with(density) {
                    maxOf((88f * fontScale.coerceAtLeast(1f)).dp,
                        requiredBodyHeight.toDp() + 16.sp.toDp() + 7.dp)
                }
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
                    HorizontalPager(pager, modifier = Modifier.fillMaxWidth().height(pagerHeight)
                        .testTag("weather-insight-pager"), userScrollEnabled = insights.size > 1, verticalAlignment = Alignment.Top) { page ->
                        val insight = insights[page]
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(insight.category, color = Color.White.copy(alpha = .8f), fontSize = 13.sp, lineHeight = 16.sp,
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(insight.message, color = Color.White, style = messageStyle, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(valueWidth)) {
                                    InsightValue(insight.value, baseStyle, Modifier.fillMaxWidth())
                                    Text(insight.label, color = Color.White.copy(alpha = .8f), style = labelStyle,
                                        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    if (insights.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                            repeat(insights.size) { index ->
                                Box(Modifier.size(6.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (index == pager.currentPage) .9f else .3f)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keep the reading intact; give wind units a quieter size on the same baseline. */
@Composable
private fun InsightValue(value: String, baseStyle: TextStyle, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val available = with(density) { maxWidth.roundToPx() }
        fun styledValue(size: Int) = buildAnnotatedString {
            val unitStart = value.lastIndexOf(' ').takeIf { value.endsWith("km/h") || value.endsWith("mph") }
            if (unitStart != null && unitStart >= 0) {
                append(value.substring(0, unitStart + 1))
                withStyle(SpanStyle(fontSize = (size * .55f).sp, fontWeight = FontWeight.Normal)) {
                    append(value.substring(unitStart + 1))
                }
            } else append(value)
        }
        var size = 28
        while (size > 1 && measurer.measure(styledValue(size),
            style = baseStyle.copy(fontSize = size.sp, fontWeight = FontWeight.Light),
            softWrap = false, maxLines = 1).size.width > available) size--
        Text(styledValue(size), color = Color.White,
            style = baseStyle.copy(fontSize = size.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light),
            maxLines = 1, softWrap = false, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth())
    }
}
