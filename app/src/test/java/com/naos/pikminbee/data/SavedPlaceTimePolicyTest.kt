package com.naos.pikminbee.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SavedPlaceTimePolicyTest {
    private val winter = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
    private val summer = Instant.parse("2026-07-22T12:00:00Z").toEpochMilli()

    @Test fun destinationClocksDoNotNeedRealGps() {
        assertEquals("11/22 20:00", SavedPlaceTimePolicy.localClock("Asia/Taipei", winter))
        assertEquals("11/22 21:00", SavedPlaceTimePolicy.localClock("Asia/Tokyo", winter))
        assertEquals("11/22 17:45", SavedPlaceTimePolicy.localClock("Asia/Kathmandu", winter))
        assertEquals("11/22 00:00", SavedPlaceTimePolicy.localClock("UTC",
            Instant.parse("2026-11-22T00:00:00Z").toEpochMilli()))
    }

    @Test fun localClockRespectsDaylightSaving() {
        assertEquals("11/22 07:00", SavedPlaceTimePolicy.localClock("America/New_York", winter))
        assertEquals("07/22 08:00", SavedPlaceTimePolicy.localClock("America/New_York", summer))
    }

    @Test fun unknownZonesRemainUnknown() {
        for (zone in listOf(null, "", "invalid/City", "+02:00"))
            assertNull(SavedPlaceTimePolicy.localClock(zone, winter))
    }

    @Test fun dateChangesAcrossZonesAndNewYearIncludesYear() {
        val sameInstant = Instant.parse("2026-11-25T02:30:00Z").toEpochMilli()
        assertEquals("11/25 10:30", SavedPlaceTimePolicy.localClock("Asia/Taipei", sameInstant))
        assertEquals("11/24 18:30", SavedPlaceTimePolicy.localClock("America/Los_Angeles", sameInstant))
        val newYear = Instant.parse("2026-12-31T16:30:00Z").toEpochMilli()
        assertEquals("2027/01/01 00:30", SavedPlaceTimePolicy.localClock("Asia/Taipei", newYear))
        assertEquals("2026/12/31 08:30", SavedPlaceTimePolicy.localClock("America/Los_Angeles", newYear))
    }

    @Test fun boundedSingleZoneFallbackNeverGuessesMultiZoneCountries() {
        assertEquals("11/22 20:00", SavedPlaceTimePolicy.localClock(null, winter, "TW"))
        assertEquals("11/22 21:00", SavedPlaceTimePolicy.localClock("invalid/City", winter, "jp"))
        assertEquals("11/22 07:00", SavedPlaceTimePolicy.localClock("America/New_York", winter, "TW"))
        for (country in listOf("US", "AU", "CA", null, ""))
            assertNull(SavedPlaceTimePolicy.localClock(null, winter, country))
    }

    @Test fun flagPathsAreLocalAndStrictlyBounded() {
        assertEquals("lan/flags/jp.png", SavedPlaceTimePolicy.flagAssetPath("jp"))
        assertEquals("lan/flags/tw.png", SavedPlaceTimePolicy.flagAssetPath("TW"))
        assertEquals("lan/flags/gb.png", SavedPlaceTimePolicy.flagAssetPath(" GB "))
        for (code in listOf(null, "", "TWN", "../tw", "/jp.png", "1A", "東京"))
            assertNull(SavedPlaceTimePolicy.flagAssetPath(code))
    }
}
