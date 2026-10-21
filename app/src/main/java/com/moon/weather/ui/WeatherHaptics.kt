package com.moon.weather.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

@Composable
internal fun hapticFeedbackAction(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34)
            HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
        action()
    }
}

/** A distinct confirmation cue for crossing the release-to-delete threshold. */
@Composable
internal fun deletionReadyHapticAction(): () -> Unit {
    val view = LocalView.current
    return {
        val delivered = view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        if (!delivered && Build.VERSION.SDK_INT >= 30)
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/** Observes the initial down without consuming the button or scrolling gesture. */
internal fun Modifier.pressHaptic(enabled: Boolean = true, feedback: (() -> Unit)? = null): Modifier = composed {
    val view = LocalView.current
    if (!enabled) this else pointerInput(view, feedback) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (feedback != null) feedback() else view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START
                else HapticFeedbackConstants.CLOCK_TICK)
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        }
    }
}
