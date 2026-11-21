package com.naos.pikminbee.lan

/**
 * Shared HTTP security policy used by the built-in Wi-Fi browser server.
 * The local web UI is intentionally served without third-party executable scripts.
 */
object LanHttpPolicy {
    const val MAX_BODY_BYTES = 8192

    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        // MapLibre uses same-origin and Blob images for map resources.
        "img-src 'self' data: blob: https://tiles.openfreemap.org; " +
        "connect-src 'self' https://tiles.openfreemap.org; " +
        "font-src data: https://tiles.openfreemap.org; " +
        "worker-src 'self' blob:; " +
        "object-src 'none'; frame-ancestors 'none'"

    /** Status code only; caller supplies a generic JSON error response. */
    fun rejectRequest(
        method: String,
        authority: String,
        headers: Map<String, String>,
    ): Int? {
        if (headers["host"] != authority) return 403
        if (headers["transfer-encoding"] != null) return 400
        if (method == "POST" && headers["origin"] != "http://$authority") return 403
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 0..MAX_BODY_BYTES || (method == "POST" && length == 0)) return 413
        if (method == "POST" &&
            headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/json") {
            return 415
        }
        return null
    }
}
