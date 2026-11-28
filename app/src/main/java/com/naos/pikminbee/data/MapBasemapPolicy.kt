package com.naos.pikminbee.data

enum class MapProvider { OPEN_FREE_MAP, GOOGLE_MAPS }

/** Keyless, publicly documented OpenFreeMap styles; never use undocumented Google tile URLs. */
enum class FreeMapStyle(val label: String) {
    BRIGHT("Bright"),
}

object MapBasemapPolicy {
    private const val BASE = "https://tiles.openfreemap.org/styles/"

    /** All historical basemap preferences safely migrate to Bright. */
    @Suppress("UNUSED_PARAMETER")
    fun fromStored(raw: String?): FreeMapStyle = FreeMapStyle.BRIGHT

    fun availableProviders(hasGoogleMapsKey: Boolean): List<MapProvider> =
        if (hasGoogleMapsKey) listOf(MapProvider.OPEN_FREE_MAP, MapProvider.GOOGLE_MAPS)
        else listOf(MapProvider.OPEN_FREE_MAP)

    fun resolveProvider(preferred: MapProvider, hasGoogleMapsKey: Boolean): MapProvider =
        if (preferred == MapProvider.GOOGLE_MAPS && !hasGoogleMapsKey)
            MapProvider.OPEN_FREE_MAP
        else preferred

    /** Light and dark use the same Bright style; dark paint is applied separately. */
    @Suppress("UNUSED_PARAMETER")
    fun openFreeMapStyleUrl(style: FreeMapStyle, appDark: Boolean): String = BASE + "bright"
}
