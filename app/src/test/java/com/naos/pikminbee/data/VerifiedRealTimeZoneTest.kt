package com.naos.pikminbee.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerifiedRealTimeZoneTest {
    private val now = 1_795_340_000_000L

    @Test fun aVerifiedNonMockZoneSurvivesModeSwitchUntilItExpires() {
        val physical = VerifiedRealTimeZone("Asia/Taipei", now - 10 * 60_000L)
        assertEquals("Asia/Taipei", physical.freshId(now))
        // Mock/real button state does not change this trusted historical reference.
        assertEquals("Asia/Taipei", physical.freshId(now + 19 * 60_000L))
        assertNull(physical.freshId(now + 21 * 60_000L))
    }

    @Test fun futureFixAndUnknownZoneAreNotTrusted() {
        assertNull(VerifiedRealTimeZone("Asia/Taipei", now + 10).freshId(now))
        assertNull(VerifiedRealTimeZone("Fake/City", now).freshId(now))
    }
}
