package com.naos.pikminbee.domain

/**
 * Exactly one completion alert for a user-started non-loop route.
 * Manual stop, pause, re-observation and loop lap boundaries never alert.
 */
object RouteCompletionAlertPolicy {
    fun shouldAlert(
        from: WalkPhase,
        to: WalkPhase,
        looping: Boolean,
        alreadyNotified: Boolean,
    ): Boolean = from == WalkPhase.MOVING &&
        to == WalkPhase.PARKED && !looping && !alreadyNotified
}
