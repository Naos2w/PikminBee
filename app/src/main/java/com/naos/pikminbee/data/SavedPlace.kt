package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate

/** Local saved coordinate with optional resolved metadata. Null means unknown, never inferred. */
data class SavedPlace(
    val id: Long,
    val name: String,
    val coordinate: Coordinate,
    val countryCode: String? = null,
    val countryName: String? = null,
    val city: String? = null,
    val timeZoneId: String? = null,
    val createdAtMillis: Long = id,
)
