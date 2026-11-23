package com.naos.pikminbee.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomCaptureDiagnosticPolicyTest {
    @Test fun metadataDistinguishesWaitingFromVisibleAndHiddenCapture() {
        assertEquals("已分析 0 張影格 · 分享區域狀態未回報",
            BloomCaptureDiagnosticPolicy.label(0L, null))
        assertEquals("已分析 2 張影格 · 分享區域可見",
            BloomCaptureDiagnosticPolicy.label(2L, true))
        assertEquals("已分析 3 張影格 · 分享區域目前不可見",
            BloomCaptureDiagnosticPolicy.label(3L, false))
    }

    @Test fun hiddenSharedAppCannotBeMisreportedAsProtectedPixels() {
        val blank = BloomDetector.Result(100, 200, emptyList(), true,
            "畫面幾乎全黑；可能尚未準備好或內容受保護")
        val message = BloomCaptureDiagnosticPolicy.frameMessage(blank, false)
        assertTrue(message.contains("分享"))
        assertFalse(message.contains("受保護"))
        assertEquals(blank.note, BloomCaptureDiagnosticPolicy.frameMessage(blank, true))
    }

    @Test fun visibleNonblankFrameReportsCandidatesWithoutClaimingAccuracy() {
        val frame = BloomDetector.Result(240, 420, emptyList(), false, "未驗證")
        val message = BloomCaptureDiagnosticPolicy.frameMessage(frame, true)
        assertTrue(message.contains("0 個畫面候選"))
        assertTrue(message.contains("尚未驗證"))
    }
}
