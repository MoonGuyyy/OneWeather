package com.moon.weather.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Keeps a quick, short fling from hiding the sheet, while preserving Back and outside taps. */
@OptIn(ExperimentalMaterial3Api::class)
internal class SheetDismissResistance(private val minimumDrag: Float) {
    lateinit var sheet: SheetState
    private var protectingDrag = false
    private var startOffset = 0f
    private var resetJob: Job? = null

    fun confirm(value: SheetValue): Boolean = value != SheetValue.Hidden ||
        !protectingDrag || sheet.requireOffset() - startOffset >= minimumDrag

    fun begin() {
        resetJob?.cancel()
        startOffset = sheet.requireOffset()
        protectingDrag = sheet.currentValue == SheetValue.Expanded
    }

    fun end(scope: CoroutineScope) {
        resetJob = scope.launch {
            // Let the sheet's draggable/nested-scroll handlers settle this same pointer-up first.
            withFrameNanos { }
            snapshotFlow {
                sheet.currentValue == SheetValue.Hidden ||
                    (sheet.targetValue == SheetValue.Expanded && abs(sheet.requireOffset() - startOffset) < 1f)
            }.first { it }
            protectingDrag = false
        }
    }
}

/** Observe without consuming: vertical list scrolling and the native sheet drag stay intact. */
internal fun Modifier.observeSheetDrag(resistance: SheetDismissResistance, scope: CoroutineScope) =
    pointerInput(resistance) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            resistance.begin()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                resistance.end(scope)
            }
        }
    }
