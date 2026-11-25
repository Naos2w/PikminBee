package com.naos.pikminbee.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** Single low-contrast, vector-only icon language for the floating toolbar. */
internal enum class OverlayGlyph {
    BEE, BEE_PAUSED, PLAY, PAUSE, RESUME, STOP, REAL, VIRTUAL, FLOWER, SPEED, MAP, CLOSE, MORE,
    TELEPORT, ROUTE, BOOKMARK, WIFI, COPY, POWER, BACK, HELP
}

internal class NaosOverlayActionIcon(
    private val glyph: OverlayGlyph,
    private val ink: Int,
    private val pixels: Int,
) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    override fun getIntrinsicWidth() = pixels
    override fun getIntrinsicHeight() = pixels
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        pen.style = Paint.Style.STROKE
        when (glyph) {
            OverlayGlyph.PLAY, OverlayGlyph.RESUME -> {
                val path = Path().apply {
                    moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
                }
                canvas.drawPath(path, pen)
            }
            OverlayGlyph.PAUSE -> {
                canvas.drawLine(8f, 5f, 8f, 19f, pen)
                canvas.drawLine(16f, 5f, 16f, 19f, pen)
            }
            OverlayGlyph.STOP -> canvas.drawRoundRect(5f, 5f, 19f, 19f, 2f, 2f, pen)
            OverlayGlyph.REAL -> {
                canvas.drawCircle(12f, 12f, 6.5f, pen)
                canvas.drawCircle(12f, 12f, 2f, pen)
                canvas.drawLine(12f, 1f, 12f, 4f, pen)
                canvas.drawLine(12f, 20f, 12f, 23f, pen)
                canvas.drawLine(1f, 12f, 4f, 12f, pen)
                canvas.drawLine(20f, 12f, 23f, 12f, pen)
            }
            OverlayGlyph.VIRTUAL -> {
                val path = Path().apply {
                    moveTo(12f, 22f)
                    cubicTo(10f, 18f, 4.5f, 14.5f, 4.5f, 10f)
                    cubicTo(4.5f, 0f, 19.5f, 0f, 19.5f, 10f)
                    cubicTo(19.5f, 14.5f, 14f, 18f, 12f, 22f)
                }
                canvas.drawPath(path, pen)
                canvas.drawCircle(12f, 9.5f, 2.9f, pen)
            }
            OverlayGlyph.FLOWER -> {
                repeat(5) { index ->
                    val angle = Math.PI * 2 * index / 5 - Math.PI / 2
                    canvas.drawCircle(
                        (12 + 5.1 * cos(angle)).toFloat(),
                        (12 + 5.1 * sin(angle)).toFloat(), 3f, pen
                    )
                }
                pen.style = Paint.Style.FILL
                canvas.drawCircle(12f, 12f, 2f, pen)
            }
            OverlayGlyph.SPEED -> {
                canvas.drawArc(3f, 4f, 21f, 22f, 195f, 150f, false, pen)
                canvas.drawLine(12f, 17f, 17f, 10f, pen)
                canvas.drawCircle(12f, 17f, 2f, pen)
            }
            OverlayGlyph.TELEPORT -> {
                val path = Path().apply {
                    moveTo(3f, 11f); lineTo(22f, 2f)
                    lineTo(13f, 21f); lineTo(11f, 13f); close()
                }
                canvas.drawPath(path, pen)
            }
            OverlayGlyph.ROUTE -> {
                canvas.drawLine(5f, 7f, 5f, 15f, pen)
                canvas.drawLine(5f, 15f, 18f, 15f, pen)
                canvas.drawCircle(5f, 5f, 2.5f, pen)
                canvas.drawCircle(19f, 19f, 2.5f, pen)
            }
            OverlayGlyph.BOOKMARK -> {
                val path = Path().apply {
                    moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f)
                    lineTo(12f, 17f); lineTo(6f, 21f); close()
                }
                canvas.drawPath(path, pen)
            }
            OverlayGlyph.MAP -> {
                val path = Path().apply {
                    moveTo(2.5f, 5f); lineTo(9f, 3f); lineTo(15f, 5f)
                    lineTo(21.5f, 3f); lineTo(21.5f, 19f)
                    lineTo(15f, 21f); lineTo(9f, 19f)
                    lineTo(2.5f, 21f); close()
                }
                canvas.drawPath(path, pen)
                canvas.drawLine(9f, 3f, 9f, 19f, pen)
                canvas.drawLine(15f, 5f, 15f, 21f, pen)
            }
            OverlayGlyph.WIFI -> {
                canvas.drawArc(-1f, 3f, 25f, 29f, 225f, 90f, false, pen)
                canvas.drawArc(4f, 8f, 20f, 24f, 225f, 90f, false, pen)
                canvas.drawCircle(12f, 17f, 1.4f, pen)
            }
            OverlayGlyph.COPY -> {
                canvas.drawRoundRect(8f, 3f, 21f, 16f, 2f, 2f, pen)
                canvas.drawRoundRect(3f, 8f, 16f, 21f, 2f, 2f, pen)
            }
            OverlayGlyph.POWER -> {
                canvas.drawArc(3.5f, 4.5f, 20.5f, 21.5f, -48f, 276f, false, pen)
                canvas.drawLine(12f, 2f, 12f, 11f, pen)
            }
            OverlayGlyph.BACK -> {
                canvas.drawLine(15f, 5f, 8f, 12f, pen)
                canvas.drawLine(8f, 12f, 15f, 19f, pen)
            }
            OverlayGlyph.HELP -> {
                canvas.drawCircle(12f, 12f, 9f, pen)
                canvas.drawArc(9f, 6.5f, 15f, 12.5f, 180f, 220f, false, pen)
                canvas.drawLine(14f, 11.5f, 12f, 14f, pen)
                canvas.drawCircle(12f, 17f, .6f, pen)
            }
            OverlayGlyph.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, pen)
                canvas.drawLine(18f, 6f, 6f, 18f, pen)
            }
            OverlayGlyph.MORE -> {
                val p = Path().apply {
                    moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f)
                }
                canvas.drawPath(p, pen)
            }
            OverlayGlyph.BEE, OverlayGlyph.BEE_PAUSED -> {
                // Preserve the original bee, but make the two wings lower and
                // wider so they read as wings rather than round bear ears.
                // All paths use the same 24-unit grid and rounded stroke.
                canvas.drawOval(2.8f, 7.6f, 10.6f, 13f, pen)
                canvas.drawOval(13.4f, 7.6f, 21.2f, 13f, pen)
                canvas.drawOval(7.2f, 9.8f, 16.8f, 21.4f, pen)
                canvas.drawLine(8.0f, 14.1f, 16f, 14.1f, pen)
                canvas.drawLine(8.1f, 17.5f, 15.9f, 17.5f, pen)
                canvas.drawLine(10.2f, 10.0f, 8.8f, 5.5f, pen)
                canvas.drawLine(13.8f, 10.0f, 15.2f, 5.5f, pen)
                if (glyph == OverlayGlyph.BEE_PAUSED) {
                    // Integrated pause mark; no surrounding button or badge.
                    canvas.drawLine(18f, 16f, 18f, 22f, pen)
                    canvas.drawLine(21.5f, 16f, 21.5f, 22f, pen)
                }
            }
        }
        canvas.restoreToCount(saved)
    }

    override fun setAlpha(alpha: Int) { pen.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        pen.colorFilter = colorFilter
        invalidateSelf()
    }
    @Deprecated("Drawable opacity", level = DeprecationLevel.WARNING)
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
