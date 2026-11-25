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

    @Test fun combinedApprovalGrantsOnlyTheExplicitRequestAndNeverInheritsConsent() {
        val pair = pairer()
        val peer = "192.168.1.12"
        val ticket = pair.pair(peer, pair.currentPin(), "PC").pendingId
        val id = pair.connection().approvalId
        assertTrue(id.isNotBlank())
        assertFalse(pair.poll(peer, ticket).sharePhysical)
        assertFalse(pair.approve(sharePhysical = true))
        assertFalse(pair.approve(sharePhysical = true, approvalId = "stale"))
        assertTrue(pair.approve(sharePhysical = true, approvalId = id))
        assertFalse(pair.approve(sharePhysical = false, approvalId = id))
        assertFalse(pair.poll("192.168.1.13", ticket).sharePhysical)
        val approved = pair.poll(peer, ticket)
        assertTrue(approved.sharePhysical)
        assertTrue(pair.authorised(approved.token, peer))
        assertFalse(pair.poll(peer, ticket).sharePhysical)

        val renewal = pair.requestRenewal(approved.token, peer).pendingId
        val renewalId = pair.connection().approvalId
        assertTrue(pair.connection().renewal)
        assertNotEquals(id, renewalId)
        assertFalse(pair.approve(sharePhysical = true, approvalId = id))
        assertTrue(pair.approve(sharePhysical = false, approvalId = renewalId))
        val renewed = pair.pollRenewal(peer, renewal, approved.token)
        assertEquals("approved", renewed.state)
        assertFalse(renewed.sharePhysical)
        assertFalse(pair.authorised(approved.token, peer))
    }

    @Test fun expiredOrDeniedRequestCannotTransferItsSharingChoice() {
        val pair = pairer()
        val peer = "192.168.1.12"
        val first = pair.pair(peer, pair.currentPin(), "PC").pendingId
        val staleId = pair.connection().approvalId
        assertTrue(pair.approve(sharePhysical = true, approvalId = staleId))
        time.addAndGet(91_000)
        assertEquals("invalid", pair.poll(peer, first).state)
        val next = pair.pair(peer, pair.currentPin(), "PC").pendingId
        assertFalse(pair.approve(sharePhysical = true, approvalId = staleId))
        assertTrue(pair.approve())
        assertFalse(pair.poll(peer, next).sharePhysical)
    }

    @Test fun sharedConnectionStateTracksAuthenticatedPresenceAndRevocation() {
        val pairing = pairer()
        val peer = "192.168.50.15"
        assertEquals(LanConnectionPhase.WAITING_PAIR, pairing.connection().phase)
        val ticket = pairing.pair(peer, pairing.currentPin(), "PC").pendingId
        assertEquals(LanConnectionPhase.PENDING_APPROVAL, pairing.connection().phase)
        assertTrue(pairing.approve())
        assertTrue(pairing.connection().awaitingBrowser)
        val token = pairing.poll(peer, ticket).token
        assertEquals(LanConnectionPhase.OFFLINE, pairing.connection().phase)
        assertFalse(pairing.markStatePoll(token, "192.168.50.16"))
        assertTrue(pairing.markStatePoll(token, peer))
        assertEquals(LanConnectionPhase.CONNECTED, pairing.connection().phase)
        assertEquals(7200, pairing.connection().remainingSeconds)
        time.addAndGet(LAN_ONLINE_WINDOW_MILLIS + 1)
        assertEquals(LanConnectionPhase.OFFLINE, pairing.connection().phase)
        assertTrue(pairing.markStatePoll(token, peer))
        assertEquals(LanConnectionPhase.CONNECTED, pairing.connection().phase)
        assertTrue(pairing.connection().remainingSeconds < 7200)
        pairing.disconnect()
        assertEquals(LanConnectionPhase.REVOKED, pairing.connection().phase)
        assertFalse(pairing.authorised(token, peer))
        assertTrue(pairing.connection().showPin)
        val next = pairing.pair(peer, pairing.currentPin(), "PC").pendingId
        assertTrue(pairing.approve())
        val newToken = pairing.poll(peer, next).token
        assertTrue(pairing.markStatePoll(newToken, peer))
        time.addAndGet(7_200_000L)
        assertEquals(LanConnectionPhase.EXPIRED, pairing.connection().phase)
        assertFalse(pairing.markStatePoll(newToken, peer))
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
        time.addAndGet(2L * 60L * 60_000L + 1)
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
        time.addAndGet(2L * 60L * 60_000L + 1)
        assertNull(pair.activeSessionToken())
        assertFalse(pair.authorised(tokenB, peerB))
    }

    @Test fun sessionLastsTwoHoursWithoutSlidingOnFrequentStateChecks() {
        val pair = pairer()
        val ip = "192.168.1.20"
        val ticket = pair.pair(ip, pair.currentPin(), "MSI").pendingId
        assertTrue(pair.approve())
        val token = pair.poll(ip, ticket).token
        assertEquals(7_200, pair.sessionDurationSeconds)
        assertEquals(7_200, pair.secondsRemaining(token, ip))
        repeat(5) {
            time.addAndGet(15L * 60_000L)
            assertTrue(pair.authorised(token, ip))
        }
        assertEquals(2_700, pair.secondsRemaining(token, ip))
        time.addAndGet(45L * 60_000L)
        assertFalse(pair.authorised(token, ip))
        assertEquals(0, pair.secondsRemaining(token, ip))
    }

    @Test fun renewalNeedsSamePeerAndPhoneApprovalThenRotatesToken() {
        val pair = pairer()
        val ip = "192.168.1.55"
        val ticket = pair.pair(ip, pair.currentPin(), "PC").pendingId
        assertTrue(pair.approve())
        val token = pair.poll(ip, ticket).token!!
        time.addAndGet(110L * 60_000L)
        assertNull(pair.requestRenewal(token, "192.168.1.56").pendingId)
        val renewal = pair.requestRenewal(token, ip).pendingId!!
        assertEquals("pending", pair.pollRenewal(ip, renewal, token).state)
        assertEquals("invalid", pair.pollRenewal("192.168.1.56", renewal, token).state)
        assertEquals("invalid", pair.pollRenewal(ip, renewal, "wrong-token").state)
        assertEquals("invalid", pair.poll(ip, renewal).state)
        assertEquals(600, pair.secondsRemaining(token, ip)) // no auto-extension on polls
        assertTrue(pair.approve())
        val next = pair.pollRenewal(ip, renewal, token)
        assertEquals("approved", next.state)
        assertNotEquals(token, next.token)
        assertFalse(pair.authorised(token, ip))
        assertTrue(pair.authorised(next.token, ip))
        assertEquals(7_200, pair.secondsRemaining(next.token, ip))
        assertEquals("invalid", pair.pollRenewal(ip, renewal, token).state)
    }

    @Test fun deniedExpiredAndRestartedRenewalsNeverGrantAccess() {
        val pair = pairer()
        val ip = "10.1.1.5"
        val first = pair.pair(ip, pair.currentPin(), "MSI").pendingId
        assertTrue(pair.approve())
        val token = pair.poll(ip, first).token!!
        val denied = pair.requestRenewal(token, ip).pendingId!!
        pair.deny()
        assertEquals("invalid", pair.pollRenewal(ip, denied, token).state)
        val timedOut = pair.requestRenewal(token, ip).pendingId!!
        time.addAndGet(91_000)
        assertEquals("invalid", pair.pollRenewal(ip, timedOut, token).state)
        time.addAndGet(2L * 60L * 60_000L - 91_000L - 30_000L)
        val expired = pair.requestRenewal(token, ip).pendingId!!
        time.addAndGet(31_000L) // request is fresh but the old session has expired
        assertTrue(pair.approve())
        assertEquals("invalid", pair.pollRenewal(ip, expired, token).state)
        assertNull(pair.activeSessionToken())
        val rePair = pair.pair(ip, pair.currentPin(), "MSI").pendingId!!
        assertTrue(pair.approve())
        val fresh = pair.poll(ip, rePair).token!!
        val afterRestart = pair.requestRenewal(fresh, ip).pendingId!!
        pair.rotate()
        assertEquals("invalid", pair.pollRenewal(ip, afterRestart, fresh).state)
        assertFalse(pair.authorised(fresh, ip))
    }

    @Test fun pendingRenewalBlocksOtherPairingUntilResolved() {
        val pair = pairer()
        val peer = "192.168.0.12"
        val first = pair.pair(peer, pair.currentPin(), "MSI").pendingId
        assertTrue(pair.approve())
        val token = pair.poll(peer, first).token!!
        val renewal = pair.requestRenewal(token, peer).pendingId!!
        assertTrue(pair.pair("192.168.0.99", pair.currentPin(), "Other").locked)
        assertTrue(pair.requestRenewal(token, peer).locked)
        assertEquals("pending", pair.pollRenewal(peer, renewal, token).state)
        pair.deny()
        assertTrue(pair.authorised(token, peer))
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
