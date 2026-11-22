package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapModeSwipePolicyTest {
    @Test fun leftSwipeShowsNextModeAndRightSwipeShowsPreviousMode() {
        assertEquals(1, MapModeSwipePolicy.target(0, -90f, 72f))
        assertEquals(2, MapModeSwipePolicy.target(1, -85f, 72f))
        assertNull(MapModeSwipePolicy.target(2, -85f, 72f))
        assertNull(MapModeSwipePolicy.target(3, 80f, 72f))
    }

    @Test fun shortOrVerticalGestureKeepsCurrentMode() {
        assertNull(MapModeSwipePolicy.target(1, 0f, 72f))
        assertNull(MapModeSwipePolicy.target(1, 60f, 72f))
        assertEquals(0, MapModeSwipePolicy.target(1, 72f, 72f))
    }

    @Test fun pageEdgesDoNotWrapToAnotherMode() {
        assertNull(MapModeSwipePolicy.target(0, 100f, 72f))
        assertNull(MapModeSwipePolicy.target(2, -100f, 72f))
        assertNull(MapModeSwipePolicy.target(3, -100f, 72f))
        assertNull(MapModeSwipePolicy.target(-1, -100f, 72f))
        assertNull(MapModeSwipePolicy.target(4, 100f, 72f))
        assertNull(MapModeSwipePolicy.target(1, Float.NaN, 72f))
    }
}
