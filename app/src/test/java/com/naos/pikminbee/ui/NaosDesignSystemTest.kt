package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaosDesignSystemTest {
    @Test fun sixApprovedThemesHaveDistinctLightAndDarkPalettes() {
        assertEquals(6, NaosThemeFamily.entries.size)
        NaosThemeFamily.entries.forEach { theme ->
            val light = NaosDesignSystem.palette(theme, false)
            val dark = NaosDesignSystem.palette(theme, true)
            assertTrue("Day/night background must differ: $theme", light.bg != dark.bg)
            assertTrue("Accent must be visible on both modes: $theme",
                light.accent != light.bg && dark.accent != dark.bg)
        }
    }

    @Test fun sageColorsMatchApprovedNaosUniversalDesignTokens() {
        val light = NaosDesignSystem.palette(NaosThemeFamily.SAGE, false)
        val dark = NaosDesignSystem.palette(NaosThemeFamily.SAGE, true)
        assertEquals(0xFF5E735FL, light.accent.value.toLong() ushr 32)
        assertEquals(0xFFB1C5AEL, dark.accent.value.toLong() ushr 32)
        assertEquals(0xFF26332AL, light.ink.value.toLong() ushr 32)
        assertEquals(0xFFEDF3EDL, dark.ink.value.toLong() ushr 32)
    }

    @Test fun materialDefaultsAndMotionMatchApprovedKit() {
        val visual = NaosVisualState()
        assertEquals(NaosThemeFamily.SAGE, visual.family)
        assertEquals(NaosGlassMaterial.REGULAR, visual.material)
        assertEquals(3, NaosGlassMaterial.entries.size)
        assertEquals(14f, NaosDesignSystem.GLASS_REFRACTION_PX)
        assertEquals(11f, NaosDesignSystem.GLASS_BLUR_PX)
        assertEquals(0.13f, NaosDesignSystem.GLASS_TINT)
        assertEquals(160, NaosDesignSystem.FAST_MS)
        assertEquals(280, NaosDesignSystem.NORMAL_MS)
        assertEquals(420, NaosDesignSystem.PANEL_MS)
    }
}
