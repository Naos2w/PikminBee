package com.naos.pikminbee.vision

import kotlin.math.min

/**
 * Converts taps on a ContentScale.Fit, centre-aligned image into image pixels.
 * Taps in letterboxed margins must not create misleading calibration anchors.
 */
object ImageTapMapper {
    data class Pixel(val x: Float, val y: Float)
    data class Fit(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val scale: Float,
    ) {
        fun toImagePixel(x: Float, y: Float, imageWidth: Int, imageHeight: Int): Pixel? {
            if (imageWidth <= 0 || imageHeight <= 0 || scale <= 0f ||
                !x.isFinite() || !y.isFinite() ||
                x < left || y < top || x > left + width || y > top + height
            ) return null
            return Pixel(
                ((x - left) / scale).coerceIn(0f, (imageWidth - 1).toFloat()),
                ((y - top) / scale).coerceIn(0f, (imageHeight - 1).toFloat()),
            )
        }

        fun toViewportPixel(x: Float, y: Float): Pixel =
            Pixel(left + x * scale, top + y * scale)
    }

    fun fit(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): Fit? {
        if (imageWidth <= 0 || imageHeight <= 0 || !viewWidth.isFinite() ||
            !viewHeight.isFinite() || viewWidth <= 0f || viewHeight <= 0f
        ) return null
        val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
        if (!scale.isFinite() || scale <= 0f) return null
        val displayWidth = imageWidth * scale
        val displayHeight = imageHeight * scale
        return Fit(
            left = (viewWidth - displayWidth) / 2f,
            top = (viewHeight - displayHeight) / 2f,
            width = displayWidth,
            height = displayHeight,
            scale = scale,
        )
    }
}
