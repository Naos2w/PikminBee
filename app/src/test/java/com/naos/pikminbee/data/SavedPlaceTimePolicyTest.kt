package com.naos.pikminbee.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SavedPlaceTimePolicyTest {
    private val winter = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
    private val summer = Instant.parse("2026-07-22T12:00:00Z").toEpochMilli()

    @Test fun comparesVerifiedRealZoneRatherThanHardcodedTaiwan() {
        assertEquals("比目前所在地快 1 小時",
            SavedPlaceTimePolicy.differenceLabel("Asia/Tokyo","Asia/Taipei",winter))
        assertEquals("比目前所在地慢 1 小時",
            SavedPlaceTimePolicy.differenceLabel("Asia/Taipei","Asia/Tokyo",winter))
        assertEquals("與目前所在地相同",
            SavedPlaceTimePolicy.differenceLabel("Asia/Taipei","Asia/Taipei",winter))
    }

    @Test fun daylightSavingDifferencesUseCurrentInstant() {
        assertEquals("比目前所在地慢 5 小時",
            SavedPlaceTimePolicy.differenceLabel("America/New_York","Europe/London",winter))
        assertEquals("比目前所在地慢 5 小時",
            SavedPlaceTimePolicy.differenceLabel("America/New_York","Europe/London",summer))
        assertEquals("比目前所在地慢 13 小時",
            SavedPlaceTimePolicy.differenceLabel("America/New_York","Asia/Taipei",winter))
        assertEquals("比目前所在地慢 12 小時",
            SavedPlaceTimePolicy.differenceLabel("America/New_York","Asia/Taipei",summer))
    }

    @Test fun refusesUnknownZoneOrMissingRealReference() {
        assertNull(SavedPlaceTimePolicy.localClock("invalid/City",winter))
        assertNull(SavedPlaceTimePolicy.differenceLabel("Asia/Tokyo",null,winter))
        assertNull(SavedPlaceTimePolicy.differenceLabel(null,"Asia/Taipei",winter))
        assertNull(SavedPlaceTimePolicy.differenceLabel("invalid/City","Asia/Taipei",winter))
        assertEquals("🇯🇵",SavedPlaceTimePolicy.flagEmoji("jp"))
    }
}
