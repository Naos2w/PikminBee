package com.naos.pikminbee.data

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest

/**
 * Internal versioned library snapshot candidate. No file IO, preferences,
 * permissions, draft, runtime location, tokens or device identifiers.
 * SHA-256 detects damage; it is not authentication or encryption.
 */
internal object SavedDataBackupCodec {
    private const val MAGIC = 0x4D474C42 // MGLB
    private const val VERSION = 1
    private const val DIGEST_BYTES = 32
    internal const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_PLACES = 10_000
    private const val MAX_ROUTES = 100
    private const val MAX_TEXT_BYTES = 4096

    data class Snapshot(val places: List<SavedPlace>, val routes: List<SavedRoute>)

    fun encode(snapshot: Snapshot): ByteArray {
        require(snapshot.places.size <= MAX_PLACES && snapshot.routes.size <= MAX_ROUTES)
        require(snapshot.places.map { it.id }.toSet().size == snapshot.places.size)
        require(snapshot.routes.map { it.id }.toSet().size == snapshot.routes.size)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(snapshot.places.size)
            for (place in snapshot.places) {
                requireValid(place)
                out.writeLong(place.id)
                out.writeText(place.name)
                out.writePoint(place.coordinate)
                out.writeText(place.countryCode)
                out.writeText(place.countryName)
                out.writeText(place.city)
                out.writeText(place.timeZoneId)
                out.writeLong(place.createdAtMillis)
                require(buffer.size() <= MAX_BYTES - DIGEST_BYTES)
            }
            out.writeInt(snapshot.routes.size)
            for (route in snapshot.routes) {
                require(route.createdAtMillis >= 0)
                out.writeText(route.id)
                out.writeText(route.name)
                // Stable wire values, not enum ordinals. Historical data is not activated.
                out.writeByte(when (route.mode) {
                    WalkMode.ROUTE -> 1
                    WalkMode.FLOWERS -> 2
                })
                out.writeInt(route.stops.size)
                route.stops.forEach { out.writePoint(it) }
                out.writeLong(route.createdAtMillis)
                require(buffer.size() <= MAX_BYTES - DIGEST_BYTES)
            }
        }
        val payload = buffer.toByteArray()
        require(payload.size <= MAX_BYTES - DIGEST_BYTES)
        return payload + digest(payload)
    }

    /**
     * Read an untrusted stream without depending on its declared length or available().
     * The caller owns the stream and must close it. At most MAX_BYTES + 1 bytes
     * are consumed; no partial snapshot is returned on corruption or IO failure.
     */
    fun readFrom(input: InputStream): Snapshot {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val remaining = MAX_BYTES - output.size()
            val count = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            if (count == -1) break
            if (count <= 0) throw IOException("Backup stream made no progress")
            require(count <= remaining) { "Backup exceeds capacity" }
            output.write(buffer, 0, count)
        }
        return decode(output.toByteArray())
    }

    /**
     * Validate the complete snapshot before touching a caller-owned output.
     * This format contains private coordinates and is NOT encrypted. This method
     * is for private storage; external export requires a separately reviewed
     * explicit user choice and destination. Flush/close and any atomic publish
     * are the caller's responsibility; a failed stream may contain partial bytes.
     */
    fun writeToPrivateStorage(snapshot: Snapshot, output: OutputStream) {
        val bytes = encode(snapshot)
        output.write(bytes)
    }

    /** Reject the whole snapshot on any malformed or unsupported record. */
    fun decode(bytes: ByteArray): Snapshot {
        require(bytes.size in (16 + DIGEST_BYTES)..MAX_BYTES) { "Invalid backup size" }
        val end = bytes.size - DIGEST_BYTES
        val payload = bytes.copyOfRange(0, end)
        require(MessageDigest.isEqual(digest(payload), bytes.copyOfRange(end, bytes.size))) {
            "Backup integrity check failed"
        }
        try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                require(input.readInt() == MAGIC) { "Unknown backup format" }
                require(input.readInt() == VERSION) { "Unsupported backup version" }
                val placeCount = input.readCount(0, MAX_PLACES)
                val places = List(placeCount) {
                    SavedPlace(
                        id = input.readLong(),
                        name = input.readRequiredText(),
                        coordinate = input.readPoint(),
                        countryCode = input.readText(),
                        countryName = input.readText(),
                        city = input.readText(),
                        timeZoneId = input.readText(),
                        createdAtMillis = input.readLong(),
                    ).also(::requireValid)
                }
                val routeCount = input.readCount(0, MAX_ROUTES)
                val routes = List(routeCount) {
                    val id = input.readRequiredText()
                    val name = input.readRequiredText()
                    val mode = when (input.readUnsignedByte()) {
                        1 -> WalkMode.ROUTE
                        2 -> WalkMode.FLOWERS
                        else -> throw IllegalArgumentException("Unknown route record type")
                    }
                    val stops = List(input.readCount(1, 15)) { input.readPoint() }
                    val created = input.readLong()
                    require(created >= 0)
                    SavedRoute(id, name, mode, stops, created)
                }
                require(input.available() == 0) { "Unexpected trailing backup content" }
                require(places.map { it.id }.toSet().size == places.size) { "Duplicate place ID" }
                require(routes.map { it.id }.toSet().size == routes.size) { "Duplicate route ID" }
                return Snapshot(places, routes)
            }
        } catch (_: IOException) {
            throw IllegalArgumentException("Malformed backup data")
        }
    }

    private fun requireValid(place: SavedPlace) {
        require(place.id > 0 && place.createdAtMillis >= 0)
        require(place.name.isNotBlank() && place.name.length <= 80)
    }

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun DataOutputStream.writePoint(point: Coordinate) {
        writeDouble(point.latitude)
        writeDouble(point.longitude)
    }

    private fun DataInputStream.readPoint() = Coordinate(readDouble(), readDouble())

    private fun DataInputStream.readCount(minimum: Int, maximum: Int): Int =
        readInt().also { require(it in minimum..maximum) { "Invalid backup record count" } }

    private fun DataOutputStream.writeText(value: String?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        require(value.length <= MAX_TEXT_BYTES)
        val encoded = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value))
        require(encoded.remaining() <= MAX_TEXT_BYTES)
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readRequiredText(): String =
        requireNotNull(readText()) { "Missing required backup text" }

    private fun DataInputStream.readText(): String? {
        val length = readInt()
        if (length == -1) return null
        require(length in 0..MAX_TEXT_BYTES && length <= available()) { "Invalid backup text size" }
        val bytes = ByteArray(length)
        readFully(bytes)
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    }
}
