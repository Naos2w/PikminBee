package com.naos.pikminbee.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Horizontal gestures are limited to the actual mode dock and the expanded
 * bottom sheet. MapLibre/Google Map gestures remain untouched.
 */
internal fun Modifier.swipeModes(active: Int, onMode: (Int) -> Unit): Modifier =
    pointerInput(active) {
        var horizontal = 0f
        detectHorizontalDragGestures(
            onDragStart = { horizontal = 0f },
            onHorizontalDrag = { change, dragAmount ->
                horizontal += dragAmount
                change.consume()
            },
            onDragEnd = {
                MapModeSwipePolicy.target(
                    active, horizontal, MapModeSwipePolicy.MIN_SWIPE_DP.dp.toPx(),
                )?.let(onMode)
                horizontal = 0f
            },
            onDragCancel = { horizontal = 0f },
        )
    }

/** Reuse the same vector icons, tab colours and selection cue everywhere. */
@Composable
internal fun MapModeTabs(
    active: Int,
    palette: NaosPalette,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 55.dp,
) {
    val labels = listOf("瞬移", "路徑", "尋花", "設定")
    Row(
        modifier.fillMaxWidth().swipeModes(active, onMode),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSettings = index == 3
            val isCurrent = !isSettings && index == active
            val background by animateColorAsState(
                if (isCurrent) palette.accent else Color.Transparent,
                animationSpec = tween(NaosDesignSystem.FAST_MS),
                label = "mode-tab-colour",
            )
            val tint = if (isCurrent) palette.onAccent else palette.ink
            Surface(
                onClick = { if (isSettings) onSettings() else onMode(index) },
                modifier = Modifier.weight(1f).height(height).semantics {
                    selected = isCurrent
                    contentDescription = if (isCurrent) "目前模式：$label" else "切換至${label}模式"
                },
                shape = RoundedCornerShape(15.dp),
                color = background,
                contentColor = tint,
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (isSettings) NaosUtilityIcon(0, tint) else NaosModeIcon(index, tint)
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
