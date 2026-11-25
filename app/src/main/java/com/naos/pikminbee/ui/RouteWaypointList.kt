package com.naos.pikminbee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.domain.Coordinate
import kotlinx.coroutines.delay
import kotlin.math.floor

private data class WaypointDrag(val from: Int, val to: Int, val viewportY: Float)

/**
 * Editing the ordered draft is separate from planning or following a route.
 * Keep the temporary order local and commit once after release; a newer
 * draft revision or cancellation discards it.
 */
@Composable
internal fun RouteWaypointList(
    stops: List<Coordinate>,
    origin: Coordinate?,
    revision: Long,
    editable: Boolean,
    foreground: Color,
    muted: Color,
    onLocate: (Coordinate) -> Unit,
    onMove: (Long, Int, Int) -> Unit,
    onDelete: (Long, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowHeight = (48f * density.fontScale.coerceAtLeast(1f)).dp
    val rowPixels = with(density) { rowHeight.toPx() }
    val scroll = rememberScrollState()
    var viewportHeight by remember { mutableIntStateOf(0) }
    var drag by remember(revision, editable) { mutableStateOf<WaypointDrag?>(null) }
    val move by rememberUpdatedState(onMove)
    fun target(y: Float): Int =
        floor((y + scroll.value) / rowPixels).toInt().coerceIn(0, stops.lastIndex.coerceAtLeast(0))

    LaunchedEffect(drag?.from, revision, editable) {
        while (editable && drag != null) {
            val active = drag ?: break
            val edge = (viewportHeight / 3f).coerceAtMost(rowPixels)
            if (edge > 0) {
                val delta = when {
                    active.viewportY < edge -> -rowPixels / 5f
                    active.viewportY > viewportHeight - edge -> rowPixels / 5f
                    else -> 0f
                }
                if (delta != 0f && scroll.scrollBy(delta) != 0f) {
                    drag?.let { drag = it.copy(to = target(it.viewportY)) }
                }
            }
            delay(24)
        }
    }

    Row(Modifier.fillMaxWidth().heightIn(min = rowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            NaosActionIcon(NaosActionGlyph.VIRTUAL_GPS, muted)
        }
        Box(Modifier.weight(1f).heightIn(min = rowHeight)
            .then(if (origin != null) Modifier.clickable { onLocate(origin) } else Modifier)
            .semantics { contentDescription = "路線起點" },
            contentAlignment = Alignment.CenterStart) {
            Text(origin?.label() ?: "尚未設定", color = muted, fontSize = 12.sp)
        }
    }

    if (stops.isNotEmpty()) {
        val display = stops.indices.toMutableList().apply {
            drag?.let { if (it.from in indices && it.to in indices) add(it.to, removeAt(it.from)) }
        }
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = rowHeight * 5f)
                .onSizeChanged { viewportHeight = it.height }
                .verticalScroll(scroll),
        ) {
            display.forEachIndexed { position, originalIndex ->
                key(originalIndex) {
                    val point = stops[originalIndex]
                    Row(
                        Modifier.fillMaxWidth().height(rowHeight)
                            .background(if (drag?.from == originalIndex) muted.copy(alpha = .10f)
                                else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp)
                                .semantics {
                                    contentDescription = "長按拖移第 " + (position + 1) + " 點"
                                    if (editable) customActions = buildList {
                                        if (originalIndex > 0) add(CustomAccessibilityAction("上移") {
                                            move(revision, originalIndex, originalIndex - 1); true
                                        })
                                        if (originalIndex < stops.lastIndex) add(CustomAccessibilityAction("下移") {
                                            move(revision, originalIndex, originalIndex + 1); true
                                        })
                                    }
                                }
                                .then(if (editable) Modifier.pointerInput(revision, originalIndex, rowPixels) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val y = originalIndex * rowPixels - scroll.value + offset.y +
                                                (rowPixels - with(density) { 48.dp.toPx() }) / 2f
                                            drag = WaypointDrag(originalIndex, originalIndex, y)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val active = drag
                                            if (active != null && active.from == originalIndex) {
                                                val y = active.viewportY + amount.y
                                                drag = active.copy(to = target(y), viewportY = y)
                                            }
                                        },
                                        onDragEnd = {
                                            val active = drag
                                            drag = null
                                            if (active != null && active.from == originalIndex && active.from != active.to)
                                                move(revision, active.from, active.to)
                                        },
                                        onDragCancel = { drag = null },
                                    )
                                } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (editable) NaosActionIcon(NaosActionGlyph.GRIP, muted)
                        }
                        val colour = when (position) {
                            0 -> Color(0xFF159E5E)
                            stops.lastIndex -> Color(0xFFD35C51)
                            else -> Color(0xFF306EEE)
                        }
                        Box(Modifier.size(28.dp).background(colour, CircleShape), contentAlignment = Alignment.Center) {
                            Text((position + 1).toString(), fontSize = 12.sp, color = Color.White,
                                fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).height(rowHeight)
                            .clickable(enabled = drag == null) { onLocate(point) },
                            contentAlignment = Alignment.CenterStart) {
                            Text(point.label(), fontSize = 12.sp, color = foreground, maxLines = 2)
                        }
                        if (editable) IconButton(
                            enabled = drag == null,
                            onClick = { onDelete(revision, originalIndex) },
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = "刪除第 " + (position + 1) + " 點"
                            },
                        ) {
                            NaosActionIcon(NaosActionGlyph.DELETE, muted)
                        }
                    }
                }
            }
        }
    }
}
