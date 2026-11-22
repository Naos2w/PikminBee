package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkingGeometry

data class PlaceSuggestion(
    val displayName: String,
    val countryCode: String? = null,
    val countryName: String? = null,
    val city: String? = null,
    val timeZoneId: String? = null,
)

data class GeocodeCandidate(
    val name: String?,
    val coordinate: Coordinate,
    val osmKey: String?,
    val street: String?,
    val city: String?,
    val district: String?,
    val countryCode: String?,
    val countryName: String?,
)

/**
 * Prefer only genuinely nearby landmarks/shops; never call a POI miles away the
 * user's selected point. Reverse geocoding returns suggestions, NOT definitive labels.
 */
object PlaceSuggestionPolicy {
    private val poiTags = setOf(
        "amenity", "shop", "tourism", "historic", "leisure", "railway",
        "aeroway", "building", "craft", "office", "man_made",
    )

    fun nameFor(point: Coordinate, candidates: List<GeocodeCandidate>): PlaceSuggestion {
        val candidatesByDistance = candidates.map {
            it to WalkingGeometry.distanceMeters(point, it.coordinate)
        }.sortedBy { it.second }
        val context = candidatesByDistance.firstOrNull { it.second <= 2_000.0 }?.first
        val place = candidatesByDistance.firstOrNull { (item, distance) ->
            item.osmKey in poiTags && !item.name.isNullOrBlank() && distance <= 100.0
        }?.first
        val street = candidatesByDistance.firstOrNull { (item, distance) ->
            !item.street.isNullOrBlank() && distance <= 300.0
        }?.first?.street
        val district = context?.district
        val city = context?.city
        val proposed = place?.name ?: street ?: district ?: city
            ?: context?.countryName ?: pointLabel(point)
        return PlaceSuggestion(
            proposed.take(80),
            context?.countryCode?.takeIf { it.matches(Regex("[A-Za-z]{2}")) }?.uppercase(),
            context?.countryName?.take(100),
            city?.take(100),
        )
    }

    fun pointLabel(point: Coordinate): String =
        "位置 ${"%.5f".format(java.util.Locale.US, point.latitude)}, " +
            "%.5f".format(java.util.Locale.US, point.longitude)
}
