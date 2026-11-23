package com.naos.pikminbee.domain

import kotlin.math.min
import kotlin.random.Random

/** Explicit test-location speed. Never claims real physical movement. */
enum class WalkSpeedKind { PRESET, FIXED, RANGE }

// The speed controller and existing Android clients use this public name.
typealias WalkSpeedMode = WalkSpeedKind

data class WalkSpeedProfile private constructor(
    val kind: WalkSpeedKind,
    val preset: WalkSpeedPreset,
    val minimumKmh: Double,
    val maximumKmh: Double,
) {
    init {
        require(minimumKmh.isFinite() && maximumKmh.isFinite())
        require(minimumKmh in MIN_KMH..MAX_KMH && maximumKmh in MIN_KMH..MAX_KMH)
        require(minimumKmh <= maximumKmh)
    }

    val mode: WalkSpeedMode get() = kind
    val fixedKmh: Double get() = minimumKmh
    val minKmh: Double get() = minimumKmh
    val maxKmh: Double get() = maximumKmh
    val estimatedKmh: Double get() = (minimumKmh + maximumKmh) / 2.0
    val nominalMps: Double get() = estimatedKmh / 3.6
    val display: String get() = when (kind) {
        WalkSpeedKind.PRESET -> preset.display
        WalkSpeedKind.FIXED -> "自訂 " + format(minimumKmh) + " km/h"
        WalkSpeedKind.RANGE ->
            "區間 " + format(minimumKmh) + "–" + format(maximumKmh) + " km/h"
    }
    fun sampleKmh(random: Random = Random.Default): Double =
        if (minimumKmh == maximumKmh) minimumKmh
        else random.nextDouble(minimumKmh, maximumKmh)

    companion object {
        const val MIN_KMH = 0.8
        const val MAX_KMH = 20.0
        fun preset(value: WalkSpeedPreset) =
            WalkSpeedProfile(WalkSpeedKind.PRESET, value,
                value.kilometresPerHour, value.kilometresPerHour)
        fun fixed(kmh: Double, preset: WalkSpeedPreset = WalkSpeedPreset.DEFAULT) =
            WalkSpeedProfile(WalkSpeedKind.FIXED, preset, kmh, kmh)
        fun range(minimumKmh: Double, maximumKmh: Double,
                  preset: WalkSpeedPreset = WalkSpeedPreset.DEFAULT): WalkSpeedProfile {
            require(minimumKmh < maximumKmh) { "區間最小速度必須小於最大速度" }
            return WalkSpeedProfile(WalkSpeedKind.RANGE, preset, minimumKmh, maximumKmh)
        }
        fun tryFixed(raw: String): WalkSpeedProfile? =
            raw.trim().toDoubleOrNull()?.let { runCatching { fixed(it) }.getOrNull() }
        fun tryRange(low: String, high: String): WalkSpeedProfile? {
            val min = low.trim().toDoubleOrNull() ?: return null
            val max = high.trim().toDoubleOrNull() ?: return null
            return runCatching { range(min, max) }.getOrNull()
        }
        fun number(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString()
            else java.lang.String.format(java.util.Locale.US, "%.1f", value)
        private fun format(value: Double): String = number(value)
    }
}

/**
 * Range is re-sampled at each selected stop boundary and loop restart.
 * A new speed applies to the next moving interval, never a paused interval.
 */
class WalkSpeedPlayback(
    initial: WalkSpeedProfile,
    private val random: Random = Random.Default,
) {
    var profile: WalkSpeedProfile = initial
        private set
    var appliedKmh: Double = initial.sampleKmh(random)
        private set

    /** A new route samples a fresh initial speed even if the mode is unchanged. */
    fun restart(next: WalkSpeedProfile) {
        profile = next
        appliedKmh = next.sampleKmh(random)
    }

    /** Re-selecting an identical profile must not silently re-roll its speed. */
    fun apply(next: WalkSpeedProfile) {
        if (profile != next) restart(next)
    }

    fun advance(
        travelledBefore: Double,
        acceptedMetres: Double,
        lengthMetres: Double,
        stopThresholds: List<Double>,
        looping: Boolean,
    ) {
        if (profile.kind != WalkSpeedKind.RANGE ||
            acceptedMetres <= 0.0 || !acceptedMetres.isFinite() ||
            lengthMetres <= 0.0 || !lengthMetres.isFinite()) return
        val boundaries = stopThresholds.filter { it > 0.1 && it < lengthMetres - 0.1 }
            .distinct().sorted()
        var remaining = acceptedMetres
        var position = if (looping) travelledBefore % lengthMetres
            else travelledBefore.coerceIn(0.0, lengthMetres)
        // Small routes may cross more than one boundary per tick.
        var iterations = 0
        while (remaining > 1e-8 && iterations++ < 10_000) {
            val take = min(remaining, lengthMetres - position)
            if (take <= 1e-8) {
                if (looping && position >= lengthMetres - 1e-8) {
                    // Rounding can leave the cursor just before the lap end.
                    position = 0.0
                    appliedKmh = profile.sampleKmh(random)
                    continue
                }
                break
            }
            val end = position + take
            for (boundary in boundaries) {
                if (boundary > position + 1e-6 && boundary <= end + 1e-6)
                    appliedKmh = profile.sampleKmh(random)
            }
            remaining -= take
            position = end
            if (looping && position >= lengthMetres - 1e-8) {
                position = 0.0
                appliedKmh = profile.sampleKmh(random)
            } else break
        }
    }
}
