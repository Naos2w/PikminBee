package com.naos.pikminbee.lan

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test

class LanPhysicalSharingPolicyTest {
    private val now = 1_795_350_000_000L
    private val verified = LanPhysicalSnapshot(
        Coordinate(25.033, 121.5654), now - 60_000L, 12f)

    @Test fun onlyTheExactConsentedSessionCanSeePreciseLocation() {
        assertTrue(LanPhysicalSharingPolicy.mayExpose("paired", "paired", verified, now))
        assertFalse(LanPhysicalSharingPolicy.mayExpose("other", "paired", verified, now))
        assertFalse(LanPhysicalSharingPolicy.mayExpose("paired", null, verified, now))
        assertFalse(LanPhysicalSharingPolicy.mayExpose(null, "paired", verified, now))
    }

    @Test fun expiryStartsAtPhysicalSampleAndNeverRenewsOnRead() {
        val latest = verified.copy(capturedAtMillis = now - 30L * 60_000L)
        assertTrue(LanPhysicalSharingPolicy.isFresh(latest, now))
        assertFalse(LanPhysicalSharingPolicy.isFresh(latest, now + 1))
        assertFalse(LanPhysicalSharingPolicy.isFresh(
            verified.copy(capturedAtMillis = now + 1), now))
        assertFalse(LanPhysicalSharingPolicy.mayExpose("paired", "paired",
            null, now))
    }

    @Test fun untrustedAccuracyIsNotPublishedAsVerified() {
        assertFalse(LanPhysicalSharingPolicy.isFresh(
            verified.copy(accuracyMetres = Float.NaN), now))
        assertFalse(LanPhysicalSharingPolicy.isFresh(
            verified.copy(accuracyMetres = -1f), now))
    }
}
