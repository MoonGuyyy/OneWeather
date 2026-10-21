package com.moon.weather.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.moon.weather.data.AppearanceMode
import com.moon.weather.R
import com.moon.weather.widget.WeatherWidgetBatterySettings
import com.moon.weather.ui.theme.LocalWeatherDarkMode
import kotlin.math.cos
import kotlin.math.sin

private data class SettingsColors(
    val background: Color, val card: Color, val text: Color,
    val secondary: Color, val accent: Color, val line: Color
)

private fun colors(dark: Boolean) = if (dark) SettingsColors(
    Color.Black, Color(0xFF171719), Color.White, Color(0xFF99999E),
    Color(0xFF3E7BFA), Color(0xFF39393B)
) else SettingsColors(
    Color(0xFFF2F2F2), Color.White, Color(0xFF080808), Color(0xFF77777D),
    Color(0xFF3478F6), Color(0xFFE3E3E6)
)

internal fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private enum class SettingsPage(val title: String) {
    ROOT("Settings"), APPEARANCE("Appearance"), TEMPERATURE("Temperature"), TIME("Time format"), WIND("Wind speed"), WIDGETS("Widget updates")
}

@Composable
fun SettingsDialog(
    useCelsius: Boolean,
    useMilitaryTime: Boolean,
    appearanceMode: AppearanceMode,
    animatedAlpha: Float,
    onUseCelsiusChange: (Boolean) -> Unit,
    onUseMilitaryTimeChange: (Boolean) -> Unit,
    onAppearanceChange: (AppearanceMode) -> Unit,
    onDismiss: () -> Unit,
    useMph: Boolean = false,
    onUseMphChange: (Boolean) -> Unit = {}
) {
    val dark = LocalWeatherDarkMode.current
    val palette = colors(dark)
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val activity = context.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var unrestricted by remember { mutableStateOf(WeatherWidgetBatterySettings.isUnrestricted(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                unrestricted = WeatherWidgetBatterySettings.isUnrestricted(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }
    DisposableEffect(activity, dark) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val previousStatus = controller?.isAppearanceLightStatusBars ?: false
        val previousNavigation = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            controller?.isAppearanceLightStatusBars = previousStatus
            controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    Surface(Modifier.fillMaxSize().graphicsLayer { alpha = animatedAlpha }, color = palette.background) {
    WeatherContentFrame { layout ->
        // The roomy One UI title scrolls away with the page; shorter displays still expose the controls.
        val headerHeight = (layout.height.dp * .3f).coerceIn(96.dp, 240.dp)
        key(page) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Box(Modifier.fillMaxWidth().height(headerHeight), contentAlignment = Alignment.Center) {
                    Text(page.title, color = palette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { if (page == SettingsPage.ROOT) onDismiss() else page = SettingsPage.ROOT },
                    modifier = Modifier.pressHaptic().padding(start = 6.dp, bottom = 20.dp).semantics { contentDescription = "Back" }
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        drawLine(palette.text, Offset(size.width * .65f, size.height * .15f),
                            Offset(size.width * .3f, size.height * .5f), 1.6.dp.toPx(), StrokeCap.Round)
                        drawLine(palette.text, Offset(size.width * .3f, size.height * .5f),
                            Offset(size.width * .65f, size.height * .85f), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
                when (page) {
                    SettingsPage.ROOT -> {
                        NavigationCard("Appearance", when (appearanceMode) {
                            AppearanceMode.SYSTEM -> "System default"
                            AppearanceMode.LIGHT -> "Light"
                            AppearanceMode.DARK -> "Dark"
                        }, SettingsPage.APPEARANCE, Color(0xFF73C900), palette) { page = SettingsPage.APPEARANCE }
                        Spacer(Modifier.height(22.dp))
                        NavigationCard("Temperature", if (useCelsius) "Celsius" else "Fahrenheit",
                            SettingsPage.TEMPERATURE, Color(0xFF437FF1), palette) { page = SettingsPage.TEMPERATURE }
                        Spacer(Modifier.height(22.dp))
                        NavigationCard("Time format", if (useMilitaryTime) "24-hour" else "12-hour",
                            SettingsPage.TIME, Color(0xFFF5A900), palette) { page = SettingsPage.TIME }
                        Spacer(Modifier.height(22.dp))
                        NavigationCard("Wind speed", if (useMph) "Miles per hour" else "Kilometers per hour",
                            SettingsPage.WIND, Color(0xFF19A4BA), palette) { page = SettingsPage.WIND }
                        Spacer(Modifier.height(22.dp))
                        NavigationCard("Widget updates", "Automatic",
                            SettingsPage.WIDGETS, Color(0xFF9362D8), palette) { page = SettingsPage.WIDGETS }
                    }
                    SettingsPage.APPEARANCE -> {
                        val followsSystem = appearanceMode == AppearanceMode.SYSTEM
                        SettingsCard(palette) {
                            Row(
                                Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                ThemePreview("Light", false, !dark, !followsSystem, palette, Modifier.weight(1f)) {
                                    onAppearanceChange(AppearanceMode.LIGHT)
                                }
                                ThemePreview("Dark", true, dark, !followsSystem, palette, Modifier.weight(1f)) {
                                    onAppearanceChange(AppearanceMode.DARK)
                                }
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = palette.line)
                            Row(
                                Modifier.fillMaxWidth().pressHaptic().toggleable(followsSystem, role = Role.Switch) {
                                    onAppearanceChange(if (it) AppearanceMode.SYSTEM else if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT)
                                }.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("System default", Modifier.weight(1f), color = palette.text, fontSize = 18.sp)
                                Switch(
                                    checked = followsSystem, onCheckedChange = null,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = palette.accent, checkedThumbColor = Color.White,
                                        checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent,
                                        uncheckedTrackColor = palette.secondary, uncheckedThumbColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    SettingsPage.TEMPERATURE -> SettingsCard(palette) {
                        Column(Modifier.selectableGroup()) {
                            ChoiceRow("Celsius", "°C", useCelsius, palette) { onUseCelsiusChange(true) }
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = palette.line)
                            ChoiceRow("Fahrenheit", "°F", !useCelsius, palette) { onUseCelsiusChange(false) }
                        }
                    }
                    SettingsPage.TIME -> SettingsCard(palette) {
                        Column(Modifier.selectableGroup()) {
                            ChoiceRow("24-hour", "17:00", useMilitaryTime, palette) { onUseMilitaryTimeChange(true) }
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = palette.line)
                            ChoiceRow("12-hour", "5 PM", !useMilitaryTime, palette) { onUseMilitaryTimeChange(false) }
                        }
                    }
                    SettingsPage.WIND -> SettingsCard(palette) {
                        Column(Modifier.selectableGroup()) {
                            ChoiceRow("Kilometers per hour", "km/h", !useMph, palette) { onUseMphChange(false) }
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = palette.line)
                            ChoiceRow("Miles per hour", "mph", useMph, palette) { onUseMphChange(true) }
                        }
                    }
                    SettingsPage.WIDGETS -> SettingsCard(palette) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Automatic updates", color = palette.text, fontSize = 20.sp)
                            Text("Widgets refresh about every 30 minutes, even when the app is closed. Android may delay updates to save battery. Failed updates retry when a connection is available.",
                                color = palette.secondary, fontSize = 15.sp)
                            Text(if (unrestricted) "Android has allowed background access for $appName."
                                else "$appName updates widgets automatically. You can also allow background access to help keep them fresh in power saving mode. Android will ask you to confirm.",
                                color = palette.secondary, fontSize = 15.sp)
                            Text("Current-location widgets use the last location found by the app. Opening $appName updates your location.",
                                color = palette.secondary, fontSize = 15.sp)
                            if (!unrestricted && activity != null) Button(onClick = { WeatherWidgetBatterySettings.request(activity) }) {
                                Text("Allow background updates")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    }
}

@Composable
private fun SettingsCard(c: SettingsColors, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card), content = content)
}

@Composable
private fun NavigationCard(title: String, summary: String, page: SettingsPage, iconColor: Color, c: SettingsColors, onClick: () -> Unit) {
    SettingsCard(c) {
        Row(
            Modifier.fillMaxWidth().pressHaptic().clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Canvas(Modifier.size(28.dp).clip(CircleShape).background(iconColor).padding(6.dp)) {
                val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                when (page) {
                    SettingsPage.APPEARANCE -> {
                        drawCircle(Color.White, size.width * .22f, style = stroke)
                        repeat(8) {
                            val a = it * Math.PI / 4
                            drawLine(Color.White,
                                center + Offset(cos(a).toFloat(), sin(a).toFloat()) * size.width * .35f,
                                center + Offset(cos(a).toFloat(), sin(a).toFloat()) * size.width * .47f,
                                stroke.width, StrokeCap.Round)
                        }
                    }
                    SettingsPage.TIME -> {
                        drawCircle(Color.White, size.width * .45f, style = stroke)
                        drawLine(Color.White, center, Offset(center.x, size.height * .2f), stroke.width, StrokeCap.Round)
                        drawLine(Color.White, center, Offset(size.width * .7f, center.y), stroke.width, StrokeCap.Round)
                    }
                    SettingsPage.WIND -> {
                        repeat(3) { i ->
                            val y = size.height * (.25f + i * .25f)
                            drawLine(Color.White, Offset(size.width * .08f, y), Offset(size.width * .72f, y),
                                stroke.width, StrokeCap.Round)
                            drawArc(Color.White, -90f, 180f, false,
                                Offset(size.width * .59f, y - size.height * .13f),
                                Size(size.width * .25f, size.height * .26f), style = stroke)
                        }
                    }
                    SettingsPage.WIDGETS -> {
                        drawRoundRect(Color.White, Offset(size.width * .08f, size.height * .2f),
                            Size(size.width * .75f, size.height * .6f), CornerRadius(size.width * .1f), style = stroke)
                        drawLine(Color.White, Offset(size.width * .94f, size.height * .4f),
                            Offset(size.width * .94f, size.height * .6f), stroke.width, StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width * .3f, size.height * .5f),
                            Offset(size.width * .6f, size.height * .5f), stroke.width, StrokeCap.Round)
                    }
                    else -> {
                        drawRoundRect(Color.White, Offset(size.width * .36f, size.height * .06f),
                            Size(size.width * .28f, size.height * .65f), CornerRadius(size.width * .14f), style = stroke)
                        drawCircle(iconColor, size.width * .24f, Offset(center.x, size.height * .74f))
                        drawCircle(Color.White, size.width * .24f, Offset(center.x, size.height * .74f), style = stroke)
                        drawLine(Color.White, Offset(center.x, size.height * .35f), Offset(center.x, size.height * .74f), stroke.width)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 18.sp, lineHeight = 23.sp)
                Text(summary, color = c.secondary, fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, example: String, selected: Boolean, c: SettingsColors, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressHaptic().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 20.sp)
            Text(example, color = c.secondary, fontSize = 14.sp)
        }
        RadioMark(selected, c)
    }
}

@Composable
private fun RadioMark(selected: Boolean, c: SettingsColors) {
    Box(Modifier.size(23.dp).border(2.dp, if (selected) c.accent else c.secondary, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
    }
}

@Composable
private fun ThemePreview(label: String, night: Boolean, selected: Boolean, enabled: Boolean, c: SettingsColors, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .pressHaptic(enabled).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.widthIn(max = 110.dp).fillMaxWidth().aspectRatio(.65f).graphicsLayer { alpha = if (enabled) 1f else .48f }) {
            val base = if (night) Color(0xFF171719) else Color(0xFFF3F3F6)
            val group = if (night) Color(0xFF29292D) else Color.White
            val bar = if (night) Color(0xFF414148) else Color(0xFFD9D9DF)
            fun rect(color: Color, x: Float, y: Float, w: Float, h: Float, radius: Float) =
                drawRoundRect(color, Offset(size.width * x, size.height * y), Size(size.width * w, size.height * h), CornerRadius(size.width * radius))
            rect(base, 0f, 0f, 1f, 1f, .12f)
            rect(group, .08f, .05f, .84f, .10f, .08f)
            drawCircle(bar, size.width * .04f, Offset(size.width * .83f, size.height * .10f))
            rect(group, .08f, .19f, .84f, .21f, .08f)
            rect(group, .08f, .44f, .84f, .51f, .08f)
            val rows = listOf(.26f, .34f, .53f, .65f, .77f, .88f)
            val accents = listOf(0xFF437FF1, 0xFF437FF1, 0xFF19A4BA, 0xFF9362D8, 0xFF9362D8, 0xFFF48D46)
            rows.forEachIndexed { i, y ->
                drawCircle(Color(accents[i]), size.width * .039f, Offset(size.width * .19f, size.height * y))
                rect(bar, .29f, y - .014f, if (i % 2 == 0) .44f else .38f, .028f, .015f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (selected) c.accent else c.secondary, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.graphicsLayer { alpha = if (enabled) 1f else .4f }) { RadioMark(selected, c) }
        Spacer(Modifier.height(4.dp))
    }
}
