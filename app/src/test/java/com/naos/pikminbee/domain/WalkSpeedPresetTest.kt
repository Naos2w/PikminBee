package com.naos.pikminbee.domain

import org.junit.Assert.*
import org.junit.Test

class WalkSpeedPresetTest {
    @Test fun exactKmhPresetsConvertToMetresPerSecond() {
        assertEquals(0.8 / 3.6, WalkSpeedPreset.SLOW.metresPerSecond, 1e-10)
        assertEquals(1.4 / 3.6, WalkSpeedPreset.WALK.metresPerSecond, 1e-10)
        assertEquals(3.0, WalkSpeedPreset.FAST.metresPerSecond, 1e-10)
    }

    @Test fun defaultAndUnknownSavedValueAreNormalWalk() {
        assertEquals(WalkSpeedPreset.WALK, WalkSpeedPreset.DEFAULT)
        assertEquals(WalkSpeedPreset.WALK, WalkSpeedPreset.fromSaved(null))
        assertEquals(WalkSpeedPreset.WALK, WalkSpeedPreset.fromSaved("1.4"))
        assertEquals(WalkSpeedPreset.WALK, WalkSpeedPreset.fromSaved("INVALID"))
        assertEquals(WalkSpeedPreset.SLOW, WalkSpeedPreset.fromSaved("SLOW"))
        assertEquals(WalkSpeedPreset.FAST, WalkSpeedPreset.fromSaved("FAST"))
    }

    @Test fun storedDefaultOverridesLastSelectedWithSafeMigration() {
        assertEquals(WalkSpeedPreset.FAST,
            WalkSpeedPreset.fromSavedDefault(null, "FAST"))
        assertEquals(WalkSpeedPreset.SLOW,
            WalkSpeedPreset.fromSavedDefault("SLOW", "FAST"))
        assertEquals(WalkSpeedPreset.WALK,
            WalkSpeedPreset.fromSavedDefault(null, null))
        assertEquals(WalkSpeedPreset.WALK,
            WalkSpeedPreset.fromSavedDefault("INVALID", "FAST"))
    }

    @Test fun labelsAreExplicitKmhNotOldMetresPerSecond() {
        assertEquals("慢走 · 0.8 km/h", WalkSpeedPreset.SLOW.display)
        assertEquals("走路 · 1.4 km/h", WalkSpeedPreset.WALK.display)
        assertEquals("快走 · 10.8 km/h", WalkSpeedPreset.FAST.display)
    }
}
