package com.moon.weather.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Paints the settings surface around a transparent window onto the system wallpaper. */
internal class WallpaperPreviewBackground(private val color: Int, private val radius: Float) : Drawable() {
    private val preview = Rect()
    private val visible = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@WallpaperPreviewBackground.color }

    fun updatePreview(area: Rect, clip: Rect) {
        if (preview == area && visible == clip) return
        preview.set(area)
        visible.set(clip)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val surface = Path().apply { addRect(RectF(bounds), Path.Direction.CW) }
        if (!visible.isEmpty) {
            val hole = Path().apply { addRoundRect(RectF(preview), radius, radius, Path.Direction.CW) }
            val clip = Path().apply { addRect(RectF(visible), Path.Direction.CW) }
            hole.op(clip, Path.Op.INTERSECT)
            surface.op(hole, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(surface, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
