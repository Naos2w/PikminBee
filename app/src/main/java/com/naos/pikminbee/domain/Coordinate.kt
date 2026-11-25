package com.naos.pikminbee.domain

import kotlin.math.abs

data class Coordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    fun label(): String = "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude)

    companion object {
        private const val UNSIGNED = "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"
        private val decimal = Regex("[+-]?$UNSIGNED")
        private val ddPair = Regex("([+-]?$UNSIGNED)(?:\\s*[,，]\\s*|\\s+)([+-]?$UNSIGNED)")
        private const val ANGULAR = "([+-]?[0-9]+)\\s*°\\s*($UNSIGNED)\\s*'\\s*(?:($UNSIGNED)\\s*\"\\s*)?([NSEW])"
        private val angular = Regex(ANGULAR, RegexOption.IGNORE_CASE)
        private val angularPair = Regex("$ANGULAR\\s*(?:[,，]\\s*)?$ANGULAR", RegexOption.IGNORE_CASE)

        private fun normalise(text: String): String = text
            .replace("′", "'").replace("’", "'").replace("‘", "'").replace("＇", "'")
            .replace("″", "\"").replace("“", "\"").replace("”", "\"").replace("＂", "\"")
            .replace("−", "-").replace(Regex("[\\p{Z}\\s]+"), " ").trim()

        private fun angularValue(groups: List<String>, offset: Int): Pair<Boolean, Double>? {
            val degreeText = groups[offset]
            val degrees = degreeText.toDoubleOrNull() ?: return null
            val minuteText = groups[offset + 1]
            val minutes = minuteText.toDoubleOrNull() ?: return null
            val secondText = groups[offset + 2]
            val seconds = if (secondText.isEmpty()) 0.0 else secondText.toDoubleOrNull() ?: return null
            val direction = groups[offset + 3].uppercase(java.util.Locale.ROOT).single()
            val latitude = direction in "NS"
            val negative = direction in "SW"
            if ((degreeText.startsWith("-") && !negative) ||
                (degreeText.startsWith("+") && negative)) return null
            // Fractional minutes are DDM only, never combined with seconds.
            if (secondText.isNotEmpty() && '.' in minuteText) return null
            if (!degrees.isFinite() || !minutes.isFinite() || !seconds.isFinite() ||
                minutes < 0.0 || minutes >= 60.0 || seconds < 0.0 || seconds >= 60.0) return null
            val limit = if (latitude) 90.0 else 180.0
            val whole = abs(degrees)
            if (whole > limit || (whole == limit && (minutes > 0.0 || seconds > 0.0))) return null
            val magnitude = whole + minutes / 60.0 + seconds / 3600.0
            if (!magnitude.isFinite() || magnitude > limit) return null
            val value = if (magnitude == 0.0) 0.0 else if (negative) -magnitude else magnitude
            return latitude to value
        }

        private fun parseAxis(text: String, latitude: Boolean): Double? {
            val value = normalise(text)
            if (decimal.matches(value)) return value.toDoubleOrNull()?.let { if (it == 0.0) 0.0 else it }
            val match = angular.matchEntire(value) ?: return null
            val parsed = angularValue(match.groupValues, 1) ?: return null
            return parsed.second.takeIf { parsed.first == latitude }
        }

        fun parse(latitude: String, longitude: String): Coordinate? {
            val lat = parseAxis(latitude, true) ?: return null
            val lon = parseAxis(longitude, false) ?: return null
            return runCatching { Coordinate(lat, lon) }.getOrNull()
        }

        /** DD is latitude first; DMS/DDM must declare both axes. Never geocode free text. */
        fun parseCombined(text: String): Coordinate? {
            val value = normalise(text)
            ddPair.matchEntire(value)?.let { return parse(it.groupValues[1], it.groupValues[2]) }
            val match = angularPair.matchEntire(value) ?: return null
            val first = angularValue(match.groupValues, 1) ?: return null
            val second = angularValue(match.groupValues, 5) ?: return null
            if (first.first == second.first) return null
            return if (first.first) Coordinate(first.second, second.second)
                else Coordinate(second.second, first.second)
        }
    }
}
