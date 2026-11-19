package com.naos.pikminbee.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Original, API-key-free ordered waypoints shared by MapLibre and Google Maps.
 * First point = green, middle = blue, destination = red, all keep their number.
 */
object RoutePinBitmap {
    enum class Role { START, VIA, END }

    fun role(index: Int, count: Int): Role {
        require(count > 0 && index in 0 until count)
        return when {
            index == 0 -> Role.START
            index == count - 1 -> Role.END
            else -> Role.VIA
        }
    }

    /**
     * Compact Google-Maps-like position dot, not a large tap target or halo.
     * Real GPS is blue; a mock GPS position is orange. Bee remains the overlay icon.
     * Total bitmap footprint is 24dp, coloured centre about 9dp.
     */
    fun current(context: Context, simulated: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (24f * density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size * .5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colour = if (simulated) Color.rgb(248, 144, 46)
            else Color.rgb(53, 125, 239)
        paint.color = if (simulated) Color.argb(26, 248, 144, 46)
            else Color.argb(26, 53, 125, 239)
        canvas.drawCircle(centre, centre, size * .45f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(centre, centre, size * .31f, paint)
        paint.color = colour
        canvas.drawCircle(centre, centre, size * .23f, paint)
        return bitmap
    }

    fun create(context: Context, index: Int, count: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (49f * density).toInt().coerceAtLeast(49)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val centre = size * 0.5f
        val radius = size * 0.31f
        val role = role(index, count)
        val colour = when (role) {
            Role.START -> Color.rgb(21, 158, 94)
            Role.VIA -> Color.rgb(48, 110, 238)
            Role.END -> Color.rgb(223, 69, 77)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 18, 45)
        }
        canvas.drawCircle(centre + size * .035f, centre + size * .065f,
            radius + 5f * density, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(centre, centre, radius + 3f * density, paint)
        paint.color = colour
        canvas.drawCircle(centre, centre, radius, paint)
        paint.apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = (if (index < 9) 19f else 15f) * density
        }
        val baseline = centre - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText((index + 1).toString(), centre, baseline, paint)
        return result
    }
}
