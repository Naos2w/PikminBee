package com.naos.pikminbee.domain

/** UI speeds are km/h; the GPS engine and Valhalla geometry use metres/second. */
enum class WalkSpeedPreset(val label: String, val kilometresPerHour: Double) {
    SLOW("慢走", 0.8),
    WALK("走路", 1.4),
    FAST("快走", 10.8);

    val metresPerSecond: Double get() = kilometresPerHour / 3.6
    val display: String get() = "$label · $kilometresPerHour km/h"

    companion object {
        val DEFAULT: WalkSpeedPreset = WALK
        fun fromSaved(value: String?): WalkSpeedPreset =
            entries.firstOrNull { it.name == value } ?: DEFAULT

        /** Existing installs migrate their previously saved last-selected speed. */
        fun fromSavedDefault(explicitDefault: String?, oldSelection: String?): WalkSpeedPreset =
            fromSaved(explicitDefault ?: oldSelection)
    }
}
