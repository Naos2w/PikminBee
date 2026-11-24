package com.naos.pikminbee.data

import org.junit.Assert.*
import org.junit.Test

class MapBasemapPolicyTest {
    @Test fun noKeyUsesOpenFreeMapOnly() {
        assertEquals(listOf(MapProvider.OPEN_FREE_MAP),
            MapBasemapPolicy.availableProviders(hasGoogleMapsKey = false))
        assertEquals(MapProvider.OPEN_FREE_MAP, MapBasemapPolicy.resolveProvider(
            MapProvider.GOOGLE_MAPS, hasGoogleMapsKey = false,
        ))
        assertEquals(MapProvider.OPEN_FREE_MAP, MapBasemapPolicy.resolveProvider(
            MapProvider.OPEN_FREE_MAP, hasGoogleMapsKey = false,
        ))
    }

    @Test fun validGoogleKeyOnlyEnablesOptionalProvider() {
        assertEquals(2, MapBasemapPolicy.availableProviders(hasGoogleMapsKey = true).size)
        assertEquals(MapProvider.GOOGLE_MAPS, MapBasemapPolicy.resolveProvider(
            MapProvider.GOOGLE_MAPS, hasGoogleMapsKey = true,
        ))
    }

    @Test fun brightIsTheOnlyBasemapForBothThemesAndLegacyPreferences() {
        assertEquals(listOf(FreeMapStyle.BRIGHT), FreeMapStyle.entries.toList())
        listOf(null, "AUTO", "LIBERTY", "POSITRON", "DARK", "BRIGHT", "invalid").forEach {
            assertEquals(FreeMapStyle.BRIGHT, MapBasemapPolicy.fromStored(it))
        }
        listOf(true,false).forEach { dark ->
            assertEquals("https://tiles.openfreemap.org/styles/bright",
                MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.BRIGHT,dark))
        }
    }
}
