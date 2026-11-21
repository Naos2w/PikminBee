package com.naos.pikminbee.ui

import android.graphics.Bitmap
import com.naos.pikminbee.domain.WalkingProgressSnapshot
import java.util.Locale
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
 * never another app's pixels. Uses opaque Compose controls for fluid map gestures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFirstShell(
    dark: Boolean,
    modeIndex: Int,
    modeTitle: String,
    message: String,
    coordinateInput: String,
    coordinateInputDismissToken: Int = 0,
    onCoordinateChange: (String) -> Unit,
    onCoordinateTeleport: () -> Unit,
    onCurrentLocation: () -> Unit,
    onHome: () -> Unit,
    openDetailsRequest: Int = 0,
    closeDetailsRequest: Int = 0,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    overlayVisible: Boolean,
    onToggleOverlay: () -> Unit,
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
    walkEstimatedSteps: Long = 0L,
    walkStopped: Boolean = false,
    routeSpeedKmh: Double = 1.4,
    routeActive: Boolean = false,
    routePaused: Boolean = false,
    routePreviewReady: Boolean = false,
    routePlanning: Boolean = false,
    routeSnapMetres: Double = 0.0,
    onRouteUndo: () -> Unit = {},
    onRoutePrimary: () -> Unit = {},
    onRouteStop: () -> Unit = {},
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openDetailsRequest) {
        if (openDetailsRequest > 0) detailsOpen = true
    }
    LaunchedEffect(closeDetailsRequest) {
        if (closeDetailsRequest > 0) detailsOpen = false
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailsScrollState = rememberScrollState()
    LaunchedEffect(modeIndex, detailsOpen) {
        // A page change inside the open details sheet starts at the top.
        if (detailsOpen) detailsScrollState.scrollTo(0)
    }
    val p = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    val heightDp = LocalConfiguration.current.screenHeightDp
    val active = modeIndex.coerceIn(0, 3)
    // Ongoing walking must remain controllable even after changing map tabs.
    val showQuick = (modeIndex == 1 && routeStopsCount > 0) ||
        (modeIndex == 2 && flowerStopsCount > 0) || routeActive || flowerActive

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
                MapModeTabs(
                    active = active,
                    palette = p,
                    onMode = onMode,
                )
            }
        }

        if (showQuick) {
            val activeWalking = routeActive || flowerActive
            val modeStopsCount = when {
                flowerActive -> flowerStopsCount
                routeActive -> routeStopsCount
                modeIndex == 2 -> flowerStopsCount
                else -> routeStopsCount
            }
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
                        Text(
                            when {
                                activeWalking && routePaused -> "已暫停 · 本次估算"
                                activeWalking -> (if (flowerActive) "尋花中" else "行走中") + " · 本次估算"
                                walkStopped -> "已結束 · 本次估算"
                                else -> "下一個路點"
                            },
                            color = p.muted, fontSize = 11.sp, maxLines = 1,
                        )
                        Text(
                            when {
                                activeWalking || walkStopped -> "${walkEstimatedSteps} 步"
                                routePlanning -> "正在規劃…"
                                routePreviewReady -> "已規劃 · 請確認"
                                else -> "第 ${modeStopsCount + 1} 點"
                            },
                            color = if (activeWalking) Color(
                                if (routePaused) BeeActionColors.PAUSED_BLUE
                                else BeeActionColors.START_GREEN
                            ) else p.ink,
                            fontSize = if (activeWalking || walkStopped) 17.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.semantics {
                                if (activeWalking || walkStopped) {
                                    contentDescription = "本次模擬行走估算 ${walkEstimatedSteps} 步"
                                }
                            },
                        )
                    }
                    Surface(
                        onClick = if (activeWalking) onRouteStop else onRouteUndo,
                        enabled = activeWalking || (!routePlanning && modeStopsCount > 0),
                        shape = RoundedCornerShape(13.dp),
                        color = if (activeWalking)
                            Color(BeeActionColors.STOP_RED) else p.soft,
                        contentColor = if (activeWalking)
                            Color(BeeActionColors.ON_DANGER) else p.ink,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = if (activeWalking) 12.dp else 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            NaosActionIcon(
                                if (activeWalking) NaosActionGlyph.STOP else NaosActionGlyph.UNDO,
                                if (activeWalking) Color(BeeActionColors.ON_DANGER) else p.ink,
                            )
                            if (activeWalking) {
                                Text("停止", color = Color(BeeActionColors.ON_DANGER),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Surface(
                        onClick = onRoutePrimary,
                        enabled = activeWalking || (!routePlanning && modeStopsCount > 0),
                        shape = RoundedCornerShape(13.dp),
                        color = if (activeWalking && !routePaused) Color(BeeActionColors.PAUSE_AMBER)
                            else if (!activeWalking && !routePreviewReady) p.accent
                            else Color(BeeActionColors.START_GREEN),
                        contentColor = if (!activeWalking && !routePreviewReady) p.onAccent
                            else Color(BeeActionColors.ON_BRIGHT),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val controlInk = if (!activeWalking && !routePreviewReady) p.onAccent
                                else Color(BeeActionColors.ON_BRIGHT)
                            NaosActionIcon(
                                when {
                                    activeWalking && !routePaused -> NaosActionGlyph.PAUSE
                                    activeWalking || routePreviewReady -> NaosActionGlyph.PLAY
                                    else -> NaosActionGlyph.PLAN
                                },
                                controlInk,
                            )
                            Text(when {
                                activeWalking && routePaused -> "繼續"
                                activeWalking -> "暫停"
                                routePlanning -> "規劃中"
                                routePreviewReady -> "開始"
                                else -> "規劃"
                            }, color = controlInk,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (!activeWalking && routePreviewReady && routeSnapMetres > 40.0) {
                    Text("路點最大吸附 ${routeSnapMetres.toInt()} 公尺 · 開始前會再次確認",
                        color = p.muted, fontSize = 11.sp, maxLines = 1)
                } else if (!activeWalking &&
                    (message.contains("失敗") || message.contains("無法"))) {
                    Text(message, color = Color(BeeActionColors.STOP_RED),
                        fontSize = 11.sp, maxLines = 2)
                }
                if (activeWalking && walkingProgress.totalMetres > 0.0) {
                    val total = walkingProgress.totalMetres / 1000.0
                    val done = walkingProgress.travelledMetres / 1000.0
                    val progressText = buildString {
                        append("路段 ")
                        append(walkingProgress.activeLegLabel)
                        append(" · 共 ")
                        append(walkingProgress.waypointCount.coerceAtLeast(modeStopsCount))
                        append(" 點 · ")
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
            teleportEnabled = active == 0,
            dismissEditingToken = coordinateInputDismissToken,
            editingBlocked = detailsOpen || mapExplore,
            modeTitle = modeTitle,
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
                    bottom = if (showQuick) 224.dp else 132.dp),
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
                    onClick = onToggleOverlay,
                    modifier = Modifier.size(48.dp)
                        .background(if (overlayVisible) p.soft else Color.Transparent,
                            RoundedCornerShape(14.dp))
                        .semantics { contentDescription = if (overlayVisible)
                            "隱藏懸浮控制" else "開啟懸浮控制" },
                ) { NaosBeeHomeIcon(if (overlayVisible) p.accent else p.ink) }
                IconButton(
                    onClick = { detailsOpen = false; onPermissions() },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "檢查權限" },
                ) { NaosUtilityIcon(1, p.ink) }
            }
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = (heightDp - 230).coerceIn(175, 580).dp)
                    // Horizontal swipe switches the sheet's functional page.
                    // Map swipes are left to the map surface outside this sheet.
                    .swipeModes(active, onMode)
                    .verticalScroll(detailsScrollState)
                    .padding(horizontal = 17.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { modeContent() }
            // Same four pages, visual selection and icon labels as the main dock.
            MapModeTabs(
                active = active,
                palette = p,
                onMode = onMode,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                height = 64.dp,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
