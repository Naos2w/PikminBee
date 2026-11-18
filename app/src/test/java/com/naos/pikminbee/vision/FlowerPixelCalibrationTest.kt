package com.naos.pikminbee.vision

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class FlowerPixelCalibrationTest {
    private val metresPerDegree = 111_319.49079327357
    private val referenceLat = 25.0
    private val referenceLon = 121.0

    private fun at(x: Double, y: Double, eastMetres: Double, northMetres: Double) =
        FlowerPixelCalibration.Anchor(
            x, y,
            Coordinate(
                referenceLat + northMetres / metresPerDegree,
                referenceLon + eastMetres /
                    (metresPerDegree * cos(Math.toRadians(referenceLat))),
            ),
        )

    private fun mapAnchors(a: Double = 2.0, b: Double = 0.0) =
        listOf(50.0 to 80.0, 250.0 to 80.0, 60.0 to 350.0, 250.0 to 330.0)
            .map { (x, y) ->
                val dx = x - 150.0
                val northPixel = 200.0 - y
                at(x, y, a * dx - b * northPixel, b * dx + a * northPixel)
            }

    @Test fun identityAlignedMapHasTinyMeasuredAnchorError() {
        val fit = FlowerPixelCalibration.fit(300, 400, mapAnchors())
        assertEquals(4, fit.anchorCount)
        assertTrue(fit.trainingRmseMetres < 0.001)
        assertTrue(fit.heldOutRmseMetres < 0.001)
        val centre = fit.preview(150.0, 200.0)!!
        assertEquals(referenceLat, centre.coordinate.latitude, 0.000001)
        assertEquals(referenceLon, centre.coordinate.longitude, 0.000001)
        assertTrue(centre.insideAnchorHull)
    }

    @Test fun rotatedMapStillEstimatesCoordinatesWithoutAssumingNorthUp() {
        val fit = FlowerPixelCalibration.fit(300, 400, mapAnchors(1.3, 0.7))
        val result = fit.preview(170.0, 180.0)!!
        val dx = 20.0
        val up = 20.0
        val east = 1.3 * dx - 0.7 * up
        val north = 0.7 * dx + 1.3 * up
        assertEquals(referenceLat + north / metresPerDegree,
            result.coordinate.latitude, 0.000001)
        assertEquals(referenceLon + east /
            (metresPerDegree * cos(Math.toRadians(referenceLat))),
            result.coordinate.longitude, 0.000001)
        assertTrue(fit.heldOutRmseMetres < 0.001)
    }

    @Test fun suspiciousAnchorIncreasesIndependentHeldOutError() {
        val anchors = mapAnchors().toMutableList()
        val original = anchors.last()
        anchors[anchors.lastIndex] = at(
            original.pixelX, original.pixelY,
            245.0, -220.0,
        )
        val fit = FlowerPixelCalibration.fit(300, 400, anchors)
        assertTrue(fit.heldOutRmseMetres > 5.0)
        assertTrue(fit.worstHeldOutErrorMetres >= fit.heldOutRmseMetres)
    }

    @Test fun previewDistinguishesExtrapolationFromInRangeCoordinates() {
        val fit = FlowerPixelCalibration.fit(300, 400, mapAnchors())
        assertTrue(fit.preview(150.0, 200.0)!!.insideAnchorHull)
        assertFalse(fit.preview(15.0, 15.0)!!.insideAnchorHull)
        assertNull(fit.preview(-1.0, 200.0))
        assertNull(fit.preview(300.0, 200.0))
        assertNull(fit.preview(Double.NaN, 200.0))
    }

    @Test fun rejectsMissingCoincidentAndCollinearReferencePoints() {
        assertThrows(IllegalArgumentException::class.java) {
            FlowerPixelCalibration.fit(300, 400, mapAnchors().take(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlowerPixelCalibration.fit(
                300, 400,
                listOf(at(50.0, 50.0, 0.0, 0.0),
                    at(80.0, 80.0, 30.0, 30.0),
                    at(110.0, 110.0, 60.0, 60.0)),
            )
        }
    }

    @Test fun identicalDetectionResultsStillGetDistinctDocumentRevisions() {
        val before = BloomVisionState.newDocumentRevision()
        val after = BloomVisionState.newDocumentRevision()
        assertTrue(after > before)
        val result = BloomDetector.Result(300, 400, emptyList(), false, "候選為零")
        assertNotEquals(
            BloomVisionState.Snapshot(result = result, documentRevision = before),
            BloomVisionState.Snapshot(result = result, documentRevision = after),
        )
    }

    @Test fun rejectsImageMismatchAndUnsafeGeography() {
        assertThrows(IllegalArgumentException::class.java) {
            FlowerPixelCalibration.fit(1000, 1000, mapAnchors())
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlowerPixelCalibration.fit(
                300, 400, mapAnchors().mapIndexed { i, a ->
                    if (i == 0) a.copy(pixelX = 999.0) else a
                },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlowerPixelCalibration.fit(
                300, 400,
                listOf(
                    FlowerPixelCalibration.Anchor(50.0, 80.0, Coordinate(25.0, 179.99)),
                    FlowerPixelCalibration.Anchor(250.0, 80.0, Coordinate(25.0, -179.99)),
                    FlowerPixelCalibration.Anchor(60.0, 350.0, Coordinate(25.001, 179.98)),
                ),
            )
        }
    }
}
