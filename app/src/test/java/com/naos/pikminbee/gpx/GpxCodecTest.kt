package com.naos.pikminbee.gpx

import com.naos.pikminbee.data.SavedRoute
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.WalkMode
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class GpxCodecTest {
    private val first = Coordinate(25.033, 121.5654)
    private val second = Coordinate(25.035, 121.567)

    @Test fun exportAndImportKeepsFlowerModeUnicodeTitleAndCoordinates() {
        val saved = SavedRoute.create("台北 & 河濱 <採蜜>", WalkMode.FLOWERS, listOf(first, second))
        val bytes = GpxCodec.encode(saved)
        val xml = bytes.toString(Charsets.UTF_8)
        assertTrue(xml.contains("version=\"1.1\""))
        assertTrue(xml.contains("台北 &amp; 河濱 &lt;採蜜&gt;"))
        val imported = GpxCodec.decode(bytes, WalkMode.ROUTE)
        assertEquals(saved.name, imported.name)
        assertEquals(saved.mode, imported.mode)
        assertEquals(saved.stops, imported.stops)
        assertFalse(imported.simplified)
        assertEquals("rte", imported.sourceType)
    }

    @Test fun externalRouteKeepsSelectedDefaultMode() {
        val xml = gpx("<rte><name>Morning walk</name>" +
            "<rtept lat=\"25.033\" lon=\"121.5654\"/>" +
            "<rtept lat=\"25.035\" lon=\"121.567\"/></rte>")
        val imported = GpxCodec.decode(xml.toByteArray(), WalkMode.FLOWERS)
        assertEquals("Morning walk", imported.name)
        assertEquals(WalkMode.FLOWERS, imported.mode)
        assertEquals(listOf(first, second), imported.stops)
    }

    @Test fun trackWithManyPointsIsSimplifiedButPreservesEndpoints() {
        val points = (0 until 300).map { i ->
            Coordinate(25.0 + i * 0.00002, 121.0 + kotlin.math.sin(i * 0.04) * 0.001)
        }
        val xml = gpx("<trk><name>Curve</name><trkseg>" +
            points.joinToString("") {
                "<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"/>"
            } + "</trkseg></trk>")
        val imported = GpxCodec.decode(xml.toByteArray())
        assertEquals(300, imported.originalPoints)
        assertTrue(imported.simplified)
        assertTrue(imported.stops.size in 2..15)
        assertEquals(points.first(), imported.stops.first())
        assertEquals(points.last(), imported.stops.last())
        assertEquals("trk", imported.sourceType)
    }

    @Test fun trackDisconnectedSegmentsAreRejected() {
        val xml = gpx("<trk><trkseg><trkpt lat=\"25\" lon=\"121\"/></trkseg>" +
            "<trkseg><trkpt lat=\"25.1\" lon=\"121\"/></trkseg></trk>")
        assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode(xml.toByteArray())
        }
    }

    @Test fun multipleRoutesAreRejectedInsteadOfDiscardingLaterRoutes() {
        val route = "<rte><rtept lat=\"25\" lon=\"121\"/></rte>"
        assertMultipleObjectsRejected(route + route)
    }

    @Test fun multipleTracksAreRejectedInsteadOfDiscardingLaterTracks() {
        val track = "<trk><trkseg><trkpt lat=\"25\" lon=\"121\"/></trkseg></trk>"
        assertMultipleObjectsRejected(track + track)
    }

    @Test fun mixedRouteAndTrackAreRejected() {
        assertMultipleObjectsRejected("<rte><rtept lat=\"25\" lon=\"121\"/></rte>" +
            "<trk><trkseg><trkpt lat=\"25\" lon=\"121\"/></trkseg></trk>")
    }

    @Test fun standaloneWaypointsAlongsideRouteAreNotSilentlyDropped() {
        assertMultipleObjectsRejected("<wpt lat=\"26\" lon=\"122\"/>" +
            "<rte><rtept lat=\"25\" lon=\"121\"/></rte>")
    }

    @Test fun standaloneWaypointsAlongsideTrackAreNotSilentlyDropped() {
        assertMultipleObjectsRejected("<wpt lat=\"26\" lon=\"122\"/>" +
            "<trk><trkseg><trkpt lat=\"25\" lon=\"121\"/></trkseg></trk>")
    }

    private fun assertMultipleObjectsRejected(content: String) {
        val error = assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode(gpx(content).toByteArray())
        }
        assertTrue(error.message.orEmpty().contains("請先分成"))
    }

    @Test fun unknownAndOversizedInputIsRejected() {
        val invalid = "<!DOCTYPE gpx [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
            gpx("<rte><name>&x;</name><rtept lat=\"25\" lon=\"121\"/></rte>")
        assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode(invalid.toByteArray())
        }
        val large = ByteArray(GpxCodec.MAX_BYTES + 1) { 65 }
        assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode(ByteArrayInputStream(large))
        }
    }

    @Test fun invalidOrOutOfBoundsCoordinatesAreRejected() {
        for (coordinates in listOf("200" to "121", "NaN" to "121", "" to "121")) {
            val xml = gpx("<rte><rtept lat=\"${coordinates.first}\" lon=\"${coordinates.second}\"/></rte>")
            assertThrows(GpxCodec.GpxException::class.java) {
                GpxCodec.decode(xml.toByteArray())
            }
        }
    }

    @Test fun tooManyRouteStopsAreNotSilentlyTruncated() {
        val points = (1..16).joinToString("") { i ->
            "<rtept lat=\"25.${i}\" lon=\"121.${i}\"/>"
        }
        assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode(gpx("<rte>$points</rte>").toByteArray())
        }
    }

    @Test fun waypointOnlyGpxIsSupported() {
        val xml = gpx("<wpt lat=\"25.033\" lon=\"121.5654\"/>" +
            "<wpt lat=\"25.035\" lon=\"121.567\"/>")
        val imported = GpxCodec.decode(xml.toByteArray())
        assertEquals(listOf(first, second), imported.stops)
        assertEquals("wpt", imported.sourceType)
    }

    @Test fun rejectsGpxTenAndOtherXmlRoot() {
        assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode("<gpx version=\"1.0\"/>".toByteArray())
        }
        assertThrows(GpxCodec.GpxException::class.java) {
            GpxCodec.decode("<not-gpx version=\"1.1\"/>".toByteArray())
        }
    }

    @Test fun savedFileNameCannotContainPathSeparators() {
        val route = SavedRoute.create("before/after:sea?*", WalkMode.ROUTE, listOf(first))
        val name = route.fileName()
        assertTrue(name.endsWith(".gpx"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains(":"))
    }

    private fun gpx(content: String): String =
        "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">$content</gpx>"
}
