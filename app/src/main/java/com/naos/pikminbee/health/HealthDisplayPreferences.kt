package com.naos.pikminbee.health

import android.content.Context

/** Separate explicit display permission from Health Connect read/write grants. */
class HealthDisplayPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("pikminbee_health_display_v2", Context.MODE_PRIVATE)
    fun showOverlaySteps(): Boolean = prefs.getBoolean("show_overlay_steps", false)
    fun setShowOverlaySteps(value: Boolean) {
        prefs.edit().putBoolean("show_overlay_steps", value).apply()
    }
}
