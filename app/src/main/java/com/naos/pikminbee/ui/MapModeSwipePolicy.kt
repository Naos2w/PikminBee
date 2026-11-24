package com.naos.pikminbee.ui

import kotlin.math.abs

/**
 * Horizontal page navigation is allowed on the bottom dock and sheet content,
 * never on the interactive map surface. A vertical scroll does not change tabs.
 */
object MapModeSwipePolicy {
    // Settings is tap-only; only Teleport and Route are swipeable.
    const val TAB_COUNT = 2
    const val MIN_SWIPE_DP = 72f

    fun target(currentIndex: Int, horizontalDistancePx: Float, thresholdPx: Float): Int? {
        if (currentIndex !in 0 until TAB_COUNT ||
            !horizontalDistancePx.isFinite() ||
            !thresholdPx.isFinite() || thresholdPx <= 0f ||
            abs(horizontalDistancePx) < thresholdPx) return null
        val next = currentIndex + if (horizontalDistancePx < 0f) 1 else -1
        return next.takeIf { it in 0 until TAB_COUNT }
    }
}
