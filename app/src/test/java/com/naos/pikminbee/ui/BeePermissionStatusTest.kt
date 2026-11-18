package com.naos.pikminbee.ui

import org.junit.Assert.*
import org.junit.Test

class BeePermissionStatusTest {
    @Test fun onlyLocationAndMockSelectionGateGpsOnboarding() {
        val missing = BeePermissionStatus(false, false, false, false, false)
        assertFalse(missing.canContinue(0))
        assertFalse(missing.canContinue(1))
        assertTrue(missing.canContinue(2))
        assertTrue(missing.canContinue(3))
        assertTrue(missing.canContinue(4))
        val preciseOnly = missing.copy(preciseLocation = true)
        assertTrue(preciseOnly.canContinue(0))
        assertFalse(preciseOnly.canContinue(1))
        val requiredDone = preciseOnly.copy(selectedMockApp = true)
        assertTrue(requiredDone.canContinue(0))
        assertTrue(requiredDone.canContinue(1))
    }

    @Test fun selectedMockAppDoesNotBypassMissingPreciseLocation() {
        val mockOnly = BeePermissionStatus(false, true, false, true, false)
        assertFalse(mockOnly.canContinue(0))
        assertTrue(mockOnly.canContinue(1))
    }
}
