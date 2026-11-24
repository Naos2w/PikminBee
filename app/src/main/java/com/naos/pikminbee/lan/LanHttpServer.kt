package com.naos.pikminbee.lan

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class LanRequest(
    val method: String,
    val path: String,
    val body: String,
    val token: String?,
    val pendingId: String?,
    val remoteAddress: String,
)

data class LanReply(
    val status: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8",
    val binaryBody: ByteArray? = null,
) {
    fun payloadBytes(): ByteArray = binaryBody ?: body.toByteArray(StandardCharsets.UTF_8)
}

/**
 * Tiny bounded, single-request HTTP/1.1 server for an explicitly enabled private Wi-Fi IP.
 * No remote upload, no chunked encoding, no proxy, no wildcard bind, no WebSocket yet.
 */
class LanHttpServer(
    private val localIp: Inet4Address,
    private val port: Int = 8765,
    private val handler: (LanRequest) -> LanReply,
) {
    private val running = AtomicBoolean(false)
    private val workers = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(16),
        { task -> Thread(task, "PikminBee-LAN-worker").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    @Volatile private var server: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null

    fun start() {
        check(!running.get()) { "LAN server is already running" }
        check(localIp.isSiteLocalAddress) { "LAN listener needs a private IPv4 address" }
        val socket = ServerSocket(port, 16, localIp)
        socket.soTimeout = 1_500
        server = socket
        running.set(true)
        acceptThread = Thread({
            while (running.get()) {
                try {
                    val client = socket.accept()
                    if (!client.inetAddress.isSiteLocalAddress) {
                        client.close()
                    } else {
                        try {
                            workers.execute { handle(client) }
                        } catch (_: java.util.concurrent.RejectedExecutionException) {
                            client.close()
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Periodically observe the shutdown flag.
                } catch (_: SocketException) {
                    if (running.get()) break
                } catch (_: Exception) {
                    if (running.get()) continue
                }
            }
        }, "PikminBee-LAN-accept").apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        workers.shutdownNow()
        acceptThread?.interrupt()
    }

    private fun handle(socket: Socket) {
        socket.use { peer ->
            peer.soTimeout = 4_000
            runCatching {
                val input = BufferedInputStream(peer.getInputStream())
                val first = readLine(input) ?: return
                val parts = first.split(' ')
                if (parts.size != 3 || parts[2] != "HTTP/1.1" ||
                    parts[0] !in setOf("GET", "POST") ||
                    !parts[1].startsWith("/") || parts[1].contains('?')
                ) return@runCatching write(peer, LanReply(400, "{\"error\":\"invalid request\"}"))
                val headers = HashMap<String, String>()
                var count = 0
                while (true) {
                    val line = readLine(input) ?: return@runCatching
                    if (line.isEmpty()) break
                    if (++count > 32) return@runCatching write(peer, LanReply(431, "{\"error\":\"headers too large\"}"))
                    val colon = line.indexOf(':')
                    if (colon <= 0) return@runCatching write(peer, LanReply(400, "{\"error\":\"invalid header\"}"))
                    val key = line.substring(0, colon).trim().lowercase(Locale.US)
                    if (key in headers) return@runCatching write(peer, LanReply(400, "{\"error\":\"duplicate header\"}"))
                    headers[key] = line.substring(colon + 1).trim()
                }
                val authority = "${localIp.hostAddress}:$port"
                val rejection = LanHttpPolicy.rejectRequest(parts[0], authority, headers)
                if (rejection != null) {
                    return@runCatching write(peer,
                        LanReply(rejection, "{\"error\":\"invalid request headers\"}"))
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(bytes, read, length - read)
                    if (n < 0) return@runCatching
                    read += n
                }
                val request = LanRequest(
                    method = parts[0],
                    path = parts[1],
                    body = bytes.toString(StandardCharsets.UTF_8),
                    token = headers["x-pikminbee-token"],
                    pendingId = headers["x-pikminbee-pending"],
                    remoteAddress = peer.inetAddress.hostAddress.orEmpty(),
                )
                val answer = handler(request)
                write(peer, answer)
            }.onFailure {
                runCatching { write(peer, LanReply(500, "{\"error\":\"internal error\"}")) }
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = StringBuilder()
        while (buffer.length <= 8_192) {
            val b = input.read()
            if (b < 0) return null
            if (b == 10) return buffer.toString().removeSuffix("\r")
            if (b !in 32..126 && b != 13) return null
            buffer.append(b.toChar())
        }
        return null
    }

    private fun write(peer: Socket, reply: LanReply) {
        val payload = reply.payloadBytes()
        val statusText = when (reply.status) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorised"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            415 -> "Unsupported Media Type"
            423 -> "Locked"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            else -> "Internal Server Error"
        }
        val headers = buildString {
            append("HTTP/1.1 ${reply.status} $statusText\r\n")
            append("Content-Type: ${reply.contentType}\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-Frame-Options: DENY\r\n")
            append("Content-Security-Policy: ")
            append(LanHttpPolicy.CONTENT_SECURITY_POLICY)
            append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        peer.getOutputStream().apply { write(headers); write(payload); flush() }
    }
}
