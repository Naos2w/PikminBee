package com.naos.pikminbee.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Source-level WCAG AA contrast gates for the shared Material 3 palettes. */
class PikminBeeThemeColorsTest {
    private fun ratio(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    private fun readable(label: String, text: Color, surface: Color) {
        val value = ratio(text, surface)
        assertTrue("$label contrast $value must be at least 4.5:1", value >= 4.5)
    }

    @Test fun darkCardsAndMaterialControlsUseReadableText() {
        val s = PikminBeeThemeColors.dark
        readable("dark surface text", s.onSurface, s.surface)
        readable("dark secondary text", s.onSurfaceVariant, s.surface)
        readable("dark buttons and links", s.primary, s.surface)
        readable("dark selected chips", s.onSecondaryContainer, s.secondaryContainer)
        readable("dark primary container", s.onPrimaryContainer, s.primaryContainer)
    }

    @Test fun lightCardsAndMaterialControlsUseReadableText() {
        val s = PikminBeeThemeColors.light
        readable("light surface text", s.onSurface, s.surface)
        readable("light secondary text", s.onSurfaceVariant, s.surface)
        readable("light buttons and links", s.primary, s.surface)
        readable("light selected chips", s.onSecondaryContainer, s.secondaryContainer)
        readable("light primary container", s.onPrimaryContainer, s.primaryContainer)
    }

    @Test fun honeyPrimaryActionAlwaysUsesWhiteText() {
        readable("honey primary button", Color.White, Color(0xFF9F5E0C))
    }

    @Test fun themeSwitchSelectsAppropriateMaterialScheme() {
        assertTrue(PikminBeeThemeColors.forDarkMode(true) === PikminBeeThemeColors.dark)
        assertTrue(PikminBeeThemeColors.forDarkMode(false) === PikminBeeThemeColors.light)
    }
}
