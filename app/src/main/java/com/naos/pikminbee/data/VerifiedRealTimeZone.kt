package com.naos.pikminbee.data

/**
 * An explicitly verified Android non-mock physical fix establishes the comparison zone.
 * The reference may survive a switch TO mock GPS, but not its 30-minute freshness window.
 * This is memory-only: no fake "Taiwan default" and no indefinite location history.
 */
data class VerifiedRealTimeZone(
    val timeZoneId: String,
    val physicalFixAtMillis: Long,
) {
    fun freshId(nowMillis: Long = System.currentTimeMillis()): String? =
        timeZoneId.takeIf {
            nowMillis - physicalFixAtMillis in 0L..MAX_AGE_MILLIS &&
                SavedPlaceTimePolicy.validatedZone(it) != null
        }

    companion object {
        const val MAX_AGE_MILLIS: Long = 30L * 60_000L
    }
}
