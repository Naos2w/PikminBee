package com.naos.pikminbee.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.floor

/** Estimates virtual walking activity only; never claims it is measured physical steps. */
data class EstimatedStepSnapshot(
    val totalMetres: Double = 0.0,
    val estimatedSteps: Long = 0L,
    val lastSessionId: String? = null,
)

class EstimatedStepCounter(private val strideMetres: Double = 0.72) {
    init { require(strideMetres.isFinite() && strideMetres in 0.3..1.5) }
    private var metres: Double = 0.0

    fun addTravel(metresTraveled: Double): EstimatedStepSnapshot {
        require(metresTraveled.isFinite() && metresTraveled >= 0.0)
        metres += metresTraveled
        return snapshot()
    }
    fun restore(totalMetres: Double) {
        require(totalMetres.isFinite() && totalMetres >= 0.0)
        metres = totalMetres
    }
    fun snapshot(): EstimatedStepSnapshot =
        EstimatedStepSnapshot(metres, floor(metres / strideMetres).toLong())

    /** All-time totals may be restored; a new walk displays steps since its start. */
    fun estimatedSince(startSteps: Long): Long =
        (snapshot().estimatedSteps - startSteps).coerceAtLeast(0L)
}

/** One observable source for Activity and overlay; synthetic and measured steps are separate. */
object EstimatedWalkStatus {
    val state = MutableStateFlow(EstimatedStepSnapshot())
}
