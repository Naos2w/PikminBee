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
    private data class Session(val peer: String, val name: String, val expiresAt: Long, var lastActivityAt: Long? = null)
    private data class Pending(
        val ticket: String, val peer: String, val name: String,
        val token: String, val createdAt: Long, var approved: Boolean = false,
        val renewFromToken: String? = null,
    )

    private val attempts = ConcurrentHashMap<String, Attempts>()
    private val tokens = ConcurrentHashMap<String, Session>()
    private val tokenLifetimeMillis = 2 * 60 * 60_000L
    val sessionDurationSeconds: Int get() = (tokenLifetimeMillis / 1_000L).toInt()
    private val pendingLifetimeMillis = 90_000L
    private val blockMillis = 5 * 60_000L
    private val attemptWindowMillis = 60_000L
    private var pin = newPin()
    private var pending: Pending? = null
    private var endedPhase: LanConnectionPhase? = null

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
        endedPhase = null
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

    /** A renewal requires an already-authorised peer and a fresh phone approval.
     *  It cannot extend the previous token merely by polling /api/state. */
    @Synchronized fun requestRenewal(token: String?, remoteAddress: String): AuthResult {
        if (!authorised(token, remoteAddress)) return AuthResult()
        clearExpiredPending()
        if (pending != null) return AuthResult(locked = true)
        pending = Pending(
            ticket = newSecret(18), peer = remoteAddress,
            name = "續期請求", token = newSecret(32),
            createdAt = clockMillis(), renewFromToken = token,
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
        if (current.renewFromToken != null || ticket == null ||
            !constantTimeMatch(current.ticket, ticket) || remoteAddress != current.peer) {
            return ApprovalStatus("invalid")
        }
        if (!current.approved) return ApprovalStatus("pending")
        endedPhase = null
        tokens.clear() // Pairing a new browser revokes old access.
        tokens[current.token] = Session(current.peer, current.name, clockMillis() + tokenLifetimeMillis)
        pending = null
        return ApprovalStatus("approved", current.token)
    }

    /** Rotate the token only after the SAME authorised browser is approved on-phone. */
    @Synchronized fun pollRenewal(
        remoteAddress: String, ticket: String?, oldToken: String?,
    ): ApprovalStatus {
        clearExpiredPending()
        val current = pending ?: return ApprovalStatus("invalid")
        if (current.renewFromToken == null || ticket == null || oldToken == null ||
            !constantTimeMatch(current.ticket, ticket) ||
            !constantTimeMatch(current.renewFromToken, oldToken) ||
            remoteAddress != current.peer) return ApprovalStatus("invalid")
        if (!authorised(oldToken, remoteAddress)) {
            pending = null
            return ApprovalStatus("invalid")
        }
        if (!current.approved) return ApprovalStatus("pending")
        endedPhase = null
        val device = tokens[oldToken]?.name ?: "網頁裝置"
        tokens.clear()
        tokens[current.token] = Session(current.peer, device, clockMillis() + tokenLifetimeMillis)
        pending = null
        return ApprovalStatus("approved", current.token)
    }

    @Synchronized fun secondsRemaining(token: String?, remoteAddress: String): Int {
        if (!authorised(token, remoteAddress)) return 0
        val expiry = tokens[token]?.expiresAt ?: return 0
        return ((expiry - clockMillis()).coerceAtLeast(0L) + 999L)
            .div(1_000L).toInt()
    }

    /** Only one pairing session is active: a new browser approval revokes the old one. */
    @Synchronized fun activeSessionToken(): String? {
        val now = clockMillis()
        if (tokens.entries.removeIf { now >= it.value.expiresAt }) endedPhase = LanConnectionPhase.EXPIRED
        return tokens.keys.firstOrNull()
    }

    @Synchronized fun authorised(token: String?, remoteAddress: String): Boolean {
        if (token == null || token.length !in 40..64) return false
        val session = tokens[token] ?: return false
        if (remoteAddress != session.peer || clockMillis() >= session.expiresAt) {
            if (clockMillis() >= session.expiresAt) {
                tokens.remove(token)
                endedPhase = LanConnectionPhase.EXPIRED
            }
            return false
        }
        return true
    }

    /** Only successful authenticated state polling establishes browser presence. */
    @Synchronized fun markStatePoll(token: String?, remoteAddress: String): Boolean {
        if (!authorised(token, remoteAddress)) return false
        tokens[token]?.lastActivityAt = clockMillis()
        return true
    }

    /** Safe device-side status. Never includes token, PIN or a pending ticket. */
    @Synchronized fun connection(): LanConnection {
        clearExpiredPending()
        val token = activeSessionToken()
        val session = token?.let(tokens::get)
        val now = clockMillis()
        val seconds = session?.let { ((it.expiresAt - now).coerceAtLeast(0L) + 999L).div(1000L).toInt() } ?: 0
        val request = pending
        val phase = when {
            request != null -> LanConnectionPhase.PENDING_APPROVAL
            session == null -> endedPhase ?: LanConnectionPhase.WAITING_PAIR
            session.lastActivityAt == null -> LanConnectionPhase.OFFLINE
            now - session.lastActivityAt!! in 0L..LAN_ONLINE_WINDOW_MILLIS -> LanConnectionPhase.CONNECTED
            else -> LanConnectionPhase.OFFLINE
        }
        return LanConnection(
            phase = phase,
            remainingSeconds = seconds,
            device = session?.name.orEmpty(),
            pendingDevice = request?.takeUnless { it.approved }?.let {
                (if (it.renewFromToken != null) "續期目前裝置" else it.name) + " · " + it.peer
            }.orEmpty(),
            awaitingBrowser = request?.approved == true,
        )
    }

    @Synchronized fun disconnect() {
        tokens.clear()
        pending = null
        endedPhase = LanConnectionPhase.REVOKED
        pin = newPin()
    }

    @Synchronized fun revoke() {
        tokens.clear()
        attempts.clear()
        pending = null
        endedPhase = null
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
