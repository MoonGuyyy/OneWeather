package com.moon.weather.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.moon.weather.ui.theme.LocalWeatherDarkMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Changes only the indicator's layer during a pull; weather cards do not move or recompose. */
@Stable
internal class WeatherPullRefreshState(
    private val scope: CoroutineScope,
    private val threshold: Float,
    val hiddenTop: Float,
    private val restingTop: Float,
    private val canPull: () -> Boolean,
    private val refresh: suspend () -> Unit,
    private val tick: () -> Unit
) {
    var indicatorTop by mutableFloatStateOf(hiddenTop)
        private set
    var refreshing by mutableStateOf(false)
        private set
    private var distance = 0f
    private var settling = false
    private var job: Job? = null

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.Drag || available.y >= 0f || distance <= 0f) return Offset.Zero
            val consumed = available.y.coerceAtLeast(-distance)
            distance += consumed
            updatePosition()
            return Offset(0f, consumed)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.Drag || available.y <= 0f || settling || !canPull()) return Offset.Zero
            distance += available.y
            updatePosition()
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (distance <= 0f) return Velocity.Zero
            release()
            return Velocity(0f, available.y)
        }
    }

    private fun updatePosition() {
        val progress = distance / threshold
        // Extra pulling meets increasing resistance instead of sending the circle far down-screen.
        val fraction = if (progress <= 1f) progress else 1f + .18f * (1f - 1f / progress)
        indicatorTop = hiddenTop + (restingTop - hiddenTop) * fraction
    }

    private fun release() {
        val armed = distance >= threshold
        distance = 0f
        settling = true
        refreshing = armed
        job = scope.launch {
            try {
                if (armed) {
                    tick()
                    val started = SystemClock.uptimeMillis()
                    coroutineScope {
                        launch { moveTo(restingTop) }
                        refresh()
                        // Avoid a flash when a request finishes immediately.
                        delay((350L - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0L))
                    }
                }
            } finally {
                refreshing = false
                moveTo(hiddenTop)
                settling = false
            }
        }
    }

    private suspend fun moveTo(top: Float) {
        animate(indicatorTop, top, animationSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow
        )) { value, _ -> indicatorTop = value }
    }

    fun dispose() { job?.cancel() }
}

@Composable
internal fun rememberWeatherPullRefreshState(
    key: Any?,
    canPull: () -> Boolean,
    onRefresh: suspend () -> Unit,
    onTick: (() -> Unit)? = null
): WeatherPullRefreshState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val statusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val view = LocalView.current
    val latestCanPull by rememberUpdatedState(canPull)
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestTick by rememberUpdatedState(onTick)
    val threshold = with(density) { 128.dp.toPx() }
    val hidden = with(density) { (-56).dp.toPx() }
    val resting = with(density) { (statusPadding + 48.dp).toPx() }
    val state = remember(key, threshold, hidden, resting) {
        WeatherPullRefreshState(scope, threshold, hidden, resting,
            canPull = { latestCanPull() }, refresh = { latestRefresh() }, tick = {
                latestTick?.invoke() ?: view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    else HapticFeedbackConstants.CLOCK_TICK
                )
            })
    }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}

@Composable
internal fun WeatherRefreshIndicator(state: WeatherPullRefreshState, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.indicatorTop > state.hiddenTop + .5f } }
    if (!visible) return
    val dark = LocalWeatherDarkMode.current
    Surface(
        modifier = modifier.size(44.dp).graphicsLayer { translationY = state.indicatorTop }
            .testTag("weather-refresh-indicator"),
        shape = CircleShape,
        color = if (dark) Color(0xFF172B38) else Color(0xFFF7FBFF),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) { TwidgetLoadingSpinner(Modifier.size(28.dp)) }
    }
}
