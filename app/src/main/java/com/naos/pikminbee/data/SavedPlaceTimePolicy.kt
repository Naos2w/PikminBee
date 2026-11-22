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

    /** No real GPS time zone => no relative comparison, even if device clock has a zone. */
    fun differenceLabel(destinationId: String?, verifiedRealId: String?, nowMillis: Long): String? {
        val destination = validatedZone(destinationId) ?: return null
        val reference = validatedZone(verifiedRealId) ?: return null
        val deltaMinutes = (destination.getOffset(nowMillis) - reference.getOffset(nowMillis)) / 60_000
        if (deltaMinutes == 0) return "與目前所在地相同"
        val magnitude = kotlin.math.abs(deltaMinutes)
        val hours = magnitude / 60
        val minutes = magnitude % 60
        val duration = when {
            minutes == 0 -> "${hours} 小時"
            hours == 0 -> "${minutes} 分鐘"
            else -> "${hours} 小時 ${minutes} 分鐘"
        }
        return "比目前所在地${if (deltaMinutes > 0) "快" else "慢"} $duration"
    }

    fun savedDate(createdAtMillis: Long): String =
        if (createdAtMillis <= 0) "收藏日期不明"
        else SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN).format(Date(createdAtMillis))

    fun flagEmoji(countryCode: String?): String {
        val code = countryCode?.uppercase(Locale.ROOT)
        if (code == null || !code.matches(Regex("[A-Z]{2}"))) return ""
        val first = Character.toChars(0x1F1E6 + code[0].code - 'A'.code)
        val second = Character.toChars(0x1F1E6 + code[1].code - 'A'.code)
        return String(first) + String(second)
    }
}
