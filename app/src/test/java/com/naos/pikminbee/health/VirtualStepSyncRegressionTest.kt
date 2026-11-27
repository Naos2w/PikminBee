package com.naos.pikminbee.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * S3: test the actual shared exporter that is used by both the Settings screen
 * and WorkManager, without accessing a real Health Connect account.
 */
class VirtualStepSyncRegressionTest {
    private fun session(number: Int): EstimatedStepSession =
        EstimatedStepSession("test-$number", 10_000L + number * 10_000L,
            15_000L + number * 10_000L, 50L)

    @Test fun drainsEntirePersistedLedgerInsteadOfStrandingAfterFiftyRecords() = runBlocking {
        val queue = (1..120).map(::session).toMutableList()
        val exported = mutableSetOf<String>()
        val outcome = VirtualStepSyncIntegrity.drainAll(
            sessions = queue.toList(),
            optedIn = { true },
            stillPending = { id -> queue.any { it.id == id } },
            insert = { exported.add(VirtualStepSyncIntegrity.clientRecordId(it.id)) },
            acknowledge = { id -> queue.removeAll { it.id == id } },
        )
        assertNull(outcome.failure)
        assertFalse(outcome.optedOut)
        assertEquals(120, outcome.acknowledged)
        assertEquals(120, exported.size)
        assertTrue(queue.isEmpty())
    }

    @Test fun optOutDuringBatchStopsEverySubsequentRecord() = runBlocking {
        val queue = (1..5).map(::session).toMutableList()
        var userAgrees = true
        val written = mutableListOf<String>()
        val outcome = VirtualStepSyncIntegrity.drain(
            sessions = queue.toList(),
            optedIn = { userAgrees },
            stillPending = { id -> queue.any { it.id == id } },
            insert = { written.add(it.id) },
            acknowledge = { id ->
                queue.removeAll { it.id == id }
                userAgrees = false
            },
        )
        assertEquals(listOf("test-1"), written)
        assertTrue(outcome.optedOut)
        assertEquals(1, outcome.acknowledged)
        assertEquals(4, queue.size)
    }

    @Test fun discardDuringSyncSkipsEntriesNoLongerPending() = runBlocking {
        val sessions = (1..3).map(::session)
        val queue = sessions.toMutableList()
        val inserted = mutableListOf<String>()
        val outcome = VirtualStepSyncIntegrity.drain(
            sessions = sessions,
            optedIn = { true },
            stillPending = { id -> queue.any { it.id == id } },
            insert = { item ->
                inserted.add(item.id)
                if (item.id == "test-1") queue.removeAll { it.id == "test-2" }
            },
            acknowledge = { id -> queue.removeAll { it.id == id } },
        )
        assertEquals(listOf("test-1", "test-3"), inserted)
        assertEquals(2, outcome.acknowledged)
        assertTrue(queue.isEmpty())
    }

    @Test fun writeSuccessAckFailureUsesSameRemoteClientIdOnRetry() = runBlocking {
        val record = session(1)
        val queue = mutableListOf(record)
        val remote = mutableSetOf<String>()
        var failAck = true
        suspend fun attempt(): VirtualStepSyncIntegrity.BatchResult =
            VirtualStepSyncIntegrity.drain(
                sessions = queue.toList(),
                optedIn = { true },
                stillPending = { id -> queue.any { it.id == id } },
                insert = { remote.add(VirtualStepSyncIntegrity.clientRecordId(it.id)) },
                acknowledge = { id ->
                    if (failAck) throw IllegalStateException("disk write failed")
                    queue.removeAll { it.id == id }
                },
            )
        val first = attempt()
        assertEquals(0, first.acknowledged)
        assertNotNull(first.failure)
        assertEquals(1, queue.size)
        assertEquals(1, remote.size)
        failAck = false
        val second = attempt()
        assertEquals(1, second.acknowledged)
        assertNull(second.failure)
        assertTrue(queue.isEmpty())
        assertEquals(setOf("pikminbee-virtual-test-1"), remote)
    }

    @Test fun anInsertFailureKeepsPendingUnacknowledged() = runBlocking {
        val queue = (1..3).map(::session).toMutableList()
        val outcome = VirtualStepSyncIntegrity.drain(
            sessions = queue.toList(),
            optedIn = { true },
            stillPending = { id -> queue.any { it.id == id } },
            insert = { if (it.id == "test-2") error("permission revoked") },
            acknowledge = { id -> queue.removeAll { it.id == id } },
        )
        assertEquals(1, outcome.acknowledged)
        assertNotNull(outcome.failure)
        assertEquals(listOf("test-2", "test-3"), queue.map { it.id })
    }

    @Test fun coroutineCancellationCannotBeSwallowedAsASyncFailure() {
        val queue = listOf(session(1))
        assertThrows(CancellationException::class.java) {
            runBlocking {
                VirtualStepSyncIntegrity.drain(
                    sessions = queue,
                    optedIn = { true },
                    stillPending = { true },
                    insert = { throw CancellationException("worker stopped") },
                    acknowledge = { },
                )
            }
        }
    }

    @Test fun sameDayAndMidnightOverlapNeverAttributeAllOfYesterdayToToday() {
        // 23:50 -> 00:10, half of the record belongs to each local day.
        val start = 1_000_000L
        val mid = start + 10 * 60_000L
        val end = mid + 10 * 60_000L
        assertEquals(50L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100L, start, end, mid, end))
        assertEquals(50L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100L, start, end, start, mid))
        assertEquals(100L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100L, start, end, start, end))
        assertEquals(0L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100L, start, end, end, end + 60_000L))
    }

    @Test fun tinyCrossMidnightCountsAreConservedInsteadOfDoubleRounded() {
        val start = 1_000_000L
        val midnight = start + 10 * 60_000L
        val end = midnight + 10 * 60_000L
        for (steps in listOf(1L, 3L, 7L, 101L)) {
            val yesterday = VirtualStepSyncIntegrity.estimatedStepsInWindow(
                steps, start, end, start, midnight)
            val today = VirtualStepSyncIntegrity.estimatedStepsInWindow(
                steps, start, end, midnight, end)
            assertEquals(steps, yesterday + today)
            assertTrue(yesterday >= 0L)
            assertTrue(today >= 0L)
        }
        assertEquals(0L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            1L, start, end, start, midnight))
        assertEquals(1L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            1L, start, end, midnight, end))
    }

    @Test fun threeConsecutiveDayWindowsPreserveTheOriginalEstimateTotal() {
        val start = 1_000L
        val one = 2_000L
        val two = 3_000L
        val end = 4_000L
        val dayOne = VirtualStepSyncIntegrity.estimatedStepsInWindow(
            7L, start, end, start, one)
        val dayTwo = VirtualStepSyncIntegrity.estimatedStepsInWindow(
            7L, start, end, one, two)
        val dayThree = VirtualStepSyncIntegrity.estimatedStepsInWindow(
            7L, start, end, two, end)
        assertEquals(2L, dayOne)
        assertEquals(2L, dayTwo)
        assertEquals(3L, dayThree)
        assertEquals(7L, dayOne + dayTwo + dayThree)
    }

    @Test fun malformedOneLegacySessionDoesNotEraseOthers() {
        val old = EstimatedStepSession("legitimate-one", 1_000, 2_000, 20)
        val next = EstimatedStepSession("legitimate-two", 3_000, 4_000, 40)
        val json = "[{" +
            "\"id\":\"legitimate-one\",\"start\":1000,\"end\":2000,\"steps\":20," +
            "\"synced\":false},\"corrupt-entry\",{" +
            "\"id\":\"legitimate-two\",\"start\":3000,\"end\":4000,\"steps\":40," +
            "\"synced\":false}]"
        assertEquals(listOf(old, next), EstimatedStepSessionCodec.decode(json))
    }
}
