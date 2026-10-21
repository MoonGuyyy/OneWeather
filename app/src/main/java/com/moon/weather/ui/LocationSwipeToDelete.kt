package com.moon.weather.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Distance, rather than fling velocity, arms deletion. Only release commits it. */
@Composable
internal fun LocationSwipeToDelete(
    locationKey: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onArmedHaptic: (() -> Unit)? = null,
    content: @Composable (Boolean) -> Unit
) {
    var width by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val delete by rememberUpdatedState(onDelete)
    val thresholdFeedback = deletionReadyHapticAction()
    val feedback by rememberUpdatedState(onArmedHaptic ?: thresholdFeedback)
    val lidOpen by animateFloatAsState(if (armed || dismissing) 1f else 0f,
        animationSpec = tween(140), label = "delete-lid")

    fun settle(remove: Boolean) {
        settleJob?.cancel()
        dismissing = remove
        settling = true
        armed = false
        settleJob = scope.launch {
            animate(offset, if (remove) -width.toFloat() else 0f,
                animationSpec = if (remove) tween<Float>(220) else spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow
                )) { value, _ -> offset = value }
            settling = false
            if (remove) delete()
        }
    }

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width }
        .clip(RoundedCornerShape(30.dp))
        .pointerInput(enabled, width) {
            if (!enabled || width == 0) return@pointerInput
            var distance = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    if (!dismissing) {
                        settleJob?.cancel()
                        settling = false
                        distance = -offset / .8f
                    }
                },
                onHorizontalDrag = { change, delta ->
                    if (!dismissing) {
                        change.consume()
                        distance = (distance - delta).coerceIn(0f, width.toFloat())
                        // A little resistance; the open lid means release will delete after a third-width pull.
                        offset = -distance * .8f
                        val ready = distance >= width / 3f
                        if (ready && !armed) feedback()
                        armed = ready
                    }
                },
                onDragEnd = { if (!dismissing) settle(armed) },
                onDragCancel = { if (!dismissing) settle(false) }
            )
        }) {
        Box(Modifier.matchParentSize()
            .graphicsLayer { alpha = if (offset < -.5f) 1f else 0f }
            .background(Color(0xFFE33E43))) {
            Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(32.dp)
                    .testTag("delete-reveal-$locationKey")
                    .semantics { stateDescription = if (armed || dismissing) "Release to delete" else "Swipe farther to delete" }
                    .graphicsLayer {
                        alpha = (-offset / 64.dp.toPx()).coerceIn(0f, 1f)
                    }) {
                val w = size.width
                val h = size.height
                val body = Path().apply {
                    moveTo(w * .25f, h * .40f); lineTo(w * .75f, h * .40f)
                    lineTo(w * .71f, h * .83f)
                    quadraticBezierTo(w * .705f, h * .88f, w * .65f, h * .88f)
                    lineTo(w * .35f, h * .88f)
                    quadraticBezierTo(w * .295f, h * .88f, w * .29f, h * .83f)
                    close()
                }
                drawPath(body, Color.White)
                // The lid lifts off the bin and tilts around its left edge when deletion is armed.
                withTransform({
                    translate(0f, -h * .1f * lidOpen)
                    rotate(-18f * lidOpen, Offset(w * .20f, h * .31f))
                }) {
                    drawRoundRect(Color.White, Offset(w * .20f, h * .29f), Size(w * .6f, h * .065f), CornerRadius(w * .025f))
                    drawRoundRect(Color.White, Offset(w * .42f, h * .22f), Size(w * .16f, h * .08f), CornerRadius(w * .025f))
                }
            }
        }
        Box(Modifier.fillMaxWidth().testTag("swipe-card-$locationKey")
            .graphicsLayer { translationX = offset }) {
            content(!dismissing && !settling)
        }
    }
}
