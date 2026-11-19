package com.naos.pikminbee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Map receives all drag, pinch, rotation and zoom events outside the small controls.
 * Deliberately NO ModalNavigationDrawer and NO draggable BottomSheetScaffold:
 * both previously captured gestures on the map/at the left edge.
 *
 * The collapsed bottom rail switches modes only. Drag its dedicated handle up,
 * or tap it, to open an actual modal editor. Closing restores the free map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFirstShell(
    dark: Boolean,
    modeIndex: Int,
    modeTitle: String,
    message: String,
    coordinateInput: String,
    onCoordinateChange: (String) -> Unit,
    onCoordinateTeleport: () -> Unit,
    onCurrentLocation: () -> Unit,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
    mapExplore: Boolean,
    onCloseExplore: () -> Unit,
    mapContent: @Composable () -> Unit,
    modeContent: @Composable () -> Unit,
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val text = if (dark) Color(0xFFEAF4FF) else Color(0xFF203856)
    val subdued = if (dark) Color(0xFFB5CAE4) else Color(0xFF667E98)
    val selected = if (dark) Color(0xFF336AA6) else Color(0xFF3578D1)
    val smallPane = if (dark) Color(0xE8233E60) else Color(0xEFF5F9FF)
    val heightDp = LocalConfiguration.current.screenHeightDp
    val labels = listOf("瞬移", "路徑", "尋花", "收藏")
    val symbols = listOf("⌖", "①→②", "✿", "☆")

    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF101C2B) else Color(0xFFF5F8FC))) {
        // Native MapLibre / Google MapView. Do not put a full-screen gesture layer above it.
        mapContent()

        MapGlass(
            dark = dark,
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(33.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dy ->
                                    if (dy < -8f) {
                                        change.consume()
                                        detailsOpen = true
                                    }
                                },
                            )
                        }
                        .clickable { detailsOpen = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Spacer(
                        Modifier.padding(top = 3.dp).width(38.dp).height(4.dp)
                            .background(subdued.copy(alpha = .55f), RoundedCornerShape(6.dp)),
                    )
                    Text("上滑展開詳細操作", fontSize = 10.sp, color = subdued)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    labels.forEachIndexed { i, label ->
                        Surface(
                            onClick = { onMode(i) },
                            modifier = Modifier.weight(1f).height(47.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (modeIndex == i) selected else smallPane,
                            contentColor = if (modeIndex == i) Color.White else text,
                            shadowElevation = if (modeIndex == i) 3.dp else 0.dp,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(symbols[i], fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                                    maxLines = 1)
                                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text(
                    if (modeIndex == 1) message
                    else if (modeIndex == 0) "點地圖選位置；上方箭頭直接瞬移"
                    else message,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, start = 3.dp, end = 3.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = subdued, fontSize = 10.sp,
                )
            }
        }

        CoordinateTopBar(
            value = coordinateInput,
            onValueChange = onCoordinateChange,
            onTeleport = onCoordinateTeleport,
            dark = dark,
            modifier = Modifier.align(Alignment.TopCenter)
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        CurrentLocationMapButton(
            onClick = onCurrentLocation,
            dark = dark,
            modifier = Modifier.align(Alignment.BottomEnd)
                .navigationBarsPadding().padding(end = 16.dp, bottom = 145.dp),
        )
    }

    if (detailsOpen) {
        ModalBottomSheet(
            onDismissRequest = { detailsOpen = false },
            sheetState = sheetState,
            containerColor = if (dark) Color(0xF31A2D47) else Color(0xF4F3F8FF),
            contentColor = text,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$modeTitle · 詳細操作", modifier = Modifier.weight(1f),
                    color = text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                TextButton(onClick = { detailsOpen = false; onSettings() }) {
                    Text("設定", fontSize = 12.sp)
                }
                TextButton(onClick = { detailsOpen = false; onPermissions() }) {
                    Text("權限", fontSize = 12.sp)
                }
            }
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = (heightDp - 230).coerceIn(175, 580).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 17.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modeContent()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
