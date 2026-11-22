package com.naos.pikminbee.lan

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class LanPairingTest {
    private val time = AtomicLong(1_000L)
    private fun pairer() = LanPairing(clockMillis = { time.get() })

    @Test fun pairingRequiresExplicitPhoneApprovalBeforeTokenIsIssued() {
        val pairing = pairer()
        val ip = "192.168.50.15"
        val request = pairing.pair(ip, pairing.currentPin(), "MSI-Desktop")
        assertNotNull(request.pendingId)
        assertEquals("MSI-Desktop", pairing.pendingDevice()?.name)
        assertEquals("pending", pairing.poll(ip, request.pendingId).state)
        assertFalse(pairing.authorised(request.pendingId, ip))
        assertTrue(pairing.approve())
        val approved = pairing.poll(ip, request.pendingId)
        assertEquals("approved", approved.state)
        assertNotNull(approved.token)
        assertTrue(pairing.authorised(approved.token, ip))
        assertFalse(pairing.authorised(approved.token, "192.168.50.16"))
        assertEquals("invalid", pairing.poll(ip, request.pendingId).state)
    }

    @Test fun wrongPinLocksOutAfterFiveTries() {
        val pairing = pairer()
        val wrong = if (pairing.currentPin() == "000000") "999999" else "000000"
        repeat(4) { assertNull(pairing.pair("192.168.0.9", wrong, "attacker").pendingId) }
        assertTrue(pairing.pair("192.168.0.9", wrong, "attacker").locked)
        assertTrue(pairing.pair("192.168.0.9", pairing.currentPin(), "attacker").locked)
    }

    @Test fun anotherDeviceCannotStealPendingApproval() {
        val pairing = pairer()
        val request = pairing.pair("10.0.0.5", pairing.currentPin(), "<danger>")
        assertFalse(pairing.pendingDevice()!!.name.contains("<"))
        assertEquals("invalid", pairing.poll("10.0.0.6", request.pendingId).state)
        assertTrue(pairing.approve())
        assertEquals("invalid", pairing.poll("10.0.0.6", request.pendingId).state)
        assertTrue(pairing.authorised(pairing.poll("10.0.0.5", request.pendingId).token, "10.0.0.5"))
    }

    @Test fun deniedAndExpiredRequestsNeverIssueTokens() {
        val pairing = pairer()
        val one = pairing.pair("10.0.0.1", pairing.currentPin(), "PC")
        pairing.deny()
        assertEquals("invalid", pairing.poll("10.0.0.1", one.pendingId).state)
        val two = pairing.pair("10.0.0.1", pairing.currentPin(), "PC")
        time.addAndGet(91_000)
        assertFalse(pairing.approve())
        assertEquals("invalid", pairing.poll("10.0.0.1", two.pendingId).state)
    }

    @Test fun tokensExpireAndRestartRevokesAllClients() {
        val pairing = pairer()
        val ip = "192.168.1.55"
        val ticket = pairing.pair(ip, pairing.currentPin(), "tablet").pendingId
        assertTrue(pairing.approve())
        val token = pairing.poll(ip, ticket).token
        assertTrue(pairing.authorised(token, ip))
        time.addAndGet(30L * 60_000L + 1)
        assertFalse(pairing.authorised(token, ip))
        val next = pairing.pair(ip, pairing.currentPin(), "tablet").pendingId
        assertTrue(pairing.approve())
        val nextToken = pairing.poll(ip, next).token
        pairing.rotate()
        assertFalse(pairing.authorised(nextToken, ip))
    }

    @Test fun physicalShareSessionIsExactlyOneUnexpiredBrowserAndNewPairingInvalidatesOld() {
        val pair = pairer()
        val peerA = "192.168.1.12"
        val first = pair.pair(peerA, pair.currentPin(), "A").pendingId
        assertNull(pair.activeSessionToken())
        assertTrue(pair.approve())
        val tokenA = pair.poll(peerA, first).token!!
        assertEquals(tokenA, pair.activeSessionToken())
        val peerB = "192.168.1.13"
        val second = pair.pair(peerB, pair.currentPin(), "B").pendingId
        assertTrue(pair.approve())
        val tokenB = pair.poll(peerB, second).token!!
        assertEquals(tokenB, pair.activeSessionToken())
        assertFalse(pair.authorised(tokenA, peerA))
        time.addAndGet(30L * 60_000L + 1)
        assertNull(pair.activeSessionToken())
        assertFalse(pair.authorised(tokenB, peerB))
    }

    @Test fun rateLimiterBlocksFloodAndResetsNextWindow() {
        val limiter = LanRateLimiter(clockMillis = { time.get() })
        repeat(45) { assertTrue(limiter.allow("192.168.0.10")) }
        assertFalse(limiter.allow("192.168.0.10"))
        assertTrue(limiter.allow("192.168.0.11"))
        time.addAndGet(10_000L)
        assertTrue(limiter.allow("192.168.0.10"))
    }
}
