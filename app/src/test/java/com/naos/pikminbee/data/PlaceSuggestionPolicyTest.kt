package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test

class PlaceSuggestionPolicyTest {
    private val point = Coordinate(35.681236, 139.767125)
    private val context = GeocodeCandidate("Tokyo",point,"place",null,"Tokyo",null,"JP","Japan")
    @Test fun nearbyPoiWinsButDistantBusinessNeverHijacksSelection() {
        val near = GeocodeCandidate("Tokyo Station",Coordinate(35.6813,139.7672),
            "railway","Marunouchi","Tokyo",null,"JP","Japan")
        val far = GeocodeCandidate("Far shop",Coordinate(35.692,139.770),
            "shop",null,"Tokyo",null,"JP","Japan")
        assertEquals("Tokyo Station", PlaceSuggestionPolicy.nameFor(point,
            listOf(far,context,near)).displayName)
        assertEquals("Tokyo", PlaceSuggestionPolicy.nameFor(point,
            listOf(far,context)).displayName)
    }

    @Test fun photonUsesSupportedOsmNativeLanguageInsteadOfUnsupportedZh() {
        val url = PhotonReverseRequest.url(Coordinate(25.033, 121.5654))
        assertTrue(url.startsWith("https://photon.komoot.io/reverse?"))
        assertTrue(url.contains("lat=25.033000"))
        assertTrue(url.contains("lon=121.565400"))
        assertTrue(url.endsWith("&lang=default"))
        assertFalse(url.contains("lang=zh"))
    }

    @Test fun streetThenDistrictThenCityThenCoordinateFallback() {
        val street = GeocodeCandidate(null,point,"highway","Marunouchi 1", "Tokyo",null,"JP","Japan")
        assertEquals("Marunouchi 1", PlaceSuggestionPolicy.nameFor(point,listOf(street)).displayName)
        assertTrue(PlaceSuggestionPolicy.nameFor(point,emptyList()).displayName.startsWith("位置 "))
        val found = PlaceSuggestionPolicy.nameFor(point,listOf(context))
        assertEquals("JP",found.countryCode)
        assertEquals("Tokyo",found.city)
    }
}
