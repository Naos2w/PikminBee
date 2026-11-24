package com.naos.pikminbee.data

/** Keyless, publicly documented OpenFreeMap styles; never use undocumented Google tile URLs. */
enum class FreeMapStyle(val label: String) {
    AUTO("跟隨主題"),
    BRIGHT("Bright"),
    POSITRON("Positron"),
    DARK("Dark"),
}

object MapBasemapPolicy {
    private const val BASE = "https://tiles.openfreemap.org/styles/"

    /** Existing users with the retired LIBERTY preference keep their saved
     *  preference data; it resolves to AUTO (Positron light / Dark dark). */
    fun fromStored(raw: String?): FreeMapStyle =
        FreeMapStyle.entries.firstOrNull { it.name == raw } ?: FreeMapStyle.AUTO

    fun availableProviders(hasGoogleMapsKey: Boolean): List<MapProvider> =
        if (hasGoogleMapsKey) listOf(MapProvider.OPEN_FREE_MAP, MapProvider.GOOGLE_MAPS)
        else listOf(MapProvider.OPEN_FREE_MAP)

    fun resolveProvider(preferred: MapProvider, hasGoogleMapsKey: Boolean): MapProvider =
        if (preferred == MapProvider.GOOGLE_MAPS && !hasGoogleMapsKey)
            MapProvider.OPEN_FREE_MAP
        else preferred

    fun openFreeMapStyleUrl(style: FreeMapStyle, appDark: Boolean): String = BASE + when (style) {
        FreeMapStyle.AUTO -> if (appDark) "dark" else "positron"
        FreeMapStyle.BRIGHT -> "bright"
        FreeMapStyle.POSITRON -> "positron"
        FreeMapStyle.DARK -> "dark"
    }
}
