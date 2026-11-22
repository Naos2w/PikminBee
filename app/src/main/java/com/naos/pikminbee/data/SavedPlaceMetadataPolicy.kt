package com.naos.pikminbee.data

/** Backfill only missing metadata. Never overwrite a user's chosen name/coordinate. */
object SavedPlaceMetadataPolicy {
    fun incomplete(place: SavedPlace): Boolean =
        place.countryCode.isNullOrBlank() ||
            place.countryName.isNullOrBlank() ||
            place.city.isNullOrBlank() ||
            place.timeZoneId.isNullOrBlank()

    fun enriched(place: SavedPlace, suggestion: PlaceSuggestion): SavedPlace {
        fun missing(stored: String?): Boolean = stored.isNullOrBlank()
        val code = suggestion.countryCode?.uppercase(java.util.Locale.ROOT)
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
        val timezone = suggestion.timeZoneId?.takeIf {
            SavedPlaceTimePolicy.validatedZone(it) != null
        }
        return place.copy(
            countryCode = if (missing(place.countryCode)) code else place.countryCode,
            countryName = if (missing(place.countryName))
                suggestion.countryName?.take(100) else place.countryName,
            city = if (missing(place.city)) suggestion.city?.take(100) else place.city,
            timeZoneId = if (missing(place.timeZoneId)) timezone else place.timeZoneId,
        )
    }
}
