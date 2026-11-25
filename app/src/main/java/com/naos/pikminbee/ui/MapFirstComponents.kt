package com.naos.pikminbee.ui

import android.graphics.Bitmap
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Low-overhead opaque mineral surface.
 * No native map snapshot, AGSL refraction, shader, backdrop blur or animated
 * transparency. The map alone renders its own tiles; controls are just Compose.
 */
@Composable
fun MapGlass(
    dark: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") backdrop: Bitmap? = null,
    content: @Composable () -> Unit,
) {
    val visual = LocalNaosVisualState.current
    val palette = if (visual.dark == dark) visual.palette
        else NaosDesignSystem.palette(visual.family, dark)
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.stroke, shape),
    ) { content() }
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

/** Shared lightweight vector glyphs; only major actions keep a short text label. */
enum class NaosActionGlyph {
    PLAN, PLAY, PAUSE, STOP, UNDO, REAL_GPS, VIRTUAL_GPS, FLOWER, SPEED, STEPS, REFRESH, EDIT, DONE, CLOCK, MORE, WIFI
}

@Composable
fun NaosActionIcon(
    glyph: NaosActionGlyph, tint: Color, modifier: Modifier = Modifier,
) {
    if (glyph == NaosActionGlyph.FLOWER) {
        NaosModeIcon(2, tint, modifier)
        return
    }
    if (glyph == NaosActionGlyph.REAL_GPS) {
        NaosModeIcon(4, tint, modifier)
        return
    }
    Canvas(modifier.size(22.dp)) {
        val scale = size.minDimension / 24f
        fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)
        val pen = 1.9f * scale
        when (glyph) {
            NaosActionGlyph.PLAN -> {
                drawLine(tint, pt(5f, 6f), pt(5f, 17f), pen, StrokeCap.Round)
                drawLine(tint, pt(5f, 17f), pt(19f, 17f), pen, StrokeCap.Round)
                drawCircle(tint, 2.5f * scale, pt(5f, 5f), style = Stroke(pen))
                drawCircle(tint, 2.5f * scale, pt(19f, 19f), style = Stroke(pen))
            }
            NaosActionGlyph.PLAY -> {
                val triangle = Path().apply {
                    moveTo(7f*scale, 4f*scale)
                    lineTo(20f*scale, 12f*scale)
                    lineTo(7f*scale, 20f*scale)
                    close()
                }
                drawPath(triangle, tint)
            }
            NaosActionGlyph.PAUSE -> {
                drawLine(tint, pt(8f, 5f), pt(8f, 19f), pen*2f, StrokeCap.Round)
                drawLine(tint, pt(16f, 5f), pt(16f, 19f), pen*2f, StrokeCap.Round)
            }
            NaosActionGlyph.STOP ->
                drawRoundRect(tint, topLeft = pt(6f, 6f),
                    size = Size(12f*scale, 12f*scale),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f*scale))
            NaosActionGlyph.UNDO -> {
                drawLine(tint, pt(11f, 5f), pt(5f, 11f), pen, StrokeCap.Round)
                drawLine(tint, pt(5f, 11f), pt(11f, 17f), pen, StrokeCap.Round)
                drawLine(tint, pt(5f, 11f), pt(17f, 11f), pen, StrokeCap.Round)
            }
            NaosActionGlyph.VIRTUAL_GPS -> {
                val marker = Path().apply {
                    moveTo(12f*scale, 22f*scale)
                    cubicTo(9f*scale, 17f*scale, 5f*scale, 13f*scale, 5f*scale, 10f*scale)
                    cubicTo(5f*scale, 0f, 19f*scale, 0f, 19f*scale, 10f*scale)
                    cubicTo(19f*scale, 13f*scale, 15f*scale, 17f*scale, 12f*scale, 22f*scale)
                    close()
                }
                drawPath(marker, tint, style = Stroke(pen, cap = StrokeCap.Round))
                drawCircle(tint, 2.5f*scale, pt(12f, 9f), style = Stroke(pen))
            }
            NaosActionGlyph.SPEED -> {
                drawArc(tint, 155f, 230f, false,
                    topLeft = pt(3f, 3f), size = Size(18f*scale, 18f*scale),
                    style = Stroke(pen, cap = StrokeCap.Round))
                drawLine(tint, pt(12f, 16f), pt(17f, 9f), pen, StrokeCap.Round)
                drawCircle(tint, 2f*scale, pt(12f, 16f))
            }
            NaosActionGlyph.STEPS -> {
                drawOval(tint, topLeft = pt(5f, 11f),
                    size = Size(5f*scale, 9f*scale))
                drawOval(tint, topLeft = pt(14f, 4f),
                    size = Size(5f*scale, 9f*scale))
                drawCircle(tint, 1.6f*scale, pt(6f, 7.5f))
                drawCircle(tint, 1.6f*scale, pt(18f, 16.5f))
            }
            NaosActionGlyph.REFRESH -> {
                drawArc(tint, -65f, 280f, false, topLeft = pt(4f, 4f),
                    size = Size(16f*scale, 16f*scale),
                    style = Stroke(pen, cap = StrokeCap.Round))
                drawLine(tint, pt(20f, 5f), pt(20f, 11f), pen, StrokeCap.Round)
                drawLine(tint, pt(20f, 11f), pt(14f, 11f), pen, StrokeCap.Round)
            }
            NaosActionGlyph.EDIT -> {
                // Conventional diagonal pencil silhouette, with separate cap.
                val body = Path().apply {
                    moveTo(4f * scale, 16.5f * scale)
                    lineTo(4f * scale, 20f * scale)
                    lineTo(7.5f * scale, 20f * scale)
                    lineTo(18f * scale, 9.5f * scale)
                    lineTo(14.5f * scale, 6f * scale)
                    close()
                }
                val cap = Path().apply {
                    moveTo(15.8f * scale, 4.7f * scale)
                    lineTo(17.5f * scale, 3f * scale)
                    lineTo(21f * scale, 6.5f * scale)
                    lineTo(19.3f * scale, 8.2f * scale)
                    close()
                }
                drawPath(body, tint)
                drawPath(cap, tint)
            }
            NaosActionGlyph.DONE -> {
                drawLine(tint, pt(4f, 12f), pt(10f, 18f), pen*1.3f, StrokeCap.Round)
                drawLine(tint, pt(10f, 18f), pt(21f, 6f), pen*1.3f, StrokeCap.Round)
            }
            NaosActionGlyph.CLOCK -> {
                drawCircle(tint, 9f * scale, pt(12f, 12f), style = Stroke(pen))
                drawLine(tint, pt(12f, 6f), pt(12f, 12f), pen, StrokeCap.Round)
                drawLine(tint, pt(12f, 12f), pt(16f, 14f), pen, StrokeCap.Round)
            }
            NaosActionGlyph.WIFI -> {
                drawArc(tint, 225f, 90f, false, topLeft = pt(-1f, 3f), size = Size(26f*scale, 26f*scale), style = Stroke(pen, cap = StrokeCap.Round))
                drawArc(tint, 225f, 90f, false, topLeft = pt(4f, 8f), size = Size(16f*scale, 16f*scale), style = Stroke(pen, cap = StrokeCap.Round))
                drawCircle(tint, 1.6f*scale, pt(12f, 17f))
            }
            NaosActionGlyph.MORE -> {
                for (x in listOf(5f, 12f, 19f)) drawCircle(tint, 1.7f * scale, pt(x, 12f))
            }
            NaosActionGlyph.REAL_GPS, NaosActionGlyph.FLOWER -> Unit
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
    onFavourites: () -> Unit,
    teleportEnabled: Boolean = true,
    dismissEditingToken: Int = 0,
    editingBlocked: Boolean = false,
    modeTitle: String = "瞬移",
    dark: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Bitmap? = null,
) {
    val palette = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    var editor by remember { mutableStateOf(CoordinateInputEditorState()) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val showingEditor = editor.showField(teleportEnabled, editingBlocked)

    fun dismissEditor() {
        editor = editor.dismiss()
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    // Returning to the map after switching apps must not reopen the keyboard.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && editor.editing) dismissEditor()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // No input is created on startup. Only an explicit edit tap focuses it.
    LaunchedEffect(showingEditor) {
        if (showingEditor) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // Location picks, mode changes and opening sheets must not retain IME focus.
    LaunchedEffect(teleportEnabled, dismissEditingToken, editingBlocked) {
        if (editor.editing) dismissEditor()
    }

    MapGlass(dark, modifier, backdrop) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { dismissEditor(); onHome() },
                modifier = Modifier.size(46.dp)
                    .semantics { contentDescription = "回到目前定位並收起選單" },
            ) { NaosBeeHomeIcon(palette.accent) }
            Column(Modifier.weight(1f)) {
                Text(if (teleportEnabled) "瞬移 · 選取座標" else "$modeTitle · 地圖選點",
                    fontSize = 11.sp, color = palette.muted)
                if (showingEditor) {
                    BasicTextField(
                        value = value,
                        onValueChange = { onValueChange(it.take(65)) },
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text("輸入緯度、經度", color = palette.muted, fontSize = 14.sp)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "輸入緯度、經度" },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            dismissEditor()
                            onTeleport()
                        }),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                    )
                } else {
                    // Never mount a focusable field while merely viewing the map.
                    Text(
                        text = value.ifEmpty {
                            if (teleportEnabled) "點地圖選取座標" else "點地圖選擇位置"
                        },
                        color = if (value.isEmpty()) palette.muted else palette.ink,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                            .semantics {
                                contentDescription = "目前選取座標：" + value.ifEmpty { "尚未選取" }
                            },
                    )
                }
            }
            if (teleportEnabled && !editingBlocked) {
                IconButton(
                    onClick = {
                        if (showingEditor) dismissEditor()
                        else editor = editor.begin(teleportMode = true, blocked = false)
                    },
                    modifier = Modifier.size(42.dp).semantics {
                        contentDescription = if (showingEditor)
                            "完成座標編輯並收起鍵盤" else "編輯經緯度"
                    },
                ) {
                    NaosActionIcon(
                        if (showingEditor) NaosActionGlyph.DONE else NaosActionGlyph.EDIT,
                        palette.ink
                    )
                }
            }
            if (teleportEnabled) {
                Box(
                    modifier = Modifier.size(width = 48.dp, height = 47.dp)
                        .clip(RoundedCornerShape(14.dp)).background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { dismissEditor(); onTeleport() },
                        modifier = Modifier.matchParentSize()
                            .semantics { contentDescription = "瞬移到所選座標" },
                    ) {
                        Text("↗", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                            color = palette.onAccent)
                    }
                }
            }
            IconButton(
                onClick = { dismissEditor(); onFavourites() },
                modifier = Modifier.size(44.dp)
                    .semantics { contentDescription = "開啟收藏地點與路線" },
            ) { NaosModeIcon(3, palette.ink) }
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
