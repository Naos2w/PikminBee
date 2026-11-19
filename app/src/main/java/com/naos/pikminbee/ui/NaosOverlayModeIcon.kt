package com.naos.pikminbee.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** Same custom 24dp vector vocabulary as the Compose Map Dock, without emoji. */
internal class NaosOverlayModeIcon(
    private val mode: Int,
    private val color: Int,
    private val pixels: Int,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@NaosOverlayModeIcon.color
        strokeWidth = pixels / 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun getIntrinsicWidth() = pixels
    override fun getIntrinsicHeight() = pixels
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.strokeWidth = 1.9f
        paint.style = Paint.Style.STROKE
        when (mode) {
            0 -> {
                val path = Path().apply {
                    moveTo(3f, 11f); lineTo(22f, 2f); lineTo(13f, 21f)
                    lineTo(11f, 13f); close()
                }
                canvas.drawPath(path, paint)
            }
            1 -> {
                canvas.drawLine(5f, 7f, 5f, 15f, paint)
                canvas.drawLine(5f, 15f, 18f, 15f, paint)
                canvas.drawCircle(5f, 5f, 2.5f, paint)
                canvas.drawCircle(19f, 19f, 2.5f, paint)
            }
            2 -> {
                repeat(5) { index ->
                    val angle = 2.0 * Math.PI * index / 5
                    canvas.drawCircle((12 + 5*cos(angle)).toFloat(),
                        (12 + 5*sin(angle)).toFloat(), 2.7f, paint)
                }
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 12f, 2.1f, paint)
            }
            else -> {
                val path = Path().apply {
                    moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f)
                    lineTo(12f, 17f); lineTo(6f, 21f); close()
                }
                canvas.drawPath(path, paint)
            }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter; invalidateSelf()
    }
    @Deprecated("Use opacity of drawable", level = DeprecationLevel.WARNING)
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
