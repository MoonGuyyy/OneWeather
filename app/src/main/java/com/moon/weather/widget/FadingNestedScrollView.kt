package com.moon.weather.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/**
 * NestedScrollView that smoothly fades out ONLY the portion of content
 * that scrolls up underneath the pinned widget preview.
 *
 * Uses software layer rendering with PorterDuff DST_IN and hardware clipping
 * to guarantee that content NEVER scrolls into the preview area or above it.
 */
class FadingNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    var fadeTop: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                updateShader()
                invalidate()
            }
        }

    var fadeHeight: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                updateShader()
                invalidate()
            }
        }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private var baseGradient: LinearGradient? = null
    private val shaderMatrix = Matrix()

    init {
        // Software layer ensures PorterDuff DST_IN blend mode executes reliably on all Android GPU drivers
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun updateShader() {
        if (width <= 0 || height <= 0 || fadeHeight <= 0f) {
            baseGradient = null
            maskPaint.shader = null
            return
        }
        val h = height.toFloat()
        val fTop = fadeTop.coerceIn(0f, h)
        val fBottom = (fadeTop + fadeHeight).coerceIn(0f, h)

        val colors = intArrayOf(
            0x00FFFFFF,
            0x00FFFFFF,
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt()
        )
        val positions = floatArrayOf(
            0f,
            (fTop / h).coerceIn(0f, 1f),
            (fBottom / h).coerceIn(0f, 1f),
            1f
        )
        val gradient = LinearGradient(
            0f, 0f, 0f, h,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )
        baseGradient = gradient
        maskPaint.shader = gradient
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val gradient = baseGradient
        if (fadeHeight <= 0f || fadeTop <= 0f || gradient == null) {
            super.dispatchDraw(canvas)
            return
        }
        val sx = scrollX.toFloat()
        val sy = scrollY.toFloat()
        val w = width.toFloat()
        val h = height.toFloat()

        // Hard clip at fadeTop: guarantees content can NEVER be drawn above the fade boundary
        val saveCount = canvas.save()
        canvas.clipRect(sx, sy + fadeTop, sx + w, sy + h)

        // Translate the shader to match the current scroll position so the fade stays fixed on screen
        shaderMatrix.setTranslate(sx, sy)
        gradient.setLocalMatrix(shaderMatrix)

        val count = canvas.saveLayer(sx, sy, sx + w, sy + h, null)
        super.dispatchDraw(canvas)
        canvas.drawRect(sx, sy, sx + w, sy + h, maskPaint)
        canvas.restoreToCount(count)

        canvas.restoreToCount(saveCount)
    }
}
