package com.naos.pikminbee.data

import com.naos.pikminbee.domain.WalkSpeedPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkSpeedStateTest {
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
