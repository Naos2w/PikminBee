package com.naos.pikminbee.lan

import com.naos.pikminbee.domain.Coordinate

/** Short-lived physical location sample, never written to preferences or disk. */
data class LanPhysicalSnapshot(
    val coordinate: Coordinate,
    val capturedAtMillis: Long,
    val accuracyMetres: Float?,
)

object LanPhysicalSharingPolicy {
    const val MAX_AGE_MILLIS = 30L * 60_000L

    /** Compare against sample time, not time since the browser opened. */
    fun isFresh(snapshot: LanPhysicalSnapshot?, nowMillis: Long): Boolean =
        snapshot != null &&
            nowMillis - snapshot.capturedAtMillis in 0L..MAX_AGE_MILLIS &&
            (snapshot.accuracyMetres == null ||
                (snapshot.accuracyMetres.isFinite() && snapshot.accuracyMetres >= 0f))

    /** Precise coordinates may only go to the exact session approved on the phone. */
    fun mayExpose(
        requesterToken: String?, grantedSessionToken: String?,
        snapshot: LanPhysicalSnapshot?, nowMillis: Long,
    ): Boolean =
        !requesterToken.isNullOrEmpty() &&
            requesterToken == grantedSessionToken &&
            isFresh(snapshot, nowMillis)
}
