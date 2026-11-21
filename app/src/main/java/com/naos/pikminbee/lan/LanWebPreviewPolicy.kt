package com.naos.pikminbee.lan

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.PreparedWalkingRoute

/** Pure server-side gate: browser controls/HTML can never skip the preview. */
internal object LanWebPreviewPolicy {
    enum class StartCheck { READY, MISSING, STALE, CONFIRM_SNAP, ALREADY_USED }

    fun canStart(
        prepared: PreparedWalkingRoute?,
        previewId: String?,
        requestedId: String?,
        currentOrigin: Coordinate?,
        currentStops: List<Coordinate>,
        loop: Boolean,
        acknowledgeLargeSnap: Boolean,
        lastUsedId: String?,
    ): StartCheck {
        if (prepared == null || previewId.isNullOrBlank() || requestedId != previewId)
            return StartCheck.MISSING
        if (!prepared.isCurrent(currentOrigin, currentStops, loop)) return StartCheck.STALE
        if (previewId == lastUsedId) return StartCheck.ALREADY_USED
        if (prepared.requiresSnapConfirmation && !acknowledgeLargeSnap)
            return StartCheck.CONFIRM_SNAP
        return StartCheck.READY
    }
}
