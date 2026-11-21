package com.naos.pikminbee.ui

import org.junit.Assert.*
import org.junit.Test

class FlowerCandidateSavePolicyTest {
    @Test fun onlyConservativeInHullManualCandidatesMayBeStoredUnverified() {
        assertTrue(FlowerCandidateSavePolicy.canStore(true, 10.0, 28.0))
        assertFalse(FlowerCandidateSavePolicy.canStore(false, 0.0, 0.0))
        assertFalse(FlowerCandidateSavePolicy.canStore(true, 61.0, 20.0))
        assertFalse(FlowerCandidateSavePolicy.canStore(true, 10.0, 121.0))
        assertFalse(FlowerCandidateSavePolicy.canStore(true, Double.NaN, 5.0))
        assertFalse(FlowerCandidateSavePolicy.canStore(true, Double.POSITIVE_INFINITY, 5.0))
    }
}
