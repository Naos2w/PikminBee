package com.naos.pikminbee.data

import com.naos.pikminbee.domain.PreparedWalkingRoute
import com.naos.pikminbee.domain.WalkMode
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-local prepared routes shared by the main Activity and quick overlay.
 * They are never silently restored after Android kills the process.
 */
object PreparedRouteState {
    val route = MutableStateFlow<PreparedWalkingRoute?>(null)
    val flowers = MutableStateFlow<PreparedWalkingRoute?>(null)

    fun get(mode: WalkMode): PreparedWalkingRoute? =
        if (mode == WalkMode.ROUTE) route.value else flowers.value

    fun set(mode: WalkMode, value: PreparedWalkingRoute?) {
        if (mode == WalkMode.ROUTE) route.value = value else flowers.value = value
    }
}
