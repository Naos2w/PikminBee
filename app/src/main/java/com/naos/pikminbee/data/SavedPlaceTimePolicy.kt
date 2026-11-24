package com.naos.pikminbee.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Display IANA zones only when they were resolved for actual coordinates. */
object SavedPlaceTimePolicy {
    private val validZones: Set<String> = TimeZone.getAvailableIDs().toSet()

    fun validatedZone(zoneId: String?): TimeZone? =
        zoneId?.takeIf { it in validZones }
            ?.let(TimeZone::getTimeZone)

    fun localClock(zoneId: String?, nowMillis: Long): String? =
        validatedZone(zoneId)?.let { zone ->
            SimpleDateFormat("HH:mm", Locale.TAIWAN).apply { timeZone = zone }
                .format(Date(nowMillis))
        }

    fun savedDate(createdAtMillis: Long): String =
        if (createdAtMillis <= 0) "收藏日期不明"
        else SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date(createdAtMillis))

    /** The same packaged PNG is used by Android and the LAN browser. */
    fun flagAssetPath(countryCode: String?): String? {
        val code = countryCode?.trim()?.lowercase(Locale.ROOT)
        return code?.takeIf { it.matches(Regex("[a-z]{2}")) }?.let { "lan/flags/$it.png" }
    }
}
