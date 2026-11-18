package com.naos.pikminbee.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-opacity, low-cost Liquid Glass-inspired surfaces for MapLibre/POCO.
 * Avoid per-frame backdrop blur over an actively rendered vector map.
 */
@Composable
fun MapGlass(dark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
        color = if (dark) Color(0xC521334D) else Color(0xD8F7FAFF),
        contentColor = if (dark) Color(0xFFF5F7FF) else Color(0xFF1B2536),
        border = BorderStroke(1.2.dp, Brush.linearGradient(
            if (dark) listOf(Color(0xDAFFFFFF), Color(0x559FC9FF), Color(0x95FFFFFF))
            else listOf(Color.White, Color(0xA7B8D3FF), Color(0xFFFFFFFF)),
        )),
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

/** Coordinate-only top bar. No network search or automatic teleport. */
@Composable
fun CoordinateTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onMenu: () -> Unit,
    onMapBack: () -> Unit,
    mapExplore: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    MapGlass(dark, modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            IconButton(
                onClick = if (mapExplore) onMapBack else onMenu,
                modifier = Modifier.size(48.dp),
            ) {
                Text(
                    if (mapExplore) "‹" else "☰",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = if (mapExplore) "返回操作" else "開啟模式選單"
                    },
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.take(65)) },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                singleLine = true,
                label = { Text("經緯度", fontSize = 12.sp) },
                placeholder = { Text("25.0330, 121.5654", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply() }),
                trailingIcon = {
                    IconButton(onClick = onApply, modifier = Modifier.size(44.dp)) {
                        Text("↗", fontSize = 24.sp,
                            modifier = Modifier.semantics { contentDescription = "前往輸入座標" })
                    }
                },
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(43.dp)) {
                Text("⧉", fontSize = 24.sp,
                    modifier = Modifier.semantics { contentDescription = "複製選取座標" })
            }
        }
    }
}

/** Map camera only; this action never writes a mock GPS position. */
@Composable
fun CurrentLocationMapButton(
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(54.dp).semantics {
            contentDescription = "地圖移至目前位置，不會瞬移"
        },
        containerColor = if (dark) Color(0xD02B4165) else Color(0xDEF5F9FF),
        contentColor = if (dark) Color(0xFFD4E8FF) else Color(0xFF3265B7),
    ) { Text("◎", fontSize = 29.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun CompactModeHeader(
    mode: String,
    description: String,
    onExpand: () -> Unit,
    dark: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(mode, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = if (dark) Color.White else Color(0xFF1B2536))
            Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp, color = if (dark) Color(0xFFD2D9E8) else Color(0xFF526077))
        }
        TextButton(onClick = onExpand, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("展開操作 ⌃", fontSize = 12.sp)
        }
    }
}
