package com.naos.pikminbee.health

enum class RealStepSource {
    HC_ON_DEVICE, PHONE_STEP_SENSOR, UNAVAILABLE
}

object RealStepSourcePolicy {
    fun select(nativeHcAvailable: Boolean, hasPhoneStepCounter: Boolean): RealStepSource =
        when {
            nativeHcAvailable -> RealStepSource.HC_ON_DEVICE
            hasPhoneStepCounter -> RealStepSource.PHONE_STEP_SENSOR
            else -> RealStepSource.UNAVAILABLE
        }

    /** Real sensor export is only needed on devices lacking HC native recording. */
    fun mayExportSensor(nativeHcAvailable: Boolean, sensorOptIn: Boolean, writePermission: Boolean): Boolean =
        !nativeHcAvailable && sensorOptIn && writePermission
}
