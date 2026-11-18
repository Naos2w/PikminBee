package com.naos.pikminbee.ui

/**
 * Pure overlay geometry in pixels. WindowManager uses gravity TOP|END so x is
 * the distance from the *right* edge and y is the distance from the top.
 */
data class OverlayPlacement(val x: Int, val y: Int) {
    companion object {
        const val DEFAULT_X_DP = 18
        const val DEFAULT_Y_DP = 250
        private const val MAX_STORED_DP = 20_000

        fun fromStoredDp(x: Int?, y: Int?): OverlayPlacement {
            if (x == null || y == null || x !in 0..MAX_STORED_DP ||
                y !in 0..MAX_STORED_DP
            ) return OverlayPlacement(DEFAULT_X_DP, DEFAULT_Y_DP)
            return OverlayPlacement(x, y)
        }
    }

    fun clamp(
        screenWidth: Int,
        screenHeight: Int,
        panelWidth: Int,
        panelHeight: Int,
        horizontalMargin: Int,
        topMargin: Int,
        bottomMargin: Int,
    ): OverlayPlacement {
        require(screenWidth > 0 && screenHeight > 0)
        require(panelWidth >= 0 && panelHeight >= 0)
        require(horizontalMargin >= 0 && topMargin >= 0 && bottomMargin >= 0)

        // When a tiny viewport cannot fit the panel, align it to the edge
        // instead of producing a negative WindowManager offset.
        val maximumX = (screenWidth - panelWidth - horizontalMargin).coerceAtLeast(0)
        val minimumX = horizontalMargin.coerceAtMost(maximumX)
        val maximumY = (screenHeight - panelHeight - bottomMargin).coerceAtLeast(0)
        val minimumY = topMargin.coerceAtMost(maximumY)
        return OverlayPlacement(x.coerceIn(minimumX, maximumX),
            y.coerceIn(minimumY, maximumY))
    }
}
