package com.naos.pikminbee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Map-first shell: MapLibre/Google map stays a fully interactive background.
 * Only the small top coordinate bar and collapsible bottom sheet overlay it.
 * No background-blur render effects are used while map tiles animate.
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
    onCoordinateApply: () -> Unit,
    onCoordinateCopy: () -> Unit,
    onCurrentLocation: () -> Unit,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
    mapExplore: Boolean,
    onCloseExplore: () -> Unit,
    mapContent: @Composable () -> Unit,
    modeContent: @Composable () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val sheet = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val darkGlass = Color(0xD11D2D49)
    val foreground = if (dark) Color(0xFFF0F6FF) else Color(0xFF1B2A45)
    val muted = if (dark) Color(0xFFC4D5ED) else Color(0xFF526884)
    val maxSheetBodyDp = (LocalConfiguration.current.screenHeightDp - 190).coerceIn(175, 540)
    val labels = listOf("瞬移", "路徑", "尋花", "收藏")
    LaunchedEffect(modeIndex) { sheet.bottomSheetState.partialExpand() }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !mapExplore,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(282.dp),
                drawerContainerColor = if (dark) Color(0xE71B2B48) else Color(0xEEF4F8FF),
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🐝 PikminBee", fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        color = foreground, modifier = Modifier.padding(12.dp))
                    Text("操作模式", fontSize = 12.sp, color = muted,
                        modifier = Modifier.padding(start = 12.dp, bottom = 9.dp))
                    labels.forEachIndexed { index, title ->
                        NavigationDrawerItem(
                            label = { Text(title, color = foreground) },
                            icon = {
                                Text(listOf("⌖", "①→②", "✿", "☆")[index], color = if (modeIndex == index)
                                    Color(0xFF8AB6FF) else muted, fontSize = 16.sp)
                            },
                            selected = modeIndex == index,
                            onClick = {
                                onMode(index)
                                scope.launch { drawer.close() }
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    NavigationDrawerItem(
                        label = { Text("⚙ 設定", color = foreground) },
                        selected = false,
                        onClick = {
                            onSettings()
                            scope.launch { drawer.close() }
                        },
                    )
                    NavigationDrawerItem(
                        label = { Text("✓ 權限檢查", color = foreground) },
                        selected = false,
                        onClick = {
                            onPermissions()
                            scope.launch { drawer.close() }
                        },
                    )
                    Text(
                        "點地圖或貼上經緯度只會選擇位置，不會自動瞬移。",
                        color = muted, fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize().background(
                if (dark) Color(0xFF090C13) else Color(0xFFF4F7FC),
            ),
        ) {
            mapContent()
            if (!mapExplore) {
                BottomSheetScaffold(
                    scaffoldState = sheet,
                    containerColor = Color.Transparent,
                    sheetPeekHeight = 98.dp,
                    sheetContainerColor = if (dark) darkGlass else Color(0xDBF7FAFF),
                    sheetContentColor = if (dark) Color(0xFFF4F7FF) else Color(0xFF172538),
                    sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    sheetDragHandle = {
                        BottomSheetDefaults.DragHandle(
                            color = if (dark) Color(0xFFAFBBD2) else Color(0xFF68738C),
                        )
                    },
                    sheetContent = {
                        CompactModeHeader(
                            mode = modeTitle,
                            description = message,
                            dark = dark,
                            onExpand = { scope.launch { sheet.bottomSheetState.expand() } },
                        )
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = maxSheetBodyDp.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            modeContent()
                        }
                    },
                ) { _ -> Box(Modifier.fillMaxSize()) }
            }
            CoordinateTopBar(
                value = coordinateInput,
                onValueChange = onCoordinateChange,
                onApply = onCoordinateApply,
                onCopy = onCoordinateCopy,
                onMenu = { scope.launch { drawer.open() } },
                onMapBack = onCloseExplore,
                mapExplore = mapExplore,
                dark = dark,
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            CurrentLocationMapButton(
                onClick = onCurrentLocation,
                dark = dark,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = if (mapExplore) 22.dp else 117.dp),
            )
        }
    }
}
