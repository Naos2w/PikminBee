package com.naos.pikminbee.ui

/**
 * Explicit, conservative gate for saving a pixel-calibrated detection as an
 * unverified catalogue candidate. This is not a measurement of in-game accuracy
 * and never authorises mock-GPS movement or automatic route insertion.
 */
object FlowerCandidateSavePolicy {
    fun canStore(insideAnchorHull: Boolean, heldOutRmseMetres: Double,
        worstHeldOutErrorMetres: Double): Boolean =
        insideAnchorHull && heldOutRmseMetres.isFinite() &&
            worstHeldOutErrorMetres.isFinite() &&
            heldOutRmseMetres in 0.0..60.0 &&
            worstHeldOutErrorMetres in 0.0..120.0
}
