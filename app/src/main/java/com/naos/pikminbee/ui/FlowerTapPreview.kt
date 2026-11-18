package com.naos.pikminbee.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.naos.pikminbee.vision.FlowerPixelCalibration
import com.naos.pikminbee.vision.ImageTapMapper

/**
 * Draws the original user-picked (downsampled) image only in memory.
 * The selection is local; no touches on the game, no screenshot persistence.
 */
@Composable
fun FlowerTapPreview(
    previewImage: ImageBitmap,
    documentRevision: Long,
    anchors: List<FlowerPixelCalibration.Anchor>,
    pending: ImageTapMapper.Pixel?,
    onPixelSelected: (ImageTapMapper.Pixel) -> Unit,
) {
    var viewport by remember(documentRevision) { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier.fillMaxWidth().height(310.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF172A39))
            .onSizeChanged { viewport = it }
            .pointerInput(documentRevision, viewport) {
                detectTapGestures { tap ->
                    val fit = ImageTapMapper.fit(
                        previewImage.width, previewImage.height,
                        viewport.width.toFloat(), viewport.height.toFloat(),
                    )
                    val selected = fit?.toImagePixel(
                        tap.x, tap.y, previewImage.width, previewImage.height,
                    )
                    if (selected != null) onPixelSelected(selected)
                }
            },
    ) {
        Image(
            bitmap = previewImage,
            contentDescription = "已選擇的圖片，點一下地標即可填入像素座標",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val fit = ImageTapMapper.fit(
                previewImage.width, previewImage.height, size.width, size.height,
            ) ?: return@Canvas
            anchors.forEach { anchor ->
                val dot = fit.toViewportPixel(anchor.pixelX.toFloat(), anchor.pixelY.toFloat())
                drawCircle(
                    Color(0xFFFFC857), radius = 8.dp.toPx(),
                    center = Offset(dot.x, dot.y), style = Stroke(2.5.dp.toPx()),
                )
                drawCircle(
                    Color(0xFFFFC857).copy(alpha = 0.45f),
                    radius = 3.dp.toPx(), center = Offset(dot.x, dot.y),
                )
            }
            pending?.let { pixel ->
                val dot = fit.toViewportPixel(pixel.x, pixel.y)
                drawCircle(
                    Color(0xFF57E7FF), radius = 11.dp.toPx(),
                    center = Offset(dot.x, dot.y), style = Stroke(2.5.dp.toPx()),
                )
                drawCircle(
                    Color(0xFF57E7FF), radius = 3.dp.toPx(), center = Offset(dot.x, dot.y),
                )
            }
        }
    }
}
