package com.moon.weather.ui

import android.widget.ImageView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.moon.weather.R
import com.moon.weather.widget.WeatherWidgetImages
import kotlin.math.roundToInt

@Composable
internal fun WeatherLoadingAnimation() {
    val context = LocalContext.current
    // Reuse the generated PNGs at a sampled size, instead of decoding their full canvases.
    val sun = remember(context) { WeatherWidgetImages.drawable(context, R.drawable.clear_day)!!.bitmap.asImageBitmap() }
    val cloud = remember(context) { WeatherWidgetImages.drawable(context, R.drawable.cloudy)!!.bitmap.asImageBitmap() }
    val transition = rememberInfiniteTransition(label = "weather-loading")
    val reveal by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sun-emerging")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(144.dp, 112.dp)) {
            val unit = size.width / 144f
            val sunSize = (64f * unit).roundToInt()
            drawImage(sun, dstOffset = IntOffset((64f * unit).roundToInt(), ((48f - 42f * reveal) * unit).roundToInt()),
                dstSize = IntSize(sunSize, sunSize), alpha = reveal, filterQuality = FilterQuality.Medium)
            val cloudWidth = (128f * unit).roundToInt()
            val cloudHeight = (cloudWidth.toFloat() * cloud.height / cloud.width).roundToInt()
            drawImage(cloud, dstOffset = IntOffset((8f * unit).roundToInt(), (30f * unit).roundToInt()),
                dstSize = IntSize(cloudWidth, cloudHeight), filterQuality = FilterQuality.Medium)
        }
        Spacer(Modifier.height(18.dp))
        TwidgetLoadingSpinner(Modifier.size(40.dp))
    }
}

/** Uses twidget's original four-dot animated-vector resources, including its motion and easing. */
@Composable
internal fun TwidgetLoadingSpinner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val drawable = remember(context) { AnimatedVectorDrawableCompat.create(context, R.drawable.oneui_spinner)!! }
    val host = remember { arrayOfNulls<ImageView>(1) }
    DisposableEffect(drawable, lifecycle) {
        val callback = object : Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(ignored: android.graphics.drawable.Drawable?) {
                host[0]?.post {
                    if (host[0]?.isAttachedToWindow == true && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        drawable.start()
                    }
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> drawable.start()
                Lifecycle.Event.ON_STOP -> drawable.stop()
                else -> Unit
            }
        }
        drawable.registerAnimationCallback(callback)
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) drawable.start()
        onDispose {
            lifecycle.removeObserver(observer)
            drawable.unregisterAnimationCallback(callback)
            drawable.stop()
            host[0] = null
        }
    }
    AndroidView(modifier = modifier, factory = { ImageView(it).apply {
        contentDescription = "Loading weather"
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(drawable)
        host[0] = this
    } })
}
