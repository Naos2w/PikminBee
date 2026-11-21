package com.naos.pikminbee.lan

/** Only a recent, non-mock Android fix can be described to the paired browser as real. */
internal object LanRealGpsPolicy {
    private const val MAX_FIX_AGE_MILLIS = 120_000L
    fun isVerifiedFix(timeMillis: Long, nowMillis: Long, mocked: Boolean): Boolean =
        !mocked && timeMillis > 0 && nowMillis - timeMillis in 0..MAX_FIX_AGE_MILLIS
}
