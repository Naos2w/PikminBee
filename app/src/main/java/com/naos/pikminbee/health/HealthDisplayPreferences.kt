package com.naos.pikminbee.health

import android.content.Context

/** Restore the 0.28.1 local display default without changing any HC grant. */
internal object HealthDisplayPolicy {
    fun overlayEnabled(saved: Boolean?): Boolean = saved ?: true
}

/** A display preference, not a Health Connect read/write permission. */
class HealthDisplayPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("pikminbee_health_display_v2", Context.MODE_PRIVATE)
    fun showOverlaySteps(): Boolean = HealthDisplayPolicy.overlayEnabled(
        if (prefs.contains("show_overlay_steps")) prefs.getBoolean("show_overlay_steps", true) else null)
    fun setShowOverlaySteps(value: Boolean) {
        prefs.edit().putBoolean("show_overlay_steps", value).apply()
    }
}
