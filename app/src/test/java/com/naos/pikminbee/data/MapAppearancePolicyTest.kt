package com.naos.pikminbee.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAppearancePolicyTest {
    @Test fun newUsersAndLegacySystemPreferenceFollowAndroid() {
        assertEquals(MapAppearance.SYSTEM, MapAppearancePolicy.fromStored(null))
        assertEquals(MapAppearance.SYSTEM, MapAppearancePolicy.fromStored("SYSTEM"))
        assertEquals(MapAppearance.SYSTEM, MapAppearancePolicy.fromStored("NOT_A_THEME"))
        assertFalse(MapAppearancePolicy.isDark(MapAppearance.SYSTEM, false))
        assertTrue(MapAppearancePolicy.isDark(MapAppearance.SYSTEM, true))
    }

    @Test fun manualLightDarkOverridesAndroid() {
        assertEquals(MapAppearance.LIGHT, MapAppearancePolicy.fromStored("LIGHT"))
        assertEquals(MapAppearance.DARK, MapAppearancePolicy.fromStored("DARK"))
        assertFalse(MapAppearancePolicy.isDark(MapAppearance.LIGHT, true))
        assertTrue(MapAppearancePolicy.isDark(MapAppearance.DARK, false))
    }
}
