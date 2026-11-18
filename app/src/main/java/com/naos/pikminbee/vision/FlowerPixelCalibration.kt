package com.naos.pikminbee.vision

import com.naos.pikminbee.domain.Coordinate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Estimates a local map pixel -> geographic transform from points that the user
 * explicitly matched on the SAME still image. Does not inspect game data,
 * choose anchors, persist coordinates, or insert mock GPS route waypoints.
 *
 * Error scores describe agreement between user-supplied anchors only; they
 * are NOT independently measured Big Flower position accuracy.
 */
object FlowerPixelCalibration {
    private const val METRES_PER_DEGREE = 111_319.49079327357
    private const val MAX_ANCHORS = 16

    data class Anchor(val pixelX: Double, val pixelY: Double, val coordinate: Coordinate)
    data class Preview(val coordinate: Coordinate, val insideAnchorHull: Boolean)

    class Calibration internal constructor(
        val width: Int,
        val height: Int,
        val anchorCount: Int,
        val trainingRmseMetres: Double,
        val heldOutRmseMetres: Double,
        val worstHeldOutErrorMetres: Double,
        private val referenceLatitude: Double,
        private val referenceLongitude: Double,
        private val cosLatitude: Double,
        private val transform: Transform,
        private val anchorHull: List<Pair<Double, Double>>,
    ) {
        /**
         * Preview only. Out-of-hull estimates are extrapolations and must not be
         * treated as trusted GPS waypoints, regardless of anchor error.
         */
        fun preview(pixelX: Double, pixelY: Double): Preview? {
            if (!pixelX.isFinite() || !pixelY.isFinite() ||
                pixelX !in 0.0..(width - 1).toDouble() ||
                pixelY !in 0.0..(height - 1).toDouble()) return null
            val metres = transform.map(pixelX, pixelY)
            val latitude = referenceLatitude + metres.second / METRES_PER_DEGREE
            val longitude = referenceLongitude +
                metres.first / (METRES_PER_DEGREE * cosLatitude)
            val gps = runCatching { Coordinate(latitude, longitude) }.getOrNull() ?: return null
            return Preview(gps, insideHull(anchorHull, pixelX, pixelY))
        }
    }

    internal data class Transform(
        val centreX: Double,
        val centreY: Double,
        val centreEast: Double,
        val centreNorth: Double,
        val scaleCos: Double,
        val scaleSin: Double,
    ) {
        fun map(x: Double, y: Double): Pair<Double, Double> {
            val dx = x - centreX
            // Screen y grows downwards; geographic north grows upwards.
            val dy = centreY - y
            return Pair(
                centreEast + scaleCos * dx - scaleSin * dy,
                centreNorth + scaleSin * dx + scaleCos * dy,
            )
        }
    }

    fun fit(width: Int, height: Int, anchors: List<Anchor>): Calibration {
        require(width in 16..2048 && height in 16..2048 &&
            width.toLong() * height <= BloomDetector.MAX_PIXELS) {
            "校正必須使用與畫面辨識一致的有界圖片尺寸"
        }
        require(anchors.size in 3..MAX_ANCHORS) { "請提供 3 至 16 個不同的地圖對應點" }
        require(anchors.all {
            it.pixelX.isFinite() && it.pixelY.isFinite() &&
                it.pixelX in 0.0..(width - 1).toDouble() &&
                it.pixelY in 0.0..(height - 1).toDouble() &&
                abs(it.coordinate.latitude) <= 85.0
        }) { "對應點超出影像範圍，或接近無法可靠換算的極區" }

        val refLat = anchors.map { it.coordinate.latitude }.average()
        val refLon = anchors.map { it.coordinate.longitude }.average()
        val cosLat = cos(Math.toRadians(refLat))
        require(cosLat > 0.08) { "緯度無法用局部平面安全換算" }
        require(anchors.maxOf { it.coordinate.longitude } -
            anchors.minOf { it.coordinate.longitude } < 0.3) {
            "對應點經度跨度過大或跨越國際換日線"
        }
        val metres = anchors.map {
            Pair(
                (it.coordinate.longitude - refLon) * METRES_PER_DEGREE * cosLat,
                (it.coordinate.latitude - refLat) * METRES_PER_DEGREE,
            )
        }
        require(metres.all { hypot(it.first, it.second) < 20_000.0 }) {
            "對應點範圍超過局部地圖校正限制"
        }

        val centreX = anchors.map { it.pixelX }.average()
        val centreY = anchors.map { it.pixelY }.average()
        val xx = anchors.sumOf { (it.pixelX - centreX).pow(2) }
        val yy = anchors.sumOf { (it.pixelY - centreY).pow(2) }
        val xy = anchors.sumOf { (it.pixelX - centreX) * (it.pixelY - centreY) }
        val trace = xx + yy
        require(trace >= 200 && (xx * yy - xy * xy) / trace.pow(2) >= 0.001) {
            "對應點太接近或幾乎共線，請改選分散在畫面各處的點"
        }

        val fitted = fitTransform(anchors, metres)
        val fullErrors = anchors.indices.map { i ->
            val predicted = fitted.map(anchors[i].pixelX, anchors[i].pixelY)
            hypot(predicted.first - metres[i].first, predicted.second - metres[i].second)
        }
        // Leave-one-out: each point is predicted by a model trained without it.
        // This is still only anchor consistency, not actual game-map accuracy.
        val heldOutErrors = anchors.indices.map { heldOut ->
            val indexes = anchors.indices.filter { it != heldOut }
            val subset = fitTransform(indexes.map(anchors::get), indexes.map(metres::get))
            val predicted = subset.map(anchors[heldOut].pixelX, anchors[heldOut].pixelY)
            hypot(
                predicted.first - metres[heldOut].first,
                predicted.second - metres[heldOut].second,
            )
        }
        return Calibration(
            width = width,
            height = height,
            anchorCount = anchors.size,
            trainingRmseMetres = rmse(fullErrors),
            heldOutRmseMetres = rmse(heldOutErrors),
            worstHeldOutErrorMetres = heldOutErrors.maxOrNull() ?: 0.0,
            referenceLatitude = refLat,
            referenceLongitude = refLon,
            cosLatitude = cosLat,
            transform = fitted,
            anchorHull = convexHull(anchors.map { it.pixelX to it.pixelY }),
        )
    }

    private fun fitTransform(
        anchors: List<Anchor>,
        worldMetres: List<Pair<Double, Double>>,
    ): Transform {
        require(anchors.size >= 2 && anchors.size == worldMetres.size)
        val mx = anchors.map { it.pixelX }.average()
        val my = anchors.map { it.pixelY }.average()
        val me = worldMetres.map { it.first }.average()
        val mn = worldMetres.map { it.second }.average()
        var denominator = 0.0
        var dot = 0.0
        var cross = 0.0
        anchors.forEachIndexed { i, anchor ->
            val dx = anchor.pixelX - mx
            val dy = my - anchor.pixelY
            val east = worldMetres[i].first - me
            val north = worldMetres[i].second - mn
            denominator += dx * dx + dy * dy
            dot += dx * east + dy * north
            cross += dx * north - dy * east
        }
        require(denominator >= 10.0) { "至少兩個對應點太接近" }
        val a = dot / denominator
        val b = cross / denominator
        require(hypot(a, b) in 0.01..300.0) {
            "推算出的地圖比例尺不合理，請檢查經緯度和像素座標"
        }
        return Transform(mx, my, me, mn, a, b)
    }

    private fun rmse(errors: List<Double>): Double =
        sqrt(errors.sumOf { it * it } / errors.size)

    private fun cross(
        o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>,
    ): Double =
        (a.first - o.first) * (b.second - o.second) -
            (a.second - o.second) * (b.first - o.first)

    private fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        val lower = mutableListOf<Pair<Double, Double>>()
        for (p in sorted) {
            while (lower.size >= 2 &&
                cross(lower[lower.size - 2], lower.last(), p) <= 0.0) lower.removeAt(lower.lastIndex)
            lower.add(p)
        }
        val upper = mutableListOf<Pair<Double, Double>>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 &&
                cross(upper[upper.size - 2], upper.last(), p) <= 0.0) upper.removeAt(upper.lastIndex)
            upper.add(p)
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun insideHull(hull: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        if (hull.size < 3) return false
        val point = x to y
        return hull.indices.all { i ->
            cross(hull[i], hull[(i + 1) % hull.size], point) >= -0.00001
        }
    }
}
