package com.naos.pikminbee.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Destination clock uses resolved IANA data, never physical or mock GPS. */
object SavedPlaceTimePolicy {
    private val validZones: Set<String> = TimeZone.getAvailableIDs().toSet()

    fun validatedZone(zoneId: String?): TimeZone? =
        zoneId?.takeIf { it in validZones }
            ?.let(TimeZone::getTimeZone)

    // Conservative whitelist based on IANA tzdb zone1970.tab, not device time:
    // https://github.com/eggert/tz/blob/main/zone1970.tab (TW, JP).
    // Do not generalise this to countries with multiple civil time zones.
    fun effectiveZone(zoneId: String?, countryCode: String?): TimeZone? =
        validatedZone(zoneId) ?: validatedZone(when (countryCode?.trim()?.uppercase(Locale.ROOT)) {
            "TW" -> "Asia/Taipei"
            "JP" -> "Asia/Tokyo"
            else -> null
        })

    fun localClock(zoneId: String?, nowMillis: Long, countryCode: String? = null): String? =
        effectiveZone(zoneId, countryCode)?.let { zone ->
            val date = Date(nowMillis)
            val local = SimpleDateFormat("MM/dd HH:mm", Locale.US).apply { timeZone = zone }.format(date)
            // Include the year at either side of New Year, consistently in App and Web.
            if (local.startsWith("12/31") || local.startsWith("01/01")) {
                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).apply { timeZone = zone }.format(date)
            } else local
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
