package com.naos.pikminbee.domain

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * On-device estimates for the actual pedestrian road geometry returned by Valhalla.
 * This is the geometric length of the returned polyline, never the straight-line
 * distance between selected stops. Travel time excludes breaks and GPS latency.
 */
data class RouteEstimate(val distanceMetres: Double, val minutes: Long) {
    val distanceLabel: String
        get() = if (distanceMetres < 1_000.0) {
            "${distanceMetres.roundToInt()} 公尺"
        } else {
            String.format(Locale.TAIWAN, "%.2f 公里", distanceMetres / 1_000.0)
        }

    val durationLabel: String
        get() = when {
            minutes < 60 -> "${minutes} 分鐘"
            minutes % 60L == 0L -> "${minutes / 60} 小時"
            else -> "${minutes / 60} 小時 ${minutes % 60} 分鐘"
        }

    companion object {
        fun fromRoad(geometry: List<Coordinate>, speed: WalkSpeedPreset): RouteEstimate {
            require(geometry.size >= 2) { "A road estimate requires planned road geometry" }
            return fromDistance(WalkingGeometry.lengthMeters(geometry), speed)
        }

        fun fromDistance(distanceMetres: Double, speed: WalkSpeedPreset): RouteEstimate =
            fromDistanceAtMps(distanceMetres, speed.metresPerSecond)

        fun fromDistanceAtMps(distanceMetres: Double, speedMps: Double): RouteEstimate {
            require(distanceMetres.isFinite() && distanceMetres >= 0.0)
            require(speedMps.isFinite() && speedMps > 0.0)
            val minutes = if (distanceMetres == 0.0) 0L else
                // Avoid floating-point noise turning exactly 60 minutes into 61.
                ceil(distanceMetres / speedMps / 60.0 - 1e-9)
                    .toLong().coerceAtLeast(1)
            return RouteEstimate(distanceMetres, minutes)
        }
    }
}
