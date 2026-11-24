package com.naos.pikminbee.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SavedPlaceTimePolicyTest {
    private val winter = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
    private val summer = Instant.parse("2026-07-22T12:00:00Z").toEpochMilli()

    @Test fun destinationClocksDoNotNeedRealGps() {
        assertEquals("20:00", SavedPlaceTimePolicy.localClock("Asia/Taipei", winter))
        assertEquals("21:00", SavedPlaceTimePolicy.localClock("Asia/Tokyo", winter))
        assertEquals("17:45", SavedPlaceTimePolicy.localClock("Asia/Kathmandu", winter))
        assertEquals("00:00", SavedPlaceTimePolicy.localClock("UTC",
            Instant.parse("2026-11-22T00:00:00Z").toEpochMilli()))
    }

    @Test fun localClockRespectsDaylightSaving() {
        assertEquals("07:00", SavedPlaceTimePolicy.localClock("America/New_York", winter))
        assertEquals("08:00", SavedPlaceTimePolicy.localClock("America/New_York", summer))
    }

    @Test fun unknownZonesRemainUnknown() {
        for (zone in listOf(null, "", "invalid/City", "+02:00"))
            assertNull(SavedPlaceTimePolicy.localClock(zone, winter))
    }

    @Test fun flagPathsAreLocalAndStrictlyBounded() {
        assertEquals("lan/flags/jp.png", SavedPlaceTimePolicy.flagAssetPath("jp"))
        assertEquals("lan/flags/tw.png", SavedPlaceTimePolicy.flagAssetPath("TW"))
        assertEquals("lan/flags/gb.png", SavedPlaceTimePolicy.flagAssetPath(" GB "))
        for (code in listOf(null, "", "TWN", "../tw", "/jp.png", "1A", "東京"))
            assertNull(SavedPlaceTimePolicy.flagAssetPath(code))
    }
}
