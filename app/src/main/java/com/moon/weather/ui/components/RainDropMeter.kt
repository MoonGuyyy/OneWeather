package com.moon.weather.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

@Composable
fun RainDropMeter(
    percent: Int,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.White.copy(alpha = 0.25f),
    fillColor: Color = Color.White.copy(alpha = 0.85f)
) {
    val p = (percent.coerceIn(0, 100) / 100f)
    Spacer(modifier = modifier.drawWithCache {
        val w = size.width
        val h = size.height

        // Parametric formula for a realistic raindrop:
        // x = q*sin(t) - (q*sin(t)) / D
        // y = q*cos(t) + (1-q+q*cos(t)) / D
        // where D = (1-q+q*cos(t))^2 + (q*sin(t))^2
        // Case q = 2/3 as requested.
        val q = 2f / 3f
        val points = mutableListOf<Offset>()
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        // Sample points to build the path and find bounds
        val steps = 60
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
            val cosT = kotlin.math.cos(t)
            val sinT = kotlin.math.sin(t)
            
            // Denominator D
            val d = (1f - q + q * cosT).let { it * it } + (q * sinT).let { it * it }
            
            val xRaw = q * sinT - (q * sinT) / d
            val yRaw = q * cosT + (1f - q + q * cosT) / d
            
            points.add(Offset(xRaw, yRaw))
            minX = minOf(minX, xRaw)
            maxX = maxOf(maxX, xRaw)
            minY = minOf(minY, yRaw)
            maxY = maxOf(maxY, yRaw)
        }

        val rawWidth = maxX - minX
        val rawHeight = maxY - minY
        val scale = minOf(w / rawWidth, h / rawHeight)
        val offsetX = (w - rawWidth * scale) / 2f
        val offsetY = (h - rawHeight * scale) / 2f

        val drop = Path().apply {
            points.forEachIndexed { i, pt ->
                val px = offsetX + (pt.x - minX) * scale
                // Flip Y because in raw coords the cusp (t=0) is at maxY, but in Canvas 0 is top.
                val py = offsetY + (maxY - pt.y) * scale 
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }

        onDrawBehind {
            // Keep the path cached while the chart scrolls.
            drawPath(drop, baseColor)
            clipPath(drop) {
                val fillTop = (h * (1f - p)).coerceIn(0f, h)
                drawRect(
                    color = fillColor,
                    topLeft = Offset(0f, fillTop),
                    size = androidx.compose.ui.geometry.Size(w, h - fillTop)
                )
            }

        }
    })
}
