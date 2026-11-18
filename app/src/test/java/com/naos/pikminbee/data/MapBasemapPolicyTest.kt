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

    @Test fun automaticStylesFollowAppearanceAndExplicitStylesAreIndependent() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty",
            MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.AUTO, false))
        assertEquals("https://tiles.openfreemap.org/styles/dark",
            MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.AUTO, true))
        assertEquals("https://tiles.openfreemap.org/styles/bright",
            MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.BRIGHT, true))
        assertEquals("https://tiles.openfreemap.org/styles/positron",
            MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.POSITRON, false))
        assertEquals("https://tiles.openfreemap.org/styles/liberty",
            MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.LIBERTY, true))
        assertEquals("https://tiles.openfreemap.org/styles/dark",
            MapBasemapPolicy.openFreeMapStyleUrl(FreeMapStyle.DARK, false))
    }
}
