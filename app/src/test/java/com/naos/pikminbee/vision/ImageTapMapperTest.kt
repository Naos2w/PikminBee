package com.naos.pikminbee.vision

import org.junit.Assert.*
import org.junit.Test

class ImageTapMapperTest {
    @Test fun horizontalLetterboxingAndCentredMapping() {
        val fit = ImageTapMapper.fit(200, 400, 400f, 400f)!!
        assertEquals(100f, fit.left, 0.001f)
        assertEquals(0f, fit.top, 0.001f)
        assertNull(fit.toImagePixel(99f, 200f, 200, 400))
        val pixel = fit.toImagePixel(200f, 200f, 200, 400)!!
        assertEquals(100f, pixel.x, 0.001f)
        assertEquals(200f, pixel.y, 0.001f)
        assertEquals(200f, fit.toViewportPixel(100f, 200f).x, 0.001f)
    }

    @Test fun verticalLetterboxingAndCorners() {
        val fit = ImageTapMapper.fit(400, 200, 300f, 300f)!!
        assertEquals(75f, fit.top, 0.001f)
        assertNull(fit.toImagePixel(150f, 74.9f, 400, 200))
        assertNull(fit.toImagePixel(150f, 225.1f, 400, 200))
        val first = fit.toImagePixel(0f, 75f, 400, 200)!!
        assertEquals(0f, first.x, 0.001f)
        assertEquals(0f, first.y, 0.001f)
        val last = fit.toImagePixel(300f, 225f, 400, 200)!!
        assertEquals(399f, last.x, 0.001f)
        assertEquals(199f, last.y, 0.001f)
    }

    @Test fun scaledImageRetainsSubpixelAccuracy() {
        val fit = ImageTapMapper.fit(480, 850, 300f, 320f)!!
        val placed = fit.toViewportPixel(160f, 420f)
        val mapped = fit.toImagePixel(placed.x, placed.y, 480, 850)!!
        assertEquals(160f, mapped.x, 0.01f)
        assertEquals(420f, mapped.y, 0.01f)
    }

    @Test fun rejectsInvalidViewportsOrOutOfBoundsTaps() {
        assertNull(ImageTapMapper.fit(0, 400, 300f, 500f))
        assertNull(ImageTapMapper.fit(300, 400, Float.NaN, 500f))
        assertNull(ImageTapMapper.fit(300, 400, 0f, 500f))
        val fit = ImageTapMapper.fit(300, 400, 300f, 500f)!!
        assertNull(fit.toImagePixel(-1f, 150f, 300, 400))
        assertNull(fit.toImagePixel(Float.NaN, 150f, 300, 400))
        assertNull(fit.toImagePixel(150f, 150f, 0, 400))
        assertNull(fit.toImagePixel(150f, 150f, 300, 0))
    }
}
