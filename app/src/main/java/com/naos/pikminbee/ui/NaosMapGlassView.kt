package com.naos.pikminbee.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
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
                shaderCache = runCatching { makeOpticalShader(backdrop!!, r) }
                    .onFailure { Log.w("PikminBeeGlass", "AGSL refraction unavailable", it) }
                    .getOrNull()
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
        val topTint = palette.surface.copy(alpha = if (dark) .78f else .71f).toArgb()
        val bottomTint = palette.soft.copy(alpha = if (dark) .68f else .64f).toArgb()
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
        // A stroke centred on the view bounds loses half its pixels to clipping.
        // Put the highlight AND the secondary dark bevel fully inside the glass.
        val density = resources.displayMetrics.density
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.argb(if (dark) 224 else 248, 255, 255, 255),
                Color.argb(if (dark) 88 else 136, 255, 255, 255),
                palette.stroke.copy(alpha = if (dark) .78f else .73f).toArgb(),
            ), null, Shader.TileMode.CLAMP)
        val rimInset = paint.strokeWidth / 2f + 0.6f * density
        canvas.drawRoundRect(RectF(rimInset, rimInset,
            width - rimInset, height - rimInset),
            (r - rimInset).coerceAtLeast(0f), (r - rimInset).coerceAtLeast(0f), paint)
        paint.shader = null
        paint.strokeWidth = .85f * density
        paint.color = palette.ink.copy(alpha = if (dark) .23f else .27f).toArgb()
        val innerInset = 3f * density
        canvas.drawRoundRect(RectF(innerInset, innerInset,
            width - innerInset, height - innerInset),
            (r - innerInset).coerceAtLeast(0f), (r - innerInset).coerceAtLeast(0f), paint)
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
        agsl.setFloatUniform("bevelWidth", 16f * resources.displayMetrics.density)
        agsl.setFloatUniform("dispersion", if (glassMode == NaosGlassMaterial.BLUR)
            0f else .65f * resources.displayMetrics.density)
        agsl.setFloatUniform("blur", NaosDesignSystem.GLASS_BLUR_PX * resources.displayMetrics.density)
        agsl.setFloatUniform("rawMix", when (glassMode) {
            NaosGlassMaterial.REGULAR -> 0.24f
            NaosGlassMaterial.CLEAR -> 0.76f
            NaosGlassMaterial.BLUR -> 0.02f
        })
        agsl.setFloatUniform("tintColor",
            palette.surface.red, palette.surface.green, palette.surface.blue)
        agsl.setFloatUniform("tintStrength", if (glassMode == NaosGlassMaterial.BLUR)
            .32f else NaosDesignSystem.GLASS_TINT +
                if (glassMode == NaosGlassMaterial.REGULAR) .07f else 0f)
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
            uniform float bevelWidth;
            uniform float dispersion;
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
                float edge = pow(clamp(1.0 - inside / bevelWidth, 0.0, 1.0), 1.7);
                float2 gradient = float2(
                    distanceField(pixel + float2(1.0,0.0)) - distanceField(pixel - float2(1.0,0.0)),
                    distanceField(pixel + float2(0.0,1.0)) - distanceField(pixel - float2(0.0,1.0)));
                float2 normal = gradient / max(length(gradient), 0.01);
                float2 uv = origin + pixel + normal * displacement * edge;
                // Subtle RGB separation reinforces a genuine thick-lens edge.
                half3 raw = half3(
                    backdrop.eval(uv + normal * dispersion * edge).r,
                    backdrop.eval(uv).g,
                    backdrop.eval(uv - normal * dispersion * edge).b);
                float sampleRadius = blur * 0.50;
                half3 soft = (backdrop.eval(uv).rgb +
                    backdrop.eval(uv+float2(sampleRadius,0.0)).rgb +
                    backdrop.eval(uv-float2(sampleRadius,0.0)).rgb +
                    backdrop.eval(uv+float2(0.0,sampleRadius)).rgb +
                    backdrop.eval(uv-float2(0.0,sampleRadius)).rgb) / 5.0;
                half3 mixedPixels = mix(soft, raw, half(rawMix + edge * (1.0-rawMix)*0.55));
                half3 coloured = mix(mixedPixels, half3(tintColor), half(tintStrength));
                half lift = half(pow(edge, 2.0) *
                    (0.07 + 0.13 * clamp(-normal.y * 0.5 + 0.5, 0.0, 1.0)));
                return half4(coloured + (1.0-coloured)*lift, 1.0);
            }
        """.trimIndent()
    }
}
