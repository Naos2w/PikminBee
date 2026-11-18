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

    /** Blue location dot for phone fixes; bee-marked amber dot for active mock GPS. */
    fun current(context: Context, simulated: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (58f * density).toInt().coerceAtLeast(58)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(38, 44, 122, 245)
        canvas.drawCircle(centre, centre, size * .46f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(centre, centre, size * .29f, paint)
        paint.color = if (simulated) Color.rgb(255, 197, 53) else Color.rgb(53, 123, 244)
        canvas.drawCircle(centre, centre, size * .235f, paint)
        if (simulated) {
            paint.color = Color.rgb(21, 46, 86)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 21f * density
            canvas.drawText("🐝", centre, centre - (paint.ascent() + paint.descent()) / 2f, paint)
        }
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
