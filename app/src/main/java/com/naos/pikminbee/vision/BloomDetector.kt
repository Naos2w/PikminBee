package com.naos.pikminbee.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Original, heuristic image-only bloom candidate detector.
 *
 * It does not access Pikmin Bloom APIs, simulate input, identify real-world coordinates,
 * recognise species, or claim a detected shape is definitely an in-game Big Flower.
 * Pixel colours and sizing MUST be calibrated using user-authorised UAT images.
 */
object BloomDetector {
    const val MAX_PIXELS = 600_000

    data class Frame(val width: Int, val height: Int, val argb: IntArray) {
        init {
            require(width in 16..2_048 && height in 16..2_048) { "畫面尺寸無效" }
            require(width.toLong() * height <= MAX_PIXELS) { "畫面過大，請先縮小" }
            require(argb.size == width * height) { "畫素數量與尺寸不符" }
        }
    }

    enum class Appearance { COLOURED, PALE_WITH_WARM_CENTRE }

    data class Hit(
        val centreX: Int,
        val centreY: Int,
        val width: Int,
        val height: Int,
        val pixelArea: Int,
        val boundsWidth: Int,
        val boundsHeight: Int,
        val score: Double,
        val appearance: Appearance,
    ) {
        val relativeX: Double get() = centreX.toDouble() / width
        val relativeY: Double get() = centreY.toDouble() / height
    }

    data class Result(
        val width: Int,
        val height: Int,
        val hits: List<Hit>,
        val mostlyBlank: Boolean,
        val note: String,
    )

    private const val UNKNOWN_VIEW_NOTE = "畫面候選僅供預覽：尚未有真實樣本驗證，不能自動換算 GPS 座標"
    private enum class Kind { NONE, COLOUR, WHITE, WARM }

    fun analyse(frame: Frame, limit: Int = 40): Result {
        require(limit in 1..100)
        val w = frame.width
        val h = frame.height
        val count = w * h
        val kind = ByteArray(count)
        var dark = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val at = y * w + x
                val pixel = frame.argb[at]
                val r = (pixel shr 16) and 255
                val g = (pixel shr 8) and 255
                val b = pixel and 255
                if (max(r, max(g, b)) < 30) dark++
                // Exclude UI/status/navigation areas to avoid obvious false positives.
                if (y < h * 0.09 || y > h * 0.86) continue
                kind[at] = classify(r, g, b).ordinal.toByte()
            }
        }
        val mostlyBlank = dark >= count * 0.93
        if (mostlyBlank) return Result(w, h, emptyList(), true,
            "畫面幾乎全黑：可能是空白、擷取尚未準備好或畫面受保護；不嘗試繞過")
        // Two independent masks: saturated coloured blooms and pale petals with warm core.
        val coloured = components(kind, w, h, false)
        val pale = components(kind, w, h, true)
        val deduped = ArrayList<Hit>()
        for (hit in (coloured + pale).sortedByDescending { it.score }) {
            if (deduped.size >= limit) break
            if (deduped.any {
                    abs(it.centreX - hit.centreX) <= max(it.boundsWidth, hit.boundsWidth) * 0.6 &&
                    abs(it.centreY - hit.centreY) <= max(it.boundsHeight, hit.boundsHeight) * 0.6
                }) continue
            deduped.add(hit)
        }
        return Result(w, h, deduped.sortedWith(compareBy({ it.centreY }, { it.centreX })),
            false, UNKNOWN_VIEW_NOTE)
    }

    private fun classify(r: Int, g: Int, b: Int): Kind {
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        if (maxC < 100) return Kind.NONE
        val saturation = if (maxC == 0) 0.0 else delta.toDouble() / maxC
        val hue = when {
            delta == 0 -> 0.0
            maxC == r -> ((g - b).toDouble() / delta * 60.0 + 360.0) % 360.0
            maxC == g -> (b - r).toDouble() / delta * 60.0 + 120.0
            else -> (r - g).toDouble() / delta * 60.0 + 240.0
        }
        if (maxC >= 204 && saturation <= 0.19) return Kind.WHITE
        if (maxC >= 130 && saturation >= 0.31 && hue in 24.0..68.0) return Kind.WARM
        if (maxC >= 135 && saturation >= 0.28 &&
            (hue < 24.0 || hue in 180.0..360.0 || hue in 68.0..78.0)) {
            return Kind.COLOUR
        }
        return Kind.NONE
    }

    private fun components(kind: ByteArray, w: Int, h: Int, pale: Boolean): List<Hit> {
        val n = kind.size
        val marked = BooleanArray(n)
        for (i in 0 until n) {
            val k = kind[i].toInt()
            marked[i] = if (pale) k == Kind.WHITE.ordinal || k == Kind.WARM.ordinal
                else k == Kind.COLOUR.ordinal || k == Kind.WARM.ordinal
        }
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        val result = ArrayList<Hit>()
        for (start in 0 until n) {
            if (!marked[start] || visited[start]) continue
            var head = 0
            var tail = 1
            stack[0] = start
            visited[start] = true
            var area = 0
            var whites = 0
            var warms = 0
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            var sumX = 0L
            var sumY = 0L
            var denseCore = false
            while (head < tail) {
                val i = stack[head++]
                val x = i % w
                val y = i / w
                area++
                sumX += x
                sumY += y
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                if (kind[i].toInt() == Kind.WHITE.ordinal) whites++
                if (kind[i].toInt() == Kind.WARM.ordinal) warms++
                // A connected seed with at least 4 touching pixels is required.
                var neighbours = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val index = yy * w + xx
                    if (!marked[index]) continue
                    neighbours++
                    if (!visited[index]) {
                        visited[index] = true
                        stack[tail++] = index
                    }
                }
                if (neighbours >= 5) denseCore = true
            }
            val widthPx = maxX - minX + 1
            val heightPx = maxY - minY + 1
            val bounds = widthPx * heightPx
            val fill = if (bounds == 0) 0.0 else area.toDouble() / bounds
            val aspect = max(widthPx, heightPx).toDouble() / max(1, min(widthPx, heightPx))
            val minArea = max(28, n / 12_000)
            val maxArea = max(650, n / 16)
            val basic = area in minArea..maxArea && widthPx >= 7 && heightPx >= 7 &&
                aspect <= 2.25 && fill >= 0.39 && denseCore
            val appearance = if (pale) Appearance.PALE_WITH_WARM_CENTRE else Appearance.COLOURED
            val paleValid = !pale || (whites.toDouble() / area >= 0.24 &&
                warms.toDouble() / area in 0.025..0.52)
            val colouredValid = pale || (whites.toDouble() / area < 0.65)
            if (basic && paleValid && colouredValid) {
                val scaledArea = (area.toDouble() / max(1, n)) * 1000
                val score = (0.18 + fill * 0.42 + min(0.30, scaledArea * 0.04) +
                    if (pale) 0.04 else 0.0).coerceIn(0.0, 0.95)
                result.add(Hit((sumX / area).toInt(), (sumY / area).toInt(), w, h,
                    area, widthPx, heightPx, score, appearance))
            }
        }
        return result
    }
}
