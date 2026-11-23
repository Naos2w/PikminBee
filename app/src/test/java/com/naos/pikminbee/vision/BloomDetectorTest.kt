package com.naos.pikminbee.vision

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class BloomDetectorTest {
    private val grass = 0xFF659854.toInt()
    private val pink = 0xFFF25DAD.toInt()
    private val purple = 0xFFB95CF0.toInt()
    private val pale = 0xFFEEE9E3.toInt()
    private val warm = 0xFFFFC137.toInt()

    private fun solid(w: Int = 240, h: Int = 420, color: Int = grass) =
        BloomDetector.Frame(w, h, IntArray(w * h) { color })

    private fun paintCircle(
        pixels: IntArray, width: Int, height: Int,
        cx: Int, cy: Int, radius: Int, colour: Int,
    ) {
        for (y in maxOf(0,cy-radius) until minOf(height,cy+radius+1)) {
            for (x in maxOf(0,cx-radius) until minOf(width,cx+radius+1)) {
                if ((x-cx).toDouble().pow(2) + (y-cy).toDouble().pow(2) <= radius * radius) {
                    pixels[y*width+x] = colour
                }
            }
        }
    }

    @Test fun findsTwoColouredShapesInUserSuppliedSyntheticMap() {
        val frame = solid()
        paintCircle(frame.argb, frame.width, frame.height, 70,140,11,pink)
        paintCircle(frame.argb, frame.width, frame.height, 160,230,12,purple)
        val result = BloomDetector.analyse(frame)
        assertFalse(result.mostlyBlank)
        assertEquals(2,result.hits.size)
        assertTrue(result.hits.any { kotlin.math.abs(it.centreX - 70) <= 2 &&
            kotlin.math.abs(it.centreY - 140) <= 2 })
        assertTrue(result.hits.any { kotlin.math.abs(it.centreX - 160) <= 2 &&
            kotlin.math.abs(it.centreY - 230) <= 2 })
        assertTrue(result.hits.all { it.appearance == BloomDetector.Appearance.COLOURED })
        assertTrue(result.note.contains("不能自動換算 GPS"))
    }

    @Test fun palePetalsRequireWarmCentre() {
        val frame = solid()
        paintCircle(frame.argb,frame.width,frame.height,112,180,13,pale)
        paintCircle(frame.argb,frame.width,frame.height,112,180,5,warm)
        val result = BloomDetector.analyse(frame)
        assertEquals(1, result.hits.size)
        assertEquals(BloomDetector.Appearance.PALE_WITH_WARM_CENTRE, result.hits.single().appearance)
    }

    @Test fun plainGrassAndTinySpecklesAreRejected() {
        val frame = solid()
        for (i in 0 until 90) {
            val x = 5 + (i * 37) % 228
            val y = 45 + (i * 49) % 300
            paintCircle(frame.argb,frame.width,frame.height,x,y,1,pink)
        }
        val result = BloomDetector.analyse(frame)
        assertTrue(result.hits.isEmpty())
    }

    @Test fun darkThemeScreenWithSparseReadableTextIsNotAnEmptyCapture() {
        val frame = solid(color = 0xFF101010.toInt())
        // Roughly 1% bright content is enough to distinguish a genuine dark
        // Settings page from a near-uniform black MediaProjection output.
        for (row in listOf(100, 180, 260)) {
            for (y in row until row + 3) {
                for (x in 32 until 168) {
                    frame.argb[y * frame.width + x] = 0xFFE0E0E0.toInt()
                }
            }
        }
        val result = BloomDetector.analyse(frame)
        assertFalse("A dark Android UI with text is not a blank capture", result.mostlyBlank)
        assertTrue(result.hits.isEmpty())
    }

    @Test fun nearUniformBlackWithOnlyIsolatedPixelNoiseStaysBlank() {
        val frame = solid(color = 0xFF060609.toInt())
        for (i in 0 until 3) frame.argb[(80 + i) * frame.width + 50] = 0xFFFFFFFF.toInt()
        assertTrue(BloomDetector.analyse(frame).mostlyBlank)
    }

    @Test fun blackFrameReportsPossibleSecureOrNotReadyImage() {
        val result = BloomDetector.analyse(solid(color=0xFF060609.toInt()))
        assertTrue(result.mostlyBlank)
        assertTrue(result.hits.isEmpty())
        assertTrue(result.note.contains("不嘗試繞過"))
    }

    @Test fun ignoresUiLikeTopAndBottomColourBlobs() {
        val frame = solid()
        paintCircle(frame.argb,frame.width,frame.height,115,20,14,pink)
        paintCircle(frame.argb,frame.width,frame.height,115,391,14,pink)
        assertTrue(BloomDetector.analyse(frame).hits.isEmpty())
    }

    @Test fun resultDimensionsAndScoresAreBounded() {
        val frame = solid()
        paintCircle(frame.argb,frame.width,frame.height,120,160,10,pink)
        val hit = BloomDetector.analyse(frame).hits.single()
        assertEquals(frame.width,hit.width)
        assertEquals(frame.height,hit.height)
        assertTrue(hit.score in 0.0..1.0)
        assertTrue(hit.relativeX in 0.0..1.0)
        assertTrue(hit.relativeY in 0.0..1.0)
    }

    @Test fun rejectsMismatchAndUnboundedImages() {
        assertThrows(IllegalArgumentException::class.java) {
            BloomDetector.Frame(240,420,IntArray(100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BloomDetector.Frame(1000,1000,IntArray(1_000_000))
        }
    }

    @Test fun cannotRequestUnboundedNumberOfDetections() {
        assertThrows(IllegalArgumentException::class.java) {
            BloomDetector.analyse(solid(), limit=101)
        }
    }
}
