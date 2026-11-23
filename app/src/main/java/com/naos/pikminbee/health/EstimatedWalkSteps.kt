package com.naos.pikminbee.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.floor

/** Only virtual route estimates, never phone-sensor or auto-recorded exercise. */
data class EstimatedStepSnapshot(
    val totalMetres: Double = 0.0,
    val estimatedSteps: Long = 0L,
    val lastSessionId: String? = null,
)

/** Configurable only for future distance; earlier steps never get recalculated. */
object VirtualStridePolicy {
    const val DEFAULT_METRES = 0.72
    const val MIN_METRES = 0.40
    const val MAX_METRES = 1.20
    fun valid(value: Double): Boolean =
        value.isFinite() && value in MIN_METRES..MAX_METRES
    fun fromSaved(value: Double?): Double =
        value?.takeIf(::valid)?.let {
            // Normalise float-backed legacy settings without losing centimetres.
            kotlin.math.round(it * 1000.0) / 1000.0
        } ?: DEFAULT_METRES
}

class EstimatedStepCounter(initialStrideMetres: Double = VirtualStridePolicy.DEFAULT_METRES) {
    private var strideMetres: Double = initialStrideMetres
    private var metres: Double = 0.0
    private var countedSteps = 0L
    private var remainderMetres = 0.0

    init { require(strideMetres.isFinite() && strideMetres in 0.3..1.5) }

    fun setStrideForNextTravel(next: Double) {
        require(VirtualStridePolicy.valid(next))
        if (next == strideMetres) return
        // Retain fractional progress, but never rewrite already counted steps.
        remainderMetres = (remainderMetres / strideMetres).coerceIn(0.0, 0.999999) * next
        strideMetres = next
    }

    fun addTravel(metresTraveled: Double): EstimatedStepSnapshot {
        require(metresTraveled.isFinite() && metresTraveled >= 0.0)
        val outstanding = remainderMetres + metresTraveled
        // Floating point input such as 40 x 0.72 must not lose its last step.
        val extraSteps = floor(outstanding / strideMetres + 1e-9).toLong()
        countedSteps += extraSteps
        remainderMetres = (outstanding - extraSteps * strideMetres).coerceAtLeast(0.0)
        metres += metresTraveled
        return snapshot()
    }

    /**
     * Restore new snapshot accounting when available. Legacy installs only
     * persisted distance at 0.72 m/step and migrate once using that formula.
     */
    fun restore(
        totalMetres: Double,
        savedSteps: Long? = null,
        savedRemainderMetres: Double? = null,
        savedStrideMetres: Double? = null,
    ) {
        require(totalMetres.isFinite() && totalMetres >= 0.0)
        val migrated = floor(totalMetres / VirtualStridePolicy.DEFAULT_METRES + 1e-9).toLong()
        metres = totalMetres
        if (savedSteps != null && savedSteps >= 0L &&
            savedRemainderMetres != null && savedRemainderMetres.isFinite() &&
            savedStrideMetres != null && savedStrideMetres.isFinite() &&
            savedStrideMetres in 0.3..1.5 &&
            savedRemainderMetres in 0.0..savedStrideMetres) {
            countedSteps = savedSteps
            remainderMetres = savedRemainderMetres.coerceAtMost(savedStrideMetres * 0.999999)
            strideMetres = savedStrideMetres
        } else {
            countedSteps = migrated
            val rawRemainder = totalMetres - migrated * VirtualStridePolicy.DEFAULT_METRES
            remainderMetres = rawRemainder.coerceIn(0.0, VirtualStridePolicy.DEFAULT_METRES * 0.999999)
            strideMetres = VirtualStridePolicy.DEFAULT_METRES
        }
    }

    fun storedRemainder(): Double = remainderMetres
    fun storedStride(): Double = strideMetres

    fun snapshot(): EstimatedStepSnapshot =
        EstimatedStepSnapshot(metres, countedSteps)

    fun estimatedSince(startSteps: Long): Long =
        (countedSteps - startSteps).coerceAtLeast(0L)
}

object EstimatedWalkStatus {
    val state = MutableStateFlow(EstimatedStepSnapshot())
}
