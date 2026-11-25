package com.naos.pikminbee.ui

import com.naos.pikminbee.lan.LanConnection
import com.naos.pikminbee.lan.LanConnectionPhase
import org.junit.Assert.*
import org.junit.Test

class OverlayPanelPolicyTest {
    @Test fun detailsAreExclusiveAndMayBeClosedWithoutChangingServiceState() {
        assertEquals(OverlayDetail.SPEED, OverlayPanelPolicy.toggle(OverlayDetail.NONE, OverlayDetail.SPEED))
        assertEquals(OverlayDetail.WIFI, OverlayPanelPolicy.toggle(OverlayDetail.SPEED, OverlayDetail.WIFI))
        assertEquals(OverlayDetail.NONE, OverlayPanelPolicy.toggle(OverlayDetail.WIFI, OverlayDetail.WIFI))
    }

    @Test fun detailNeverRequiresAScrollToReachApproval() {
        assertEquals(OverlayDetailLayout.GRID_AND_DETAIL, OverlayPanelPolicy.layout(600, 48, 114, 200))
        assertEquals(OverlayDetailLayout.DETAIL_ONLY, OverlayPanelPolicy.layout(300, 48, 114, 200))
        assertEquals(OverlayDetailLayout.OPEN_APP, OverlayPanelPolicy.layout(210, 48, 114, 200))
        assertEquals(OverlayDetailLayout.DETAIL_ONLY, OverlayPanelPolicy.layout(600, 48, 114, 200, editing = true))
    }

    @Test fun wifiViewUsesMutuallyExclusiveConfirmedStates() {
        assertEquals(OverlayWifiView.OFF, OverlayPanelPolicy.wifiView(false, LanConnection()))
        assertEquals(OverlayWifiView.STARTING, OverlayPanelPolicy.wifiView(false, LanConnection(LanConnectionPhase.STARTING)))
        assertEquals(OverlayWifiView.PAIRING, OverlayPanelPolicy.wifiView(true, LanConnection(LanConnectionPhase.WAITING_PAIR)))
        assertEquals(OverlayWifiView.APPROVAL, OverlayPanelPolicy.wifiView(true,
            LanConnection(LanConnectionPhase.PENDING_APPROVAL, pendingDevice = "PC")))
        assertEquals(OverlayWifiView.SESSION, OverlayPanelPolicy.wifiView(true,
            LanConnection(LanConnectionPhase.CONNECTED, remainingSeconds = 10)))
        assertEquals(OverlayWifiView.AWAITING_BROWSER, OverlayPanelPolicy.wifiView(true,
            LanConnection(LanConnectionPhase.PENDING_APPROVAL, awaitingBrowser = true)))
        assertEquals(OverlayWifiView.ERROR, OverlayPanelPolicy.wifiView(false, LanConnection(LanConnectionPhase.ERROR)))
    }

    @Test fun browserEntryNeverEmbedsPinOrTokenAndRejectsNonPrivateAddresses() {
        assertEquals("http://192.168.1.12:8765/", OverlayPanelPolicy.browserPage(true, "http://192.168.1.12:8765/"))
        assertNotNull(OverlayPanelPolicy.browserPage(true, "http://10.0.0.1:8765/"))
        assertNotNull(OverlayPanelPolicy.browserPage(true, "http://172.16.0.1:8765/"))
        for (address in listOf("http://192.168.1.12:8765/?pin=123456",
            "http://192.168.1.12:8765/#token", "http://192.168.1.12:8765/private",
            "http://user@192.168.1.12:8765/", "http://8.8.8.8:8765/", "http://example.test:8765/",
            "http://192.168.1.12:8080/", "https://192.168.1.12:8765/"))
            assertNull(address, OverlayPanelPolicy.browserPage(true, address))
        assertNull(OverlayPanelPolicy.browserPage(false, "http://192.168.1.12:8765/"))
    }
}
