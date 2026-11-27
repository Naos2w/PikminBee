package com.naos.pikminbee.ui

import com.naos.pikminbee.health.RealStepSource
import org.junit.Assert.*
import org.junit.Test

class HealthDashboardPolicyTest {
    @Test fun confirmedHcIsNeverAddedToLocalSensorOrRouteEstimate() {
        val metric = HealthDashboardPolicy.dailyMetric(8426, 4200, RealStepSource.PHONE_STEP_SENSOR)
        assertEquals(8426L, metric.count)
        assertEquals("Health Connect 彙總", metric.sourceLabel)
        assertEquals(0L, HealthDashboardPolicy.dailyMetric(0, 4200, RealStepSource.PHONE_STEP_SENSOR).count)
    }
    @Test fun sensorFallbackIsExplicitlyLocalAndUnknownIsNotZero() {
        val local = HealthDashboardPolicy.dailyMetric(null, 4200, RealStepSource.PHONE_STEP_SENSOR)
        assertEquals("本機今日步數", local.title)
        assertEquals(4200L, local.count)
        assertNull(HealthDashboardPolicy.dailyMetric(null, 4200, RealStepSource.HC_ON_DEVICE).count)
        assertNull(HealthDashboardPolicy.dailyMetric(null, null, RealStepSource.UNAVAILABLE).count)
    }
    @Test fun syncStatusDoesNotClaimSuccessWithoutConsentOrPermission() {
        val phone = RealStepSource.PHONE_STEP_SENSOR
        assertEquals("尚未授權寫入", HealthDashboardPolicy.syncLabel(phone, true, false, true, 0, false))
        assertEquals("已關閉自動同步", HealthDashboardPolicy.syncLabel(phone, false, true, true, 0, false))
        assertEquals("待同步 3 筆", HealthDashboardPolicy.syncLabel(phone, true, true, true, 3, false))
        assertEquals("已開啟 · 無待同步紀錄", HealthDashboardPolicy.syncLabel(phone, true, true, true, 0, false))
        assertEquals("系統原生計步，不重複寫入", HealthDashboardPolicy.syncLabel(RealStepSource.HC_ON_DEVICE,true,true,true,0,false))
    }
}
