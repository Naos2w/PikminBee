package com.naos.pikminbee.data

import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkSpeedStateTest {
    @Test fun storedCustomDefaultIsValidatedAndLegacyPresetIsFallback() {
        val preset = WalkSpeedPreset.WALK
        assertEquals(WalkSpeedMode.FIXED, WalkSpeedState.resolvePreferred(preset, "5.2").mode)
        assertEquals(5.2, WalkSpeedState.resolvePreferred(preset, "5.2").fixedKmh, 0.0)
        for (invalid in listOf(null, "", "NaN", "Infinity", "-1", "20.1")) {
            assertEquals(WalkSpeedProfile.preset(preset), WalkSpeedState.resolvePreferred(preset, invalid))
        }
    }

    @Test fun customDefaultAndCurrentSessionRemainIndependent() {
        val oldCurrent = WalkSpeedState.profile.value
        val oldPreferred = WalkSpeedState.preferredProfile.value
        try {
            WalkSpeedState.update(WalkSpeedPreset.WALK)
            val custom = WalkSpeedProfile.fixed(5.2)
            WalkSpeedState.updatePreferredProfile(custom)
            assertEquals(WalkSpeedProfile.preset(WalkSpeedPreset.WALK), WalkSpeedState.profile.value)
            WalkSpeedState.update(WalkSpeedPreset.SLOW)
            assertEquals(custom, WalkSpeedState.preferredProfile.value)
            WalkSpeedState.updatePreferred(WalkSpeedPreset.WALK)
            assertEquals(WalkSpeedMode.PRESET, WalkSpeedState.preferredProfile.value.mode)
            assertEquals(WalkSpeedProfile.preset(WalkSpeedPreset.SLOW), WalkSpeedState.profile.value)
        } finally {
            WalkSpeedState.updateProfile(oldCurrent)
            WalkSpeedState.updatePreferredProfile(oldPreferred)
        }
    }

    @Test fun changingCurrentSpeedNeverChangesPersonalDefault() {
        val oldCurrent = WalkSpeedState.selected.value
        val oldPreferred = WalkSpeedState.preferred.value
        try {
            WalkSpeedState.updatePreferred(WalkSpeedPreset.WALK)
            WalkSpeedState.update(WalkSpeedPreset.FAST)
            assertEquals(WalkSpeedPreset.FAST, WalkSpeedState.selected.value)
            assertEquals(WalkSpeedPreset.WALK, WalkSpeedState.preferred.value)
            WalkSpeedState.updatePreferred(WalkSpeedPreset.SLOW)
            assertEquals(WalkSpeedPreset.FAST, WalkSpeedState.selected.value)
            assertEquals(WalkSpeedPreset.SLOW, WalkSpeedState.preferred.value)
        } finally {
            WalkSpeedState.update(oldCurrent)
            WalkSpeedState.updatePreferred(oldPreferred)
        }
    }
}
