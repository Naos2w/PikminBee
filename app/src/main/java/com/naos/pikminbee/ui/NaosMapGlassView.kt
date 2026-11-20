package com.naos.pikminbee.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.compose.ui.graphics.toArgb

/**
 * Glass over MapLibre/Google map: sample the latest native-map bitmap during
 * camera movement (bounded by the provider's capture gate) and again at idle.
 * AndroidView/SurfaceView backdrops cannot be sampled as Compose layers.
 *
 * Android 13+ uses AGSL with rounded-edge pixel displacement. Older devices,
 * missing snapshots, or invalid dimensions use the canonical safe frosted tint
 * with no pretend refraction. Native overlay windows over OTHER apps cannot
 * sample those apps' pixels and always use the frosted fallback.
 *
 * One bitmap is shared across the top bar and floating dock; this view owns
 * neither the bitmap nor its lifecycle. Never recapture every frame.
 */
internal class NaosMapGlassView(context: Context) : View(context) {
    var snapshot: Bitmap? = null
        set(value) { if (field !== value) { field = value; invalidate() } }
    var originX: Float = 0f
        set(value) { if (field != value) { field = value; invalidate() } }
    var originY: Float = 0f
        set(value) { if (field != value) { field = value; invalidate() } }
    var opticalBlend: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (field != next) { field = next; invalidate() }
        }
    var glassMode: NaosGlassMaterial = NaosGlassMaterial.REGULAR
        set(value) { if (field != value) { field = value; invalidate() } }
    var palette: NaosPalette = NaosDesignSystem.palette(NaosThemeFamily.MINERAL, false)
        set(value) { if (field != value) { field = value; invalidate() } }

    private var shaderCache: Shader? = null
    private var cachedBitmap: Bitmap? = null
    private var cachedX = Float.NaN
    private var cachedY = Float.NaN
    private var cachedWidth = -1
    private var cachedHeight = -1
    private var cachedMode: NaosGlassMaterial? = null
    private var cachedPalette: NaosPalette? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frame = RectF()
    private val radius get() = 25f * resources.displayMetrics.density

    @SuppressLint("NewApi")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val r = radius.coerceAtMost(minOf(width, height) / 2f)
        frame.set(0f, 0f, width.toFloat(), height.toFloat())
        val backdrop = snapshot
        val sourceReady = backdrop != null && !backdrop.isRecycled &&
            originX >= 0f && originY >= 0f &&
            originX + width <= backdrop.width + 1 &&
            originY + height <= backdrop.height + 1
        val canSample = Build.VERSION.SDK_INT >= 33 && sourceReady && opticalBlend > 0f
        val activeShader = if (canSample) {
            if (cachedBitmap !== backdrop || cachedX != originX || cachedY != originY ||
                cachedWidth != width || cachedHeight != height ||
                cachedMode != glassMode || cachedPalette != palette) {
                shaderCache = runCatching { makeOpticalShader(backdrop!!, r) }.getOrNull()
                cachedBitmap = backdrop
                cachedX = originX
                cachedY = originY
                cachedWidth = width
                cachedHeight = height
                cachedMode = glassMode
                cachedPalette = palette
            }
            shaderCache
        } else {
            shaderCache = null
            cachedBitmap = null
            null
        }
        // Consistent translucent mineral backing under both live and idle
        // snapshot refraction, and during any provider capture stall.
        val dark = palette.ink.red > .6f
        val topTint = palette.surface.copy(alpha = if (dark) .76f else .69f).toArgb()
        val bottomTint = palette.soft.copy(alpha = if (dark) .64f else .62f).toArgb()
        paint.shader = LinearGradient(
            0f, 0f, width * .45f, height.toFloat(),
            topTint, bottomTint, Shader.TileMode.CLAMP,
        )
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(frame, r, r, paint)
        paint.shader = null
        if (activeShader != null) {
            paint.shader = activeShader
            paint.color = android.graphics.Color.WHITE
            paint.alpha = (255f * opticalBlend).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(frame, r, r, paint)
            paint.alpha = 255
            paint.shader = null
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.25f * resources.displayMetrics.density
        paint.color = android.graphics.Color.argb(
            if (dark) 110 else 220, 255, 255, 255,
        )
        canvas.drawRoundRect(frame, r, r, paint)
        paint.strokeWidth = .8f * resources.displayMetrics.density
        paint.color = palette.stroke.copy(alpha = if (dark) .58f else .42f).toArgb()
        canvas.drawRoundRect(
            RectF(frame.left + paint.strokeWidth, frame.top + paint.strokeWidth,
                frame.right - paint.strokeWidth, frame.bottom - paint.strokeWidth),
            r, r, paint,
        )
        paint.style = Paint.Style.FILL
    }

    @androidx.annotation.RequiresApi(33)
    private fun makeOpticalShader(bitmap: Bitmap, r: Float): Shader {
        val agsl = RuntimeShader(OPTICAL_SHADER)
        agsl.setInputShader("backdrop", BitmapShader(bitmap,
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        agsl.setFloatUniform("origin", originX, originY)
        agsl.setFloatUniform("extent", width.toFloat(), height.toFloat())
        agsl.setFloatUniform("corner", r)
        agsl.setFloatUniform("displacement", if (glassMode == NaosGlassMaterial.BLUR)
            0f else NaosDesignSystem.GLASS_REFRACTION_PX * resources.displayMetrics.density)
        agsl.setFloatUniform("blur", NaosDesignSystem.GLASS_BLUR_PX * resources.displayMetrics.density)
        agsl.setFloatUniform("rawMix", when (glassMode) {
            NaosGlassMaterial.REGULAR -> 0.15f
            NaosGlassMaterial.CLEAR -> 0.76f
            NaosGlassMaterial.BLUR -> 0.02f
        })
        agsl.setFloatUniform("tintColor",
            palette.surface.red, palette.surface.green, palette.surface.blue)
        agsl.setFloatUniform("tintStrength", if (glassMode == NaosGlassMaterial.BLUR)
            .32f else NaosDesignSystem.GLASS_TINT +
                if (glassMode == NaosGlassMaterial.REGULAR) .12f else 0f)
        return agsl
    }

    private companion object {
        // Pixel displacement of source bitmap near the GLASS BOUNDARY.
        // A line behind the rounded edge bends; this cannot be reproduced by
        // colour/opacity/border-only imitation.
        val OPTICAL_SHADER = """
            uniform shader backdrop;
            uniform float2 origin;
            uniform float2 extent;
            uniform float corner;
            uniform float displacement;
            uniform float blur;
            uniform float rawMix;
            uniform float3 tintColor;
            uniform float tintStrength;
            float distanceField(float2 pt) {
                float2 q = abs(pt - extent * 0.5) - (extent * 0.5 - float2(corner));
                return length(max(q,float2(0.0))) + min(max(q.x,q.y),0.0) - corner;
            }
            half4 main(float2 pixel) {
                float inside = max(0.0, -distanceField(pixel));
                float edge = pow(clamp(1.0 - inside / 24.0, 0.0, 1.0), 1.7);
                float2 gradient = float2(
                    distanceField(pixel + float2(1.0,0.0)) - distanceField(pixel - float2(1.0,0.0)),
                    distanceField(pixel + float2(0.0,1.0)) - distanceField(pixel - float2(0.0,1.0)));
                float2 normal = gradient / max(length(gradient), 0.01);
                float2 uv = origin + pixel + normal * displacement * edge;
                half3 raw = backdrop.eval(uv).rgb;
                float sampleRadius = blur * 0.50;
                half3 soft = (backdrop.eval(uv).rgb +
                    backdrop.eval(uv+float2(sampleRadius,0.0)).rgb +
                    backdrop.eval(uv-float2(sampleRadius,0.0)).rgb +
                    backdrop.eval(uv+float2(0.0,sampleRadius)).rgb +
                    backdrop.eval(uv-float2(0.0,sampleRadius)).rgb) / 5.0;
                half3 mixedPixels = mix(soft, raw, half(rawMix + edge * (1.0-rawMix)*0.55));
                half3 coloured = mix(mixedPixels, half3(tintColor), half(tintStrength));
                half lift = half(pow(edge,3.0) * 0.10);
                return half4(coloured + (1.0-coloured)*lift, 1.0);
            }
        """.trimIndent()
    }
}
