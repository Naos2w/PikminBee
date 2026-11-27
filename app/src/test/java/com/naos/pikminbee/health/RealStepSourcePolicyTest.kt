package com.naos.pikminbee.health

import org.junit.Assert.*
import org.junit.Test

class RealStepSourcePolicyTest {
    @Test fun hcNativeFirstPreventsDuplicatePhoneSensorExport() {
        assertEquals(RealStepSource.HC_ON_DEVICE, RealStepSourcePolicy.select(true, true))
        assertFalse(RealStepSourcePolicy.mayExportSensor(true, true, true))
    }

    @Test fun legacyPhoneFallbackRequiresExplicitWriteConsent() {
        assertEquals(RealStepSource.PHONE_STEP_SENSOR, RealStepSourcePolicy.select(false, true))
        assertFalse(RealStepSourcePolicy.mayExportSensor(false, false, true))
        assertFalse(RealStepSourcePolicy.mayExportSensor(false, true, false))
        assertTrue(RealStepSourcePolicy.mayExportSensor(false, true, true))
    }

    @Test fun absentSensorDoesNotClaimToRecordWalks() {
        assertEquals(RealStepSource.UNAVAILABLE, RealStepSourcePolicy.select(false, false))
    }
}
