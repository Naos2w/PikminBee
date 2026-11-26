package com.naos.pikminbee.gpx

import com.naos.pikminbee.data.SavedRoute
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkingGeometry
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.TreeSet
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.cos
import java.util.Locale

data class GpxImport(
    val name: String,
    val mode: WalkMode,
    val stops: List<Coordinate>,
    val originalPoints: Int,
    val sourceType: String,
) {
    val simplified: Boolean get() = stops.size < originalPoints
}

/** GPX 1.1 route/track waypoints only. Tracks are sampled as route stops, not replayed verbatim. */
object GpxCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_TRACK_POINTS = 5_000
    const val MAX_ROUTE_STOPS = 15

    class GpxException(message: String) : IllegalArgumentException(message)

    fun decode(stream: InputStream, defaultMode: WalkMode = WalkMode.ROUTE): GpxImport =
        decode(readBounded(stream), defaultMode)

    fun decode(bytes: ByteArray, defaultMode: WalkMode = WalkMode.ROUTE): GpxImport {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) throw GpxException("GPX 檔案空白或超過 2 MB")
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
        } catch (_: Exception) {
            throw GpxException("目前裝置無法安全解析 GPX，已拒絕此檔案")
        }
        val document = try {
            factory.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (_: Exception) {
            throw GpxException("GPX XML 格式錯誤，或包含不安全的實體／DOCTYPE")
        }
        val root = document.documentElement ?: throw GpxException("缺少 GPX 根元素")
        if (local(root) != "gpx" || root.getAttribute("version") != "1.1") {
            throw GpxException("只支援 GPX 1.1 格式")
        }

        val routes = children(root, "rte")
        val tracks = children(root, "trk")
        val waypoints = children(root, "wpt")
        // This importer produces exactly one saved route, never a partial multi-object import.
        if (routes.size > 1 || tracks.size > 1 ||
            (routes.isNotEmpty() && tracks.isNotEmpty()) ||
            (waypoints.isNotEmpty() && (routes.isNotEmpty() || tracks.isNotEmpty()))) {
            throw GpxException("此 GPX 含多組路線、軌跡或獨立地標；請先分成單一類型及單一路線的檔案再匯入")
        }
        val route = routes.singleOrNull()
        val track = tracks.singleOrNull()
        val source: Element
        val items: List<Element>
        val sourceType: String
        when {
            route != null -> {
                source = route
                items = children(route, "rtept")
                sourceType = "rte"
                if (items.size > MAX_ROUTE_STOPS) {
                    throw GpxException("GPX 路線超過 15 個路點；請先精簡路點再匯入")
                }
            }
            track != null -> {
                source = track
                // A GPX track can have disconnected segments. Never silently discard any segment.
                val segments = children(track, "trkseg")
                if (segments.size > 1) throw GpxException("此 GPX 含多段不連續軌跡，請先選取單一軌跡段")
                items = segments.singleOrNull()?.let { children(it, "trkpt") }.orEmpty()
                sourceType = "trk"
                if (items.size > MAX_TRACK_POINTS) {
                    throw GpxException("GPX 軌跡超過 5,000 個點，請先在其他工具簡化")
                }
            }
            waypoints.isNotEmpty() -> {
                source = root
                items = waypoints
                sourceType = "wpt"
                if (items.size > MAX_ROUTE_STOPS) throw GpxException("GPX 地標超過 15 個")
            }
            else -> throw GpxException("GPX 內沒有可用的路線、軌跡或地點")
        }
        if (items.isEmpty()) throw GpxException("GPX 不含有效路點")
        val points = items.mapIndexed { index, element ->
            val lat = element.getAttribute("lat").toDoubleOrNull()
            val lon = element.getAttribute("lon").toDoubleOrNull()
            if (lat == null || lon == null) throw GpxException("第 ${index + 1} 個路點缺少經緯度")
            try { Coordinate(lat, lon) } catch (_: IllegalArgumentException) {
                throw GpxException("第 ${index + 1} 個路點經緯度超出範圍")
            }
        }
        if (points.size > 1 && WalkingGeometry.lengthMeters(points) < 0.5) {
            throw GpxException("路點都在同一位置，無法形成步行路線")
        }
        val metadataName = children(root, "metadata").firstOrNull()
            ?.let { childText(it, "name") }?.trim()?.take(60).orEmpty()
        val name = childText(source, "name")?.trim()?.take(60).orEmpty()
            .ifBlank { metadataName.ifBlank { "匯入的 GPX 路線" } }
        val marker = childText(source, "type")?.lowercase().orEmpty()
        val mode = if (marker == "pikminbee:flowers") WalkMode.FLOWERS
            else if (marker == "pikminbee:route") WalkMode.ROUTE else defaultMode
        val stops = if (sourceType == "trk") simplifyTrack(points) else points
        return GpxImport(name, mode, stops, points.size, sourceType)
    }

    fun encode(route: SavedRoute): ByteArray {
        val type = if (route.mode == WalkMode.FLOWERS) "pikminbee:flowers" else "pikminbee:route"
        val text = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"PikminBee\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append("  <rte>\n    <name>").append(xmlEscape(route.name)).append("</name>\n")
            append("    <type>").append(type).append("</type>\n")
            route.stops.forEach {
                append("    <rtept lat=\"")
                    .append(String.format(Locale.US, "%.8f", it.latitude))
                    .append("\" lon=\"")
                    .append(String.format(Locale.US, "%.8f", it.longitude))
                    .append("\"/>\n")
            }
            append("  </rte>\n</gpx>\n")
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val block = ByteArray(8192)
        while (true) {
            val read = stream.read(block)
            if (read == -1) break
            if (output.size() + read > MAX_BYTES) throw GpxException("GPX 檔案超過 2 MB")
            output.write(block, 0, read)
        }
        return output.toByteArray()
    }

    private fun local(node: Node): String = node.localName ?: node.nodeName.substringAfter(':')
    private fun children(node: Element, tag: String): List<Element> =
        (0 until node.childNodes.length).mapNotNull { i ->
            (node.childNodes.item(i) as? Element)?.takeIf { local(it) == tag }
        }
    private fun childText(node: Element, tag: String): String? =
        children(node, tag).firstOrNull()?.textContent

    private fun xmlEscape(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 32 || ch == '\t' || ch == '\n' || ch == '\r') append(ch)
            }
        }
    }

    /** Preserve the endpoints and biggest turns; do not silently use a straight-line GPS route. */
    fun simplifyTrack(points: List<Coordinate>, limit: Int = MAX_ROUTE_STOPS): List<Coordinate> {
        require(limit >= 2)
        if (points.size <= limit) return points
        val picked = TreeSet<Int>().apply { add(0); add(points.lastIndex) }
        val centreLatitude = points.first().latitude
        fun distanceToLine(p: Coordinate, a: Coordinate, b: Coordinate): Double {
            val scaleX = 111_320.0 * cos(Math.toRadians(centreLatitude))
            val scaleY = 111_320.0
            val ax = a.longitude * scaleX
            val ay = a.latitude * scaleY
            val bx = b.longitude * scaleX
            val by = b.latitude * scaleY
            val px = p.longitude * scaleX
            val py = p.latitude * scaleY
            val vx = bx - ax
            val vy = by - ay
            val len2 = vx * vx + vy * vy
            val t = if (len2 < 1e-12) 0.0 else (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0.0, 1.0)
            return kotlin.math.hypot(px - ax - t * vx, py - ay - t * vy)
        }
        while (picked.size < limit) {
            var bestIndex = -1
            var largestDeviation = 1.0
            val current = picked.toList()
            for (segment in 0 until current.lastIndex) {
                val left = current[segment]
                val right = current[segment + 1]
                for (i in left + 1 until right) {
                    val deviation = distanceToLine(points[i], points[left], points[right])
                    if (deviation > largestDeviation) {
                        largestDeviation = deviation
                        bestIndex = i
                    }
                }
            }
            if (bestIndex < 0) break
            picked.add(bestIndex)
        }
        return picked.map { points[it] }
    }
}
