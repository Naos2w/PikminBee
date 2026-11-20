package com.naos.pikminbee.ui

import android.graphics.Bitmap
import com.naos.pikminbee.domain.WalkingProgressSnapshot
import java.util.Locale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pixel-diff reference: PikminBee-V4-A-Naos-LiquidGlassV3-20261120.fig
 * Naos Universal V1.0 tokens · iOS Liquid Glass V3 optical reference.
 *
 * The map owns the entire viewport. No navigation drawer, full-screen drag
 * detector, or BottomSheetScaffold over the map's gesture area. Only dedicated
 * controls consume touches. Glass captures the map's own idle screenshot,
 * never another app's pixels. Native failure falls back to frosted material.
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
    onHome: () -> Unit,
    openDetailsRequest: Int = 0,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
    mapExplore: Boolean,
    onCloseExplore: () -> Unit,
    mapContent: @Composable () -> Unit,
    modeContent: @Composable () -> Unit,
    backdrop: Bitmap? = null,
    routeStopsCount: Int = 0,
    flowerStopsCount: Int = 0,
    flowerActive: Boolean = false,
    walkingProgress: WalkingProgressSnapshot = WalkingProgressSnapshot(),
    routeSpeedKmh: Double = 1.4,
    routeActive: Boolean = false,
    routePaused: Boolean = false,
    onRouteUndo: () -> Unit = {},
    onRoutePrimary: () -> Unit = {},
    onRouteStop: () -> Unit = {},
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openDetailsRequest) {
        if (openDetailsRequest > 0) detailsOpen = true
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val p = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    val heightDp = LocalConfiguration.current.screenHeightDp
    val labels = listOf("瞬移", "路徑", "尋花", "收藏")
    val active = modeIndex.coerceIn(0, 3)

    Box(Modifier.fillMaxSize().background(p.bg)) {
        mapContent()

        // Bottom dock: 14dp side margins, 78dp body. The map remains fully
        // interactive above and to either side of the visible controls.
        MapGlass(
            dark, Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            backdrop,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)) {
                // Expanded hit area is 44dp high, but overlaps only this small dock.
                Box(
                    Modifier.fillMaxWidth().height(28.dp)
                        .pointerInput(Unit) {
                            var movement = 0f
                            detectVerticalDragGestures(
                                onDragStart = { movement = 0f },
                                onVerticalDrag = { change, drag ->
                                    movement += drag
                                    if (movement < -14f) {
                                        change.consume()
                                        detailsOpen = true
                                    }
                                },
                            )
                        }
                        .clickable { detailsOpen = true }
                        .semantics { contentDescription = "向上展開模式詳細設定" },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Spacer(
                        Modifier.padding(top = 4.dp).width(48.dp).height(4.dp)
                            .background(p.muted.copy(alpha = .6f), RoundedCornerShape(4.dp))
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    labels.forEachIndexed { i, label ->
                        val selected = i == active
                        val tabColor by animateColorAsState(
                            if (selected) p.accent else Color.Transparent,
                            tween(NaosDesignSystem.FAST_MS), label = "tabColour")
                        Surface(
                            modifier = Modifier.weight(1f).height(55.dp),
                            onClick = { onMode(i) },
                            shape = RoundedCornerShape(15.dp),
                            color = tabColor,
                            contentColor = if (selected) p.onAccent else p.ink,
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                ) {
                                NaosModeIcon(i, if (selected) p.onAccent else p.ink)
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if ((modeIndex == 1 && (routeStopsCount > 0 || routeActive)) ||
            (modeIndex == 2 && flowerActive)) {
            val activeWalking = routeActive || flowerActive
            val modeStopsCount = if (modeIndex == 2) flowerStopsCount else routeStopsCount
            MapGlass(
                dark,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 15.dp, end = 15.dp, bottom = 113.dp),
                backdrop,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (activeWalking) (if (flowerActive) "尋花行走中" else "道路行走中") else "下一個路點",
                            color = p.muted, fontSize = 11.sp, maxLines = 1)
                        Text(if (activeWalking) (if (routePaused) "已暫停" else "進行中")
                            else "第 ${modeStopsCount + 1} 點",
                            color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Surface(
                        onClick = if (activeWalking) onRouteStop else onRouteUndo,
                        enabled = activeWalking || modeStopsCount > 0,
                        shape = RoundedCornerShape(13.dp),
                        color = p.soft,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(if (activeWalking) "停止" else "↶ 上一步",
                                color = p.ink, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        onClick = onRoutePrimary,
                        shape = RoundedCornerShape(13.dp),
                        color = p.accent,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 15.dp), contentAlignment = Alignment.Center) {
                            Text(if (!activeWalking) "▷ 開始" else if (routePaused) "▷ 繼續" else "Ⅱ 暫停",
                                color = p.onAccent, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (activeWalking && walkingProgress.totalMetres > 0.0) {
                    val total = walkingProgress.totalMetres / 1000.0
                    val done = walkingProgress.travelledMetres / 1000.0
                    val progressText = buildString {
                        append("路點 ")
                        append(walkingProgress.nextWaypoint?.toString() ?: "終點")
                        append(" / ")
                        append(walkingProgress.waypointCount.coerceAtLeast(modeStopsCount))
                        append(" · ")
                        append(String.format(Locale.US, "%.2f / %.2f km", done, total))
                        append(" · ")
                        append(routeSpeedKmh)
                        append(" km/h · ")
                        append(walkingProgress.remainingSeconds / 60)
                        append(" 分")
                    }
                    Text(progressText, color = p.muted, fontSize = 10.sp, maxLines = 1)
                    Box(Modifier.fillMaxWidth().height(4.dp)
                        .background(p.soft, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(walkingProgress.fraction).height(4.dp)
                            .background(p.accent, RoundedCornerShape(4.dp)))
                    }
                }
                }
            }
        }

        CoordinateTopBar(
            value = coordinateInput,
            onValueChange = onCoordinateChange,
            onTeleport = onCoordinateTeleport,
            onHome = {
                detailsOpen = false
                onHome()
            },
            dark = dark,
            modifier = Modifier.align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            backdrop = backdrop,
        )

        CurrentLocationMapButton(
            onClick = onCurrentLocation,
            dark = dark,
            modifier = Modifier.align(Alignment.BottomEnd)
                .navigationBarsPadding().padding(end = 16.dp,
                    bottom = if ((modeIndex == 1 && (routeStopsCount > 0 || routeActive)) ||
                        (modeIndex == 2 && flowerActive)) 224.dp else 132.dp),
            backdrop = backdrop,
        )
    }

    if (detailsOpen) {
        ModalBottomSheet(
            onDismissRequest = { detailsOpen = false },
            sheetState = sheetState,
            containerColor = p.surface,
            contentColor = p.ink,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$modeTitle · 詳細操作",
                    modifier = Modifier.weight(1f), color = p.ink,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { detailsOpen = false; onSettings() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "開啟設定" },
                ) { NaosUtilityIcon(0, p.ink) }
                IconButton(
                    onClick = { detailsOpen = false; onPermissions() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "檢查權限" },
                ) { NaosUtilityIcon(1, p.ink) }
            }
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = (heightDp - 230).coerceIn(175, 580).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 17.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { modeContent() }
            Spacer(Modifier.height(8.dp))
        }
    }
}
