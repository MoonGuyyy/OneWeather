package com.moon.weather.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
internal fun OnWeatherResume(onResume: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestResume()
        }
        // LifecycleRegistry also sends the current state to newly added observers.
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
