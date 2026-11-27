package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class HealthDisplayPolicyTest {
    @Test fun absentSettingRestoresOriginalVisibleDefault() {
        // Includes an upgrade from 0.28.1 and a later install that never toggled the new preference.
        assertTrue(HealthDisplayPolicy.overlayEnabled(null))
    }

    @Test fun explicitUserChoiceIsPreserved() {
        assertTrue(HealthDisplayPolicy.overlayEnabled(true))
        assertFalse(HealthDisplayPolicy.overlayEnabled(false))
    }
}
