package com.naos.pikminbee.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Naos Universal Liquid Glass V3. Pixel refraction is only claimed when a
 * real map snapshot can be sampled. On unsupported/missing source, a frosted
 * semantic-colour fallback is used. No fake edge-displacement decoration.
 */
@Composable
fun MapGlass(
    dark: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Bitmap? = null,
    content: @Composable () -> Unit,
) {
    val visual = LocalNaosVisualState.current
    val palette = if (visual.dark == dark) visual.palette
        else NaosDesignSystem.palette(visual.family, dark)
    val shape = RoundedCornerShape(25.dp)
    val glassMaterial = visual.material
    // A bounded camera sampler now refreshes genuine map pixels during pan and
    // zoom. Keep the last valid sample between captures; fade to mineral frost
    // only if the source has genuinely stalled or the map view is disposed.
    var previousSnapshot by remember { androidx.compose.runtime.mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(backdrop) { if (backdrop != null) previousSnapshot = backdrop }
    val glassBlend by animateFloatAsState(
        targetValue = if (backdrop != null) 1f else 0f,
        animationSpec = tween(NaosDesignSystem.FAST_MS),
        label = "glassOpticalBlend",
    )
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    Box(modifier.shadow(8.dp, shape).clip(shape)) {
        AndroidView(
            modifier = Modifier.matchParentSize().onGloballyPositioned { coordinates ->
                val location = coordinates.positionInRoot()
                originX = location.x
                originY = location.y
            },
            factory = { NaosMapGlassView(it) },
            update = {
                it.snapshot = previousSnapshot
                it.opticalBlend = glassBlend
                it.glassMode = glassMaterial
                it.palette = palette
                it.originX = originX
                it.originY = originY
            },
        )
        content()
    }
}

/** Custom vector glyphs, one consistent 24dp style (no emoji or font-dependent icons). */
@Composable
fun NaosModeIcon(index: Int, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val s = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val stroke = 1.9f * s
        when (index) {
            0 -> { // direct teleport: navigational arrow
                val tri = Path().apply {
                    moveTo(3f*s, 11f*s)
                    lineTo(22f*s, 2f*s)
                    lineTo(13f*s, 21f*s)
                    lineTo(11f*s, 13f*s)
                    close()
                }
                drawPath(tri, tint, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            1 -> {
                drawLine(tint, p(5f, 7f), p(5f, 15f), stroke, StrokeCap.Round)
                drawLine(tint, p(5f, 15f), p(18f, 15f), stroke, StrokeCap.Round)
                drawCircle(tint, 2.5f*s, p(5f, 5f), style = Stroke(stroke))
                drawCircle(tint, 2.5f*s, p(19f, 19f), style = Stroke(stroke))
            }
            2 -> {
                for (n in 0 until 5) {
                    val angle = Math.PI * 2 * n / 5
                    drawCircle(tint, 2.7f*s,
                        p((12+5*Math.cos(angle)).toFloat(), (12+5*Math.sin(angle)).toFloat()),
                        style = Stroke(stroke))
                }
                drawCircle(tint, 2.1f*s, p(12f, 12f))
            }
            3 -> {
                val bookmark = Path().apply {
                    moveTo(6f*s, 3f*s); lineTo(18f*s, 3f*s)
                    lineTo(18f*s, 21f*s); lineTo(12f*s, 17f*s)
                    lineTo(6f*s, 21f*s); close()
                }
                drawPath(bookmark, tint, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            else -> {
                drawCircle(tint, 7f*s, p(12f,12f), style = Stroke(stroke))
                drawCircle(tint, 2f*s, p(12f,12f))
                drawLine(tint,p(12f,1f),p(12f,6f),stroke,StrokeCap.Round)
                drawLine(tint,p(12f,18f),p(12f,23f),stroke,StrokeCap.Round)
                drawLine(tint,p(1f,12f),p(6f,12f),stroke,StrokeCap.Round)
                drawLine(tint,p(18f,12f),p(23f,12f),stroke,StrokeCap.Round)
            }
        }
    }
}

/** Small original bee mark; doubles as the map Home/recentre action. */
@Composable
fun NaosBeeHomeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension / 24f
        // Same original bee silhouette as the draggable overlay icon.
        val outline = Stroke(width = 1.9f * s, cap = StrokeCap.Round)
        drawOval(tint, topLeft = Offset(2.8f*s, 7.6f*s),
            size = Size(7.8f*s, 5.4f*s), style = outline)
        drawOval(tint, topLeft = Offset(13.4f*s, 7.6f*s),
            size = Size(7.8f*s, 5.4f*s), style = outline)
        drawOval(tint, topLeft = Offset(7.2f*s, 9.8f*s),
            size = Size(9.6f*s, 11.6f*s), style = outline)
        drawLine(tint, Offset(8f*s, 14.1f*s), Offset(16f*s, 14.1f*s), 1.9f*s, StrokeCap.Round)
        drawLine(tint, Offset(8.1f*s, 17.5f*s), Offset(15.9f*s, 17.5f*s), 1.9f*s, StrokeCap.Round)
        drawLine(tint, Offset(10.2f*s, 10f*s), Offset(8.8f*s, 5.5f*s), 1.9f*s, StrokeCap.Round)
        drawLine(tint, Offset(13.8f*s, 10f*s), Offset(15.2f*s, 5.5f*s), 1.9f*s, StrokeCap.Round)
    }
}

/** Small vector-only settings/permission utility actions matching the dock. */
@Composable
fun NaosUtilityIcon(kind: Int, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val scale = size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * scale, cap = StrokeCap.Round)
        fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)
        if (kind == 0) {
            drawCircle(tint, radius = 7f * scale, center = pt(12f, 12f), style = stroke)
            drawCircle(tint, radius = 3f * scale, center = pt(12f, 12f), style = stroke)
            for (n in 0 until 8) {
                val rad = Math.PI * n / 4
                val dx = kotlin.math.cos(rad).toFloat()
                val dy = kotlin.math.sin(rad).toFloat()
                drawLine(tint, pt(12f + 8.6f*dx, 12f + 8.6f*dy),
                    pt(12f + 11f*dx, 12f + 11f*dy), 1.8f * scale, StrokeCap.Round)
            }
        } else {
            val path = Path().apply {
                moveTo(12f*scale, 2f*scale)
                lineTo(20f*scale, 5f*scale)
                lineTo(20f*scale, 12f*scale)
                quadraticTo(19f*scale, 18f*scale, 12f*scale, 22f*scale)
                quadraticTo(5f*scale, 18f*scale, 4f*scale, 12f*scale)
                lineTo(4f*scale, 5f*scale)
                close()
            }
            drawPath(path, tint, style = stroke)
            drawLine(tint, pt(8.5f, 12f), pt(11f, 14.5f), 1.8f*scale)
            drawLine(tint, pt(11f, 14.5f), pt(16f, 9.5f), 1.8f*scale)
        }
    }
}

/** A map pick only fills the coordinate field. The arrow/Go explicitly teleports. */
@Composable
fun CoordinateTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    onTeleport: () -> Unit,
    onHome: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Bitmap? = null,
) {
    val palette = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    MapGlass(dark, modifier, backdrop) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(start = 14.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onHome,
                modifier = Modifier.size(46.dp)
                    .semantics { contentDescription = "回到目前定位並收起選單" },
            ) { NaosBeeHomeIcon(palette.accent) }
            Column(Modifier.weight(1f)) {
                Text("經緯度 · 點地圖選取", fontSize = 11.sp, color = palette.muted)
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it.take(65)) },
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "輸入緯度、經度" },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = palette.ink,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onTeleport() }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                )
            }
            Box(
                modifier = Modifier.size(width = 52.dp, height = 49.dp)
                    .clip(RoundedCornerShape(15.dp)).background(palette.accent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onTeleport,
                    modifier = Modifier.matchParentSize()
                        .semantics { contentDescription = "立即瞬移到輸入的座標" },
                ) {
                    Text("↗", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = palette.onAccent)
                }
            }
        }
    }
}

@Composable
fun CurrentLocationMapButton(
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Bitmap? = null,
) {
    val p = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    MapGlass(dark, modifier.size(52.dp), backdrop) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
                .semantics { contentDescription = "地圖移至目前位置，不會瞬移" },
        ) { NaosModeIcon(4, p.ink, Modifier.size(24.dp)) }
    }
}

@Composable
fun CompactModeHeader(
    mode: String, description: String, onExpand: () -> Unit, dark: Boolean,
) {
    val p = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(mode, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            Text(description, fontSize = 12.sp, color = p.muted, maxLines = 1)
        }
        TextButton(onClick = onExpand, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("展開操作 ⌃", color = p.accent, fontSize = 12.sp)
        }
    }
}
