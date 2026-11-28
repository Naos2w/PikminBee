package com.naos.pikminbee.data

enum class MapAppearance { SYSTEM, LIGHT, DARK }

/** The OS choice is the default; users can still lock light or dark manually. */
object MapAppearancePolicy {
    fun fromStored(value: String?): MapAppearance =
        MapAppearance.entries.firstOrNull { it.name == value } ?: MapAppearance.SYSTEM

    fun isDark(mode: MapAppearance, systemDark: Boolean): Boolean = when (mode) {
        MapAppearance.SYSTEM -> systemDark
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
    }
}
