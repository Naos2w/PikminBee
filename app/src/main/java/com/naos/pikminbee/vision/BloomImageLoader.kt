package com.naos.pikminbee.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/** User-selected screenshots only; decode a bounded downsample without storing image files. */
object BloomImageLoader {
    fun fromDocument(open: () -> InputStream?): BloomDetector.Frame {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = open() ?: throw IllegalArgumentException("無法讀取圖片")
        header.use { BitmapFactory.decodeStream(it, null, bounds) }
        // inJustDecodeBounds returns null by design; dimensions are populated instead.
        if (bounds.outWidth !in 16..15_000 || bounds.outHeight !in 16..15_000)
            throw IllegalArgumentException("圖片尺寸不支援")
        var sample = 1
        while (bounds.outWidth / sample > 480 || bounds.outHeight / sample > 850) sample *= 2
        val bitmap = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: throw IllegalArgumentException("無法解碼圖片")
        return try {
            val scale = min(1.0, min(480.0 / bitmap.width, 850.0 / bitmap.height))
            val w = max(16, (bitmap.width * scale).toInt())
            val h = max(16, (bitmap.height * scale).toInt())
            val resized = if (w == bitmap.width && h == bitmap.height) bitmap
                else Bitmap.createScaledBitmap(bitmap, w, h, true)
            try {
                val pixels = IntArray(w * h)
                resized.getPixels(pixels, 0, w, 0, 0, w, h)
                BloomDetector.Frame(w, h, pixels)
            } finally {
                if (resized !== bitmap) resized.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
