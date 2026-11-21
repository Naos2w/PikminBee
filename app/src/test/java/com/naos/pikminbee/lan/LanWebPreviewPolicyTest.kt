package com.naos.pikminbee.lan

import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.PreparedWalkingRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class LanWebPreviewPolicyTest {
    private val origin = Coordinate(25.045485,121.440552)
    private val destination = Coordinate(25.046485,121.441552)
    private val other = Coordinate(25.047485,121.442552)
    private fun prepared(snap: Double = 0.0, loop: Boolean = false) =
        PreparedWalkingRoute(origin,listOf(destination),listOf(origin,destination),
            loop=loop,snapMetres=listOf(snap,0.0))
    private fun check(
        p: PreparedWalkingRoute? = prepared(),
        requested: String? = "preview-1",
        stops: List<Coordinate> = listOf(destination),
        loop: Boolean = false,
        ack: Boolean = false,
        used: String? = null,
    ) = LanWebPreviewPolicy.canStart(
        p,"preview-1",requested,origin,stops,loop,ack,used)

    @Test fun requiresMatchingPreparedPreviewBeforeGpsStarts() {
        assertEquals(LanWebPreviewPolicy.StartCheck.MISSING,check(p=null))
        assertEquals(LanWebPreviewPolicy.StartCheck.MISSING,check(requested=null))
        assertEquals(LanWebPreviewPolicy.StartCheck.MISSING,check(requested="old-preview"))
        assertEquals(LanWebPreviewPolicy.StartCheck.READY,check())
    }
    @Test fun changedRouteOrLoopInvalidatesOldPreview() {
        assertEquals(LanWebPreviewPolicy.StartCheck.STALE,check(stops=listOf(other)))
        assertEquals(LanWebPreviewPolicy.StartCheck.STALE,check(loop=true))
        assertEquals(LanWebPreviewPolicy.StartCheck.READY,check(p=prepared(loop=true),loop=true))
    }
    @Test fun largeRoadSnapNeedsSeparateExplicitAcknowledgement() {
        assertEquals(LanWebPreviewPolicy.StartCheck.CONFIRM_SNAP,
            check(p=prepared(68.0),ack=false))
        assertEquals(LanWebPreviewPolicy.StartCheck.READY,
            check(p=prepared(68.0),ack=true))
    }
    @Test fun duplicateStartForOnePreviewIsRejected() {
        assertEquals(LanWebPreviewPolicy.StartCheck.ALREADY_USED,check(used="preview-1"))
    }
}
