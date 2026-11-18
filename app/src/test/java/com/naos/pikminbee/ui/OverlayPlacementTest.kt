package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPlacementTest {
    @Test fun restoresUserPlacementOrSafeDefaults() {
        assertEquals(OverlayPlacement(18, 250), OverlayPlacement.fromStoredDp(null, null))
        assertEquals(OverlayPlacement(132, 455), OverlayPlacement.fromStoredDp(132, 455))
        assertEquals(OverlayPlacement(18, 250), OverlayPlacement.fromStoredDp(-1, 300))
        assertEquals(OverlayPlacement(18, 250), OverlayPlacement.fromStoredDp(23, 50_000))
    }

    @Test fun clampsDraggedPositionToVisibleScreenArea() {
        val p = OverlayPlacement(900, -500).clamp(
            screenWidth = 400, screenHeight = 800, panelWidth = 280,
            panelHeight = 460, horizontalMargin = 12, topMargin = 40, bottomMargin = 32
        )
        assertEquals(OverlayPlacement(108, 40), p)
    }

    @Test fun expandingAndRotatingCanRepositionWithoutLosingAllControls() {
        val original = OverlayPlacement(225, 620)
        val vertical = original.clamp(420, 900, 70, 70, 8, 35, 30)
        assertEquals(OverlayPlacement(225, 620), vertical)
        val expanded = vertical.clamp(420, 900, 300, 490, 8, 35, 30)
        assertEquals(OverlayPlacement(112, 380), expanded)
        val landscape = expanded.clamp(900, 420, 300, 320, 8, 35, 30)
        assertEquals(OverlayPlacement(112, 70), landscape)
    }

    @Test fun verySmallViewportNeverProducesNegativeOffsets() {
        val p = OverlayPlacement(200, 300).clamp(200, 200, 320, 320, 8, 35, 30)
        assertEquals(OverlayPlacement(0, 0), p)
    }

    @Test fun unchangedValidPlacementDoesNotJumpDuringRefresh() {
        val p = OverlayPlacement(45, 225)
        assertEquals(p, p.clamp(450, 1000, 320, 510, 12, 35, 32))
    }
}
