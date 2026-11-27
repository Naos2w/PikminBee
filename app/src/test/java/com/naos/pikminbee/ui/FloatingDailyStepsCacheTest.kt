package com.naos.pikminbee.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FloatingDailyStepsCacheTest {
    private val taipei = ZoneId.of("Asia/Taipei")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun key(date: String = "2026-11-23", zone: String = "Asia/Taipei") =
        FloatingDayKey(date, zone)

    @Test fun sameDateDifferentTimeZoneCannotReuseHealthConnectMidnight() {
        val cache = FloatingDailyStepsCache()
        val taiwan = key()
        val japan = key(zone = "Asia/Tokyo")
        val token = cache.begin(taiwan, 100L)!!
        assertTrue(cache.complete(token, taiwan, taiwan, 200L, 5_200L))
        assertEquals(5_200L, cache.currentValue(taiwan, 500L))
        assertNull(cache.currentValue(japan, 500L))
        assertTrue(cache.ensurePeriod(japan))
        assertNull(cache.currentValue(japan, 500L))
        val newToken = cache.begin(japan, 501L)!!
        assertFalse(cache.complete(token, taiwan, taiwan, 502L, 9_999L))
        assertTrue(cache.complete(newToken, japan, japan, 503L, 150L))
        assertEquals(150L, cache.currentValue(japan, 504L))
    }

    @Test fun actualInstantUsesTimeZoneInDayIdentity() {
        val instant = Instant.parse("2026-11-23T15:30:00Z")
        assertEquals("2026-11-23", FloatingDayKey.at(instant, taipei).date)
        assertEquals("2026-11-24", FloatingDayKey.at(instant, tokyo).date)
        assertEquals("Asia/Tokyo", FloatingDayKey.at(instant, tokyo).timeZoneId)
    }

    @Test fun midnightInvalidatesOldQueryEvenIfItCompletesLate() {
        val cache = FloatingDailyStepsCache()
        val yesterday = key("2026-11-23")
        val today = key("2026-11-24")
        val old = cache.begin(yesterday, 1_000L)!!
        cache.ensurePeriod(today)
        assertFalse(cache.complete(old, yesterday, today, 1_050L, 15_000L))
        assertNull(cache.currentValue(today, 1_051L))
        val next = cache.begin(today, 1_051L)!!
        assertTrue(cache.complete(next, today, today, 1_052L, 0L))
        assertEquals(0L, cache.currentValue(today, 1_052L))
    }

    @Test fun permissionRevocationClearsCachedValueAndAllowsSensorFallback() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val first = cache.begin(day, 10L)!!
        assertTrue(cache.complete(first, day, day, 20L, 2_500L))
        assertEquals(2_500L, cache.currentValue(day, 25L))
        // Every panel reopen forces a new permission-aware read and invalidates old HC data.
        val revalidate = cache.begin(day, 30L, force = true)!!
        assertNull(cache.currentValue(day, 31L))
        assertTrue(cache.complete(revalidate, day, day, 32L, null))
        assertNull(cache.currentValue(day, 33L))
        assertEquals("今日已記錄 200 步（手機）",
            FloatingDailyStepsPolicy.render(null, true, cache.currentValue(day, 33L), 200L))
    }

    @Test fun failureDoesNotDisplayFalseZeroAndRetriesAtBoundedInterval() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val first = cache.begin(day, 1_000L)!!
        assertTrue(cache.complete(first, day, day, 1_200L, null))
        assertNull(cache.currentValue(day, 1_201L))
        assertNull(cache.begin(day, 10_999L))
        assertNotNull(cache.begin(day, 16_000L))
    }

    @Test fun staleHealthConnectValueExpiresAfterSixtySeconds() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val token = cache.begin(day, 1L)!!
        assertTrue(cache.complete(token, day, day, 1_001L, 12_345L))
        assertEquals(12_345L, cache.currentValue(day, 61_000L))
        assertNull(cache.currentValue(day, 61_001L))
        assertNotNull(cache.begin(day, 61_002L))
    }

    @Test fun forcedReopenInvalidatesPreviousInFlightResultEvenOnSameDay() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val older = cache.begin(day, 100L)!!
        val newer = cache.begin(day, 101L, force = true)!!
        assertFalse(cache.complete(older, day, day, 102L, 200L))
        assertTrue(cache.complete(newer, day, day, 103L, 300L))
        assertEquals(300L, cache.currentValue(day, 104L))
    }

    @Test fun collapseOrClockRollbackMakesOldCacheAndCompletionInvalid() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val token = cache.begin(day, 200L)!!
        assertTrue(cache.complete(token, day, day, 250L, 900L))
        assertNull(cache.currentValue(day, 100L))
        cache.invalidate()
        assertFalse(cache.complete(token, day, day, 260L, 1_500L))
        assertNull(cache.currentValue(day, 261L))
        assertNotNull(cache.begin(day, 262L))
    }

    @Test fun invalidCountsCannotBeTreatedAsDailyTotals() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val token = cache.begin(day, 0L)!!
        assertTrue(cache.complete(token, day, day, 1L, -1L))
        assertNull(cache.currentValue(day, 2L))
        val zero = cache.begin(day, 3L, force = true)!!
        assertTrue(cache.complete(zero, day, day, 4L, 0L))
        assertEquals(0L, cache.currentValue(day, 4L))
    }

    @Test fun frequentFiveSecondUiTicksDoNotCauseFrequentHealthConnectReads() {
        val cache = FloatingDailyStepsCache()
        val day = key()
        val first = cache.begin(day, 100L)!!
        assertTrue(cache.complete(first, day, day, 1_000L, 1_100L))
        for (elapsed in 1_100L..55_000L step 5_000L) {
            assertNull(cache.begin(day, elapsed))
            assertEquals(1_100L, cache.currentValue(day, elapsed))
        }
        assertNotNull(cache.begin(day, 60_100L))
    }
}
