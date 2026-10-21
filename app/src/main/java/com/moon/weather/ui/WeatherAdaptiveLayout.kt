package com.moon.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlin.math.max
import kotlin.math.min

/** All bounds are window-local dp, rather than device pixels or model-specific ratios. */
internal data class WeatherFold(val left: Float, val top: Float, val right: Float,
    val bottom: Float, val vertical: Boolean)

internal data class WeatherLayout(
    val left: Float, val top: Float, val width: Float, val height: Float,
    val columns: Int, val gap: Float
)

internal fun weatherLayout(width: Float, height: Float, fontScale: Float = 1f,
    fold: WeatherFold? = null, singlePane: Boolean = false,
    fillAvailableWidth: Boolean = false): WeatherLayout {
    val minimumColumn = 280f * fontScale.coerceIn(1f, 1.5f)
    var left = 0f
    var top = 0f
    var availableWidth = width
    var availableHeight = height
    var gap = 16f
    val intersects = fold != null && fold.right >= 0 && fold.left <= width &&
        fold.bottom >= 0 && fold.top <= height
    if (intersects) {
        if (fold.vertical) {
            val before = fold.left.coerceIn(0f, width)
            val after = (width - fold.right).coerceIn(0f, width)
            if (!singlePane && min(before, after) >= minimumColumn + 28f) {
                // Center equal-width lanes on the physical hinge, including an 8dp buffer.
                val lane = min(min(before, after) - 28f, 572f)
                gap = max(16f, fold.right - fold.left + 16f)
                left = fold.left - 8f - lane - 20f
                return WeatherLayout(left, 0f, lane * 2 + gap + 40f, height, 2, gap)
            }
            if (before >= after) availableWidth = (before - 8f).coerceAtLeast(0f)
            else { left = fold.right + 8f; availableWidth = (after - 8f).coerceAtLeast(0f) }
        } else {
            // Tabletop: keep the scroll surface and controls together on the larger half.
            val above = fold.top.coerceIn(0f, height)
            val below = (height - fold.bottom).coerceIn(0f, height)
            if (above > below) availableHeight = (above - 8f).coerceAtLeast(0f)
            else { top = fold.bottom + 8f; availableHeight = (below - 8f).coerceAtLeast(0f) }
        }
    }
    val maxWidth = if (singlePane) 640f else 1200f
    val contentWidth = if (fillAvailableWidth) availableWidth else min(availableWidth, maxWidth)
    left += (availableWidth - contentWidth) / 2
    val columns = if (!singlePane && contentWidth >= max(640f, minimumColumn * 2 + 56f)) 2 else 1
    return WeatherLayout(left, top, contentWidth, availableHeight, columns, gap)
}

private val LocalWeatherFoldingFeatures = compositionLocalOf<List<FoldingFeature>> { emptyList() }
internal data class WeatherPopupLayout(val pane: WeatherLayout, val origin: Offset, val density: Float,
    val windowWidth: Float)
internal val LocalWeatherPopupLayout = compositionLocalOf<WeatherPopupLayout?> { null }

@Composable
internal fun WeatherFoldProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val folds by produceState<List<FoldingFeature>>(emptyList(), activity, lifecycle) {
        if (activity != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity).collect { info ->
                value = info.displayFeatures.filterIsInstance<FoldingFeature>()
                    .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
            }
        }
    }
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val fold = folds.firstOrNull()?.let { it.asWeatherFold(origin, density.density) }
        val pane = weatherLayout(maxWidth.value, maxHeight.value, density.fontScale, fold, singlePane = true)
        CompositionLocalProvider(LocalWeatherFoldingFeatures provides folds,
            LocalWeatherPopupLayout provides WeatherPopupLayout(pane, origin, density.density, maxWidth.value), content = content)
    }
}

private fun FoldingFeature.asWeatherFold(origin: Offset, density: Float) = WeatherFold(
    (bounds.left - origin.x) / density, (bounds.top - origin.y) / density,
    (bounds.right - origin.x) / density, (bounds.bottom - origin.y) / density,
    orientation == FoldingFeature.Orientation.VERTICAL)

/** Shared by weather, settings, search and cities so no panel straddles a separating hinge. */
@Composable
internal fun WeatherContentFrame(
    modifier: Modifier = Modifier,
    singlePane: Boolean = true,
    fillAvailableWidth: Boolean = false,
    content: @Composable BoxScope.(WeatherLayout) -> Unit
) {
    val density = LocalDensity.current
    val features = LocalWeatherFoldingFeatures.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val fold = features.firstOrNull()?.let { it.asWeatherFold(origin, density.density) }
        val layout = weatherLayout(maxWidth.value, maxHeight.value, density.fontScale, fold, singlePane,
            fillAvailableWidth)
        Box(Modifier.align(Alignment.TopStart).absoluteOffset(layout.left.dp, layout.top.dp)
            .size(layout.width.dp, layout.height.dp)) { content(layout) }
    }
}
