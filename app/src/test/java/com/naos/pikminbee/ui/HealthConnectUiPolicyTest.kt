package com.naos.pikminbee.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectUiPolicyTest {
    @Test fun readNotGrantedDoesNotShowCachedDailyTotal() {
        assertEquals("—", HealthConnectUiPolicy.todayValue(false, 12345))
        assertEquals("12345", HealthConnectUiPolicy.todayValue(true, 12345))
        assertEquals("—", HealthConnectUiPolicy.todayValue(true, null))
    }

    @Test fun aWriteGrantNeverImpliesThatVirtualSyncWasEnabled() {
        assertEquals("未啟用", HealthConnectUiPolicy.syncLabel(
            available = true, optedIn = false, canWrite = true,
            pending = 0, lastFailed = false))
        assertEquals("等待寫入授權", HealthConnectUiPolicy.syncLabel(
            available = true, optedIn = true, canWrite = false,
            pending = 3, lastFailed = false))
    }

    @Test fun queueAndFailuresAreDisplayedWithClearPriority() {
        assertEquals("待同步 2 筆", HealthConnectUiPolicy.syncLabel(
            true, true, true, 2, false))
        assertEquals("最近同步失敗", HealthConnectUiPolicy.syncLabel(
            true, true, true, 2, true))
        assertEquals("同步已開啟", HealthConnectUiPolicy.syncLabel(
            true, true, true, 0, false))
        assertEquals("不可使用", HealthConnectUiPolicy.syncLabel(
            false, true, false, 2, false))
    }

    @Test fun manualSyncRequiresOptInGrantAndPendingQueue() {
        assertTrue(HealthConnectUiPolicy.canSyncNow(true,true,true,1))
        assertFalse(HealthConnectUiPolicy.canSyncNow(true,false,true,1))
        assertFalse(HealthConnectUiPolicy.canSyncNow(true,true,false,1))
        assertFalse(HealthConnectUiPolicy.canSyncNow(true,true,true,0))
    }
}
