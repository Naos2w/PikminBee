package com.naos.pikminbee.lan

import org.junit.Assert.*
import org.junit.Test

class LanRealGpsPolicyTest {
    @Test fun noMockGpsMayBePassedOffAsReal() {
        val now = 1_700_000_000_000L
        assertTrue(LanRealGpsPolicy.isVerifiedFix(now - 10_000, now, false))
        assertFalse(LanRealGpsPolicy.isVerifiedFix(now - 10_000, now, true))
        assertFalse(LanRealGpsPolicy.isVerifiedFix(now - 130_000, now, false))
        assertFalse(LanRealGpsPolicy.isVerifiedFix(now + 10_000, now, false))
        assertFalse(LanRealGpsPolicy.isVerifiedFix(0, now, false))
    }
}
