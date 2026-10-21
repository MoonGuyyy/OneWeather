package com.moon.weather.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LocationsTransition(visible: Boolean, backEnabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestBack by rememberUpdatedState(onBack)
    LaunchedEffect(visible) { if (visible) progress.snapTo(0f) }
    PredictiveBackHandler(enabled = visible && backEnabled) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            latestBack()
        } catch (cancelled: CancellationException) {
            scope.launch {
                progress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val fraction = progress.value
            shape = RoundedCornerShape((28f * fraction).dp)
            clip = fraction > 0f
            translationX = -size.width * fraction * .3f
            scaleX = 1f - .04f * fraction
            scaleY = 1f - .04f * fraction
            transformOrigin = TransformOrigin(0f, .5f)
            alpha = 1f - .2f * fraction
        }) { content() }
    }
}
