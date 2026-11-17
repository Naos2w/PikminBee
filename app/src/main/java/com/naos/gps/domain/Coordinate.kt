package com.naos.gps.domain

data class Coordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    fun label(): String = "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude)

    companion object {
        fun parse(latitude: String, longitude: String): Coordinate? =
            runCatching { Coordinate(latitude.trim().toDouble(), longitude.trim().toDouble()) }.getOrNull()
    }
}
