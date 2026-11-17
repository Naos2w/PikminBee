package com.naos.pikminbee.lan

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Ephemeral PIN + explicit on-phone approval; bearer token is never persisted. */
class LanPairing(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    data class AuthResult(val pendingId: String? = null, val locked: Boolean = false)
    data class ApprovalStatus(val state: String, val token: String? = null)
    data class PendingDevice(val address: String, val name: String)
    private data class Attempts(var count: Int = 0, var windowStart: Long = 0L, var blockedUntil: Long = 0L)
    private data class Session(val peer: String, val expiresAt: Long)
    private data class Pending(
        val ticket: String, val peer: String, val name: String,
        val token: String, val createdAt: Long, var approved: Boolean = false,
    )

    private val attempts = ConcurrentHashMap<String, Attempts>()
    private val tokens = ConcurrentHashMap<String, Session>()
    private val tokenLifetimeMillis = 30 * 60_000L
    private val pendingLifetimeMillis = 90_000L
    private val blockMillis = 5 * 60_000L
    private val attemptWindowMillis = 60_000L
    private var pin = newPin()
    private var pending: Pending? = null

    @Synchronized fun currentPin(): String = pin
    @Synchronized fun pendingDevice(): PendingDevice? {
        clearExpiredPending()
        val pending = pending ?: return null
        return if (pending.approved) null else PendingDevice(pending.peer, pending.name)
    }

    @Synchronized fun rotate() {
        pin = newPin()
        attempts.clear()
        tokens.clear()
        pending = null
    }

    @Synchronized fun pair(remoteAddress: String, candidate: String, name: String): AuthResult {
        val now = clockMillis()
        val state = attempts.getOrPut(remoteAddress) { Attempts(windowStart = now) }
        if (now < state.blockedUntil) return AuthResult(locked = true)
        if (now - state.windowStart > attemptWindowMillis) {
            state.windowStart = now
            state.count = 0
        }
        if (state.count >= 5) {
            state.blockedUntil = now + blockMillis
            return AuthResult(locked = true)
        }
        if (!constantTimeMatch(pin, candidate)) {
            state.count++
            if (state.count >= 5) state.blockedUntil = now + blockMillis
            return AuthResult(locked = now < state.blockedUntil)
        }
        clearExpiredPending()
        if (pending != null) return AuthResult(locked = true)
        attempts.remove(remoteAddress)
        val deviceName = name.filter { it.isLetterOrDigit() || it in " -_." }.take(32).ifBlank { "網頁裝置" }
        pending = Pending(
            ticket = newSecret(18), peer = remoteAddress,
            name = deviceName, token = newSecret(32), createdAt = now,
        )
        return AuthResult(pendingId = pending?.ticket)
    }

    @Synchronized fun approve(): Boolean {
        clearExpiredPending()
        val current = pending ?: return false
        current.approved = true
        return true
    }

    @Synchronized fun deny() {
        pending = null
    }

    @Synchronized fun poll(remoteAddress: String, ticket: String?): ApprovalStatus {
        clearExpiredPending()
        val current = pending ?: return ApprovalStatus("invalid")
        if (ticket == null || !constantTimeMatch(current.ticket, ticket) || remoteAddress != current.peer) {
            return ApprovalStatus("invalid")
        }
        if (!current.approved) return ApprovalStatus("pending")
        tokens.clear() // Pairing a new browser revokes old access.
        tokens[current.token] = Session(current.peer, clockMillis() + tokenLifetimeMillis)
        pending = null
        return ApprovalStatus("approved", current.token)
    }

    fun authorised(token: String?, remoteAddress: String): Boolean {
        if (token == null || token.length !in 40..64) return false
        val session = tokens[token] ?: return false
        if (remoteAddress != session.peer || clockMillis() >= session.expiresAt) {
            if (clockMillis() >= session.expiresAt) tokens.remove(token)
            return false
        }
        return true
    }

    @Synchronized fun revoke() {
        tokens.clear()
        attempts.clear()
        pending = null
    }

    private fun clearExpiredPending() {
        if (pending?.let { clockMillis() - it.createdAt > pendingLifetimeMillis } == true) {
            pending = null
        }
    }

    private fun newPin(): String = "%06d".format(java.util.Locale.US, random.nextInt(1_000_000))
    private fun newSecret(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun constantTimeMatch(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(Charsets.US_ASCII), right.toByteArray(Charsets.US_ASCII))
}

/** Keep LAN requests bounded even for an already-paired client. */
class LanRateLimiter(private val clockMillis: () -> Long = System::currentTimeMillis) {
    private data class Counter(var startedAt: Long, var count: Int)
    private val counters = ConcurrentHashMap<String, Counter>()
    @Synchronized fun allow(ip: String): Boolean {
        val now = clockMillis()
        val counter = counters[ip]
        if (counter == null || now - counter.startedAt >= 10_000L) {
            counters[ip] = Counter(now, 1)
            return true
        }
        if (counter.count >= 45) return false
        counter.count++
        return true
    }
}
