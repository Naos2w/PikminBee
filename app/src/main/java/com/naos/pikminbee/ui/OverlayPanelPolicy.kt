package com.naos.pikminbee.ui

import com.naos.pikminbee.lan.LanConnection
import com.naos.pikminbee.lan.LanConnectionPhase
import java.net.URI

internal enum class OverlayDetail { NONE, SPEED, WIFI }
internal enum class OverlayDetailLayout { GRID_AND_DETAIL, DETAIL_ONLY, OPEN_APP }
internal enum class OverlayWifiView { OFF, STARTING, PAIRING, APPROVAL, AWAITING_BROWSER, SESSION, ERROR }

/** UI-only policies: no authorization, session duration or location-engine changes. */
internal object OverlayPanelPolicy {
    fun toggle(current: OverlayDetail, requested: OverlayDetail): OverlayDetail =
        if (current == requested) OverlayDetail.NONE else requested

    fun layout(available: Int, header: Int, grid: Int, detail: Int, editing: Boolean = false): OverlayDetailLayout =
        when {
            header + detail > available -> OverlayDetailLayout.OPEN_APP
            editing || header + grid + detail > available -> OverlayDetailLayout.DETAIL_ONLY
            else -> OverlayDetailLayout.GRID_AND_DETAIL
        }

    fun wifiView(active: Boolean, connection: LanConnection): OverlayWifiView = when {
        connection.phase == LanConnectionPhase.ERROR -> OverlayWifiView.ERROR
        connection.phase == LanConnectionPhase.STARTING -> OverlayWifiView.STARTING
        !active -> OverlayWifiView.OFF
        connection.pendingDevice.isNotBlank() -> OverlayWifiView.APPROVAL
        connection.hasSession -> OverlayWifiView.SESSION
        connection.awaitingBrowser -> OverlayWifiView.AWAITING_BROWSER
        connection.showPin -> OverlayWifiView.PAIRING
        else -> OverlayWifiView.ERROR
    }

    /** Only the current local pairing page, never a PIN, query or token. No DNS lookup. */
    fun browserPage(active: Boolean, address: String): String? {
        if (!active) return null
        val uri = runCatching { URI(address) }.getOrNull() ?: return null
        if (uri.scheme != "http" || uri.port != 8765 || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/"))
            return null
        val parts = uri.host?.split('.') ?: return null
        if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        val privateAddress = octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
        return if (privateAddress) address else null
    }
}
