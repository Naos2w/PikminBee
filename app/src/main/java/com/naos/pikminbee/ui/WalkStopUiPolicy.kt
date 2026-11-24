package com.naos.pikminbee.ui

import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase

/** Confirming Stop must never launch a stale command after a walk has ended. */
object WalkStopUiPolicy {
    fun canRequest(mode: WalkMode?, phase: WalkPhase): Boolean =
        mode == WalkMode.ROUTE && (phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED)
}
