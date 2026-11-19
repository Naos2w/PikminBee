package com.naos.pikminbee.location

import com.naos.pikminbee.domain.Coordinate
import kotlinx.coroutines.flow.MutableStateFlow

/** Latest explicitly selected point and live phone fix shared with the floating controls.
 * A map selection is never a mock GPS command by itself.
 */
object OverlayMapSelection {
    val picked = MutableStateFlow<Coordinate?>(null)
    val deviceFix = MutableStateFlow<Coordinate?>(null)
    val deviceFixTimestampMillis = MutableStateFlow(0L)
}
