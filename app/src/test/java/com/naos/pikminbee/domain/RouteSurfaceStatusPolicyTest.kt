package com.naos.pikminbee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSurfaceStatusPolicyTest {
    @Test fun completionSurvivesOverlayOpenAndCloseAcrossAllSharedSurfaces() {
        val arrived = "已抵達終點 · 原地停留"
        val onOpening = RouteSurfaceStatusPolicy.afterOverlayChange(
            arrived, WalkPhase.PARKED, WalkMode.FLOWERS, true, visible = true,
        )
        val onClosing = RouteSurfaceStatusPolicy.afterOverlayChange(
            onOpening, WalkPhase.PARKED, WalkMode.FLOWERS, true, visible = false,
        )
        // This same text feeds Android LocationStatus.display, Wi-Fi /api/state
        // "status", and the floating controller's lastStatus.
        assertEquals(arrived, onOpening)
        assertEquals(arrived, onClosing)
        assertEquals(arrived,
            RouteSurfaceStatusPolicy.parkedOverlaySummary(onClosing, WalkMode.FLOWERS))
    }

    @Test fun activeRouteAndPauseSurviveOverlayVisibilityChanges() {
        for (mode in WalkMode.entries) {
            for (phase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
                for (visible in listOf(false, true)) {
                    val realStatus = if (phase == WalkPhase.PAUSED)
                        "步行已暫停" else RouteSurfaceStatusPolicy.movingStatus(mode)
                    assertEquals(realStatus, RouteSurfaceStatusPolicy.afterOverlayChange(
                        realStatus, phase, mode, true, visible))
                }
            }
        }
    }

    @Test fun manualStopRemainsDistinctFromActualRouteArrival() {
        val stopped = "步行已停止 · 保留目前位置"
        val arrived = "已抵達終點 · 原地停留"
        val manual = RouteSurfaceStatusPolicy.parkedOverlaySummary(stopped, WalkMode.ROUTE)
        val done = RouteSurfaceStatusPolicy.parkedOverlaySummary(arrived, WalkMode.ROUTE)
        assertEquals("已停止 · 原地停留", manual)
        assertEquals(arrived, done)
        assertFalse(manual.contains("抵達"))
        assertTrue(done.contains("抵達"))
    }

    @Test fun virtualPositionParkedWithoutRouteIsNotFalselyCalledStoppedJourney() {
        assertEquals("已回到虛擬位置 · 沒有自動行走",
            RouteSurfaceStatusPolicy.parkedOverlaySummary(
                "已回到虛擬位置 · 沒有自動行走", null))
        assertEquals("系統已恢復定位 · 原地暫停",
            RouteSurfaceStatusPolicy.parkedOverlaySummary(
                "系統已恢復定位 · 原地暫停", null))
        assertEquals("已回到虛擬位置 · 沒有自動行走",
            RouteSurfaceStatusPolicy.afterOverlayChange(
                "已回到虛擬位置 · 沒有自動行走",
                WalkPhase.PARKED, null, true, visible = true))
        assertEquals("瞬移後原地停留",
            RouteSurfaceStatusPolicy.afterOverlayChange(
                "瞬移後原地停留",
                WalkPhase.PARKED, null, true, visible = false))
    }

    @Test fun onlyIdleOverlayChangesMaySetOverlaySpecificStatus() {
        assertEquals("懸浮蜜蜂已開啟 · 定位測試尚未啟動",
            RouteSurfaceStatusPolicy.afterOverlayChange(
                "真實定位", WalkPhase.IDLE, null, false, true))
        assertEquals("懸浮控制已關閉",
            RouteSurfaceStatusPolicy.afterOverlayChange(
                "待命", WalkPhase.IDLE, null, false, false))
        assertEquals("懸浮蜜蜂已開啟 · 可操作路徑",
            RouteSurfaceStatusPolicy.afterOverlayChange(
                "模擬位置停留中", WalkPhase.IDLE, null, true, true))
        assertEquals("懸浮控制已關閉 · 模擬定位保持",
            RouteSurfaceStatusPolicy.afterOverlayChange(
                "模擬位置停留中", WalkPhase.IDLE, null, true, false))
    }

    @Test fun resumedFlowerWalkDoesNotBecomeARoadWalk() {
        assertEquals("尋花步行中", RouteSurfaceStatusPolicy.movingStatus(WalkMode.FLOWERS))
        assertEquals("沿道路步行中", RouteSurfaceStatusPolicy.movingStatus(WalkMode.ROUTE))
        assertEquals("沿道路步行中", RouteSurfaceStatusPolicy.movingStatus(null))
    }

    @Test fun parkedOrClearedRouteExposesZeroAppliedSpeedAndEta() {
        val configuredKmh = 10.8
        for (phase in listOf(WalkPhase.PARKED, WalkPhase.IDLE)) {
            val actualKmh = RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
                phase, configuredKmh)
            assertEquals(0.0, actualKmh, 0.0)
            val progress = RoutePlaybackProgress.snapshot(
                50.0, 100.0, actualKmh / 3.6,
                listOf(25.0, 100.0), false,
            )
            assertEquals(0L, progress.remainingSeconds)
            // A manually stopped route has unfinished distance, not a fake arrival.
            assertTrue(progress.fraction < 1f)
        }
        assertEquals(configuredKmh, RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
            WalkPhase.MOVING, configuredKmh), 0.0)
        // While paused the value is the configured *resume* pace.
        assertEquals(configuredKmh, RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
            WalkPhase.PAUSED, configuredKmh), 0.0)
        assertEquals(0.0, RouteSurfaceStatusPolicy.visibleRouteSpeedKmh(
            WalkPhase.MOVING, Double.NaN), 0.0)
    }

    @Test fun staleCompletionNotificationClearsOnlyForActualJourneyChanges() {
        val clear = setOf(
            RouteLifecycleChange.START,
            RouteLifecycleChange.MANUAL_STOP,
            RouteLifecycleChange.CLEAR,
            RouteLifecycleChange.TELEPORT,
            RouteLifecycleChange.RESTORE_REAL_GPS,
            RouteLifecycleChange.RESTORE_VIRTUAL_GPS,
        )
        for (event in RouteLifecycleChange.entries) {
            assertEquals(event.name, event in clear,
                RouteSurfaceStatusPolicy.clearsPreviousArrival(event))
        }
    }

    @Test fun aSecondRouteMustNotInheritAnEarlierCompletionNotice() {
        assertTrue(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PARKED, false, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.PARKED, WalkPhase.PARKED, false, true))
        // Re-start is an explicit new journey and resets the one-shot guard.
        assertTrue(RouteSurfaceStatusPolicy.clearsPreviousArrival(RouteLifecycleChange.START))
        assertTrue(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PARKED, false, false))
    }

    @Test fun pausingLoopingAndManualStoppingNeverFireArrivalAlert() {
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PAUSED, false, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.PAUSED, WalkPhase.MOVING, false, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.MOVING, WalkPhase.PARKED, true, false))
        assertFalse(RouteCompletionAlertPolicy.shouldAlert(
            WalkPhase.PAUSED, WalkPhase.PARKED, false, false))
    }

    @Test fun overlayTogglingDoesNotDismissAnActualCompletionNotice() {
        assertFalse(RouteSurfaceStatusPolicy.clearsPreviousArrival(
            RouteLifecycleChange.SHOW_OVERLAY))
        assertFalse(RouteSurfaceStatusPolicy.clearsPreviousArrival(
            RouteLifecycleChange.HIDE_OVERLAY))
        assertFalse(RouteSurfaceStatusPolicy.clearsPreviousArrival(
            RouteLifecycleChange.ARRIVE))
    }
}
