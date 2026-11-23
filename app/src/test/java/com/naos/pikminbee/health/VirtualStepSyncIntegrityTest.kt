package com.naos.pikminbee.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VirtualStepSyncIntegrityTest {
    private fun session(id: String, steps: Long = 100L) =
        EstimatedStepSession(id, 1_000L, 61_000L, steps)

    @Test fun doesNotStartWhenOptInIsOff() = runBlocking {
        var writes = 0
        val result = VirtualStepSyncIntegrity.drain(
            sessions = listOf(session("a")),
            optedIn = { false },
            stillPending = { true },
            insert = { writes++ },
            acknowledge = {},
        )
        assertEquals(0, writes)
        assertTrue(result.optedOut)
        assertEquals(0, result.acknowledged)
    }

    @Test fun turningOffSyncDuringBatchStopsBeforeTheNextRemoteWrite() = runBlocking {
        var optedIn = true
        val uploaded = mutableListOf<String>()
        val acknowledged = mutableListOf<String>()
        val result = VirtualStepSyncIntegrity.drain(
            sessions = listOf(session("a"), session("b"), session("c")),
            optedIn = { optedIn },
            stillPending = { true },
            insert = {
                uploaded += it.id
                if (it.id == "a") optedIn = false
            },
            acknowledge = { acknowledged += it },
        )
        assertEquals(listOf("a"), uploaded)
        assertEquals(listOf("a"), acknowledged)
        assertTrue(result.optedOut)
        assertEquals(1, result.acknowledged)
    }

    @Test fun userDiscardedQueueItemIsNotWrittenFromStaleBatchSnapshot() = runBlocking {
        val remaining = mutableSetOf("a", "b", "c")
        val uploaded = mutableListOf<String>()
        val result = VirtualStepSyncIntegrity.drain(
            sessions = listOf(session("a"), session("b"), session("c")),
            optedIn = { true },
            stillPending = { it in remaining },
            insert = {
                uploaded += it.id
                if (it.id == "a") remaining.remove("b")
            },
            acknowledge = { remaining.remove(it) },
        )
        assertEquals(listOf("a", "c"), uploaded)
        assertEquals(2, result.acknowledged)
        assertTrue(remaining.isEmpty())
    }

    @Test fun failedLocalAcknowledgementCanRetryWithExactlyTheSameClientRecordId() = runBlocking {
        var failAcknowledge = true
        val pending = mutableSetOf("record-1")
        val remoteByClientId = mutableMapOf<String, Long>()
        suspend fun retryOnce(): VirtualStepSyncIntegrity.BatchResult =
            VirtualStepSyncIntegrity.drain(
                sessions = listOf(session("record-1", 347L)),
                optedIn = { true },
                stillPending = { it in pending },
                insert = {
                    remoteByClientId[VirtualStepSyncIntegrity.clientRecordId(it.id)] = it.steps
                },
                acknowledge = {
                    if (failAcknowledge) throw IllegalStateException("disk ack failed")
                    pending.remove(it)
                },
            )
        val first = retryOnce()
        assertEquals(0, first.acknowledged)
        assertEquals("disk ack failed", first.failure)
        assertEquals(setOf("record-1"), pending)
        failAcknowledge = false
        val second = retryOnce()
        assertEquals(1, second.acknowledged)
        assertNull(second.failure)
        assertTrue(pending.isEmpty())
        assertEquals(mapOf("pikminbee-virtual-record-1" to 347L), remoteByClientId)
    }

    @Test fun writeFailureLeavesRemainingQueuePendingAndStopsBatch() = runBlocking {
        val pending = mutableSetOf("first", "second", "third")
        val result = VirtualStepSyncIntegrity.drain(
            sessions = listOf(session("first"), session("second"), session("third")),
            optedIn = { true },
            stillPending = { it in pending },
            insert = { if (it.id == "second") error("permission revoked") },
            acknowledge = { pending.remove(it) },
        )
        assertEquals(1, result.acknowledged)
        assertTrue(result.failure!!.contains("permission revoked"))
        assertEquals(setOf("second", "third"), pending)
    }

    @Test fun coroutineCancellationIsPropagatedNotSwallowedAsAnExportFailure() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                VirtualStepSyncIntegrity.drain(
                    sessions = listOf(session("a")),
                    optedIn = { true },
                    stillPending = { true },
                    insert = { throw CancellationException("worker stopped") },
                    acknowledge = {},
                )
            }
        }
    }

    @Test fun exportDrainsTheFullFiveHundredRecordLedgerButStaysBounded() = runBlocking {
        val entries = (1..501).map { session("id-$it") }
        var uploaded = 0
        val result = VirtualStepSyncIntegrity.drainAll(
            sessions = entries,
            optedIn = { true },
            stillPending = { true },
            insert = { uploaded++ },
            acknowledge = {},
        )
        assertEquals(VirtualStepSyncIntegrity.MAX_DRAIN_SESSIONS, uploaded)
        assertEquals(500, result.acknowledged)
    }

    @Test fun crossMidnightVirtualRecordDoesNotCountItsEntireValueTwice() {
        val zone = ZoneId.of("Asia/Taipei")
        val midnight = LocalDate.of(2026, 11, 24).atStartOfDay(zone).toInstant().toEpochMilli()
        val from = midnight - 120_000L
        val to = midnight + 120_000L
        val yesterday = VirtualStepSyncIntegrity.estimatedStepsInWindow(
            240L, from, to, from, midnight)
        val today = VirtualStepSyncIntegrity.estimatedStepsInWindow(
            240L, from, to, midnight, to)
        assertEquals(120L, yesterday)
        assertEquals(120L, today)
        assertEquals(240L, yesterday + today)
    }

    @Test fun readRecordsOverlappingPreviousDayIsTimeProratedAndClamped() {
        val from = Instant.parse("2026-11-23T23:58:00Z").toEpochMilli()
        val to = Instant.parse("2026-11-24T00:02:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-11-24T00:00:00Z").toEpochMilli()
        assertEquals(50L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100, from, to, midnight, to))
        assertEquals(0L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100, from, to, to, to + 60_000L))
        assertEquals(100L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100, from, to, from - 60_000L, to + 60_000L))
        assertEquals(0L, VirtualStepSyncIntegrity.estimatedStepsInWindow(
            100, to, from, from, to))
    }

    @Test fun noAutomaticSensorProvenanceAndInvalidZeroLengthSessionsRejected() {
        assertEquals("pikminbee-virtual-test", VirtualStepSyncIntegrity.clientRecordId("test"))
        assertThrows(IllegalArgumentException::class.java) {
            EstimatedStepSession("bad", 1_000L, 1_000L, 50L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStepSyncIntegrity.clientRecordId("")
        }
    }
}
