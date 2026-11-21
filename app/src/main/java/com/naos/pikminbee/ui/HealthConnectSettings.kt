package com.naos.pikminbee.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.health.EstimatedStepLedger
import com.naos.pikminbee.health.EstimatedStepsSyncWorker
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.health.HealthConnectRealSteps
import com.naos.pikminbee.health.HealthConnectStepSync
import com.naos.pikminbee.health.PhysicalStepLedger
import com.naos.pikminbee.health.PhysicalStepTrackingService
import com.naos.pikminbee.health.PhysicalStepsStatus
import com.naos.pikminbee.health.PhysicalStepsSyncWorker
import com.naos.pikminbee.health.RealStepSource
import com.naos.pikminbee.health.RealStepSourcePolicy
import com.naos.pikminbee.location.LocationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optional Health Connect control. Keep summary, consents, and diagnostics
 * separate. Never request permissions or export virtual steps on screen entry.
 */
@Composable
fun HealthConnectSettings(dark: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val palette = NaosDesignSystem.palette(NaosThemeFamily.MINERAL, dark)
    val estimateLedger = remember(context) { EstimatedStepLedger(context) }
    val physicalLedger = remember(context) { PhysicalStepLedger(context) }
    val scope = rememberCoroutineScope()

    val estimates by EstimatedWalkStatus.state.collectAsState()
    val sensor by PhysicalStepsStatus.state.collectAsState()
    val walkPhase by LocationStatus.phase.collectAsState()
    val walkSteps by LocationStatus.walkEstimatedSteps.collectAsState()
    val queueRevision by EstimatedStepLedger.changes.collectAsState()
    val lastVirtualSync by HealthConnectStepSync.lastSyncResult.collectAsState()

    val available = remember(context) { HealthConnectStepSync.installed(context) }
    val hasHardware = remember(context) {
        context.getSystemService(SensorManager::class.java)
            .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
    val native = remember(available) {
        available && HealthConnectRealSteps.nativeDeviceStepsSupported()
    }
    val source = RealStepSourcePolicy.select(native, hasHardware)

    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var virtualOptIn by remember { mutableStateOf(estimateLedger.optIn()) }
    var physicalOptIn by remember { mutableStateOf(physicalLedger.optIn()) }
    var confirmVirtualSync by remember { mutableStateOf(false) }
    var awaitingWritePermission by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf<String?>(null) }

    var readAllowed by remember { mutableStateOf(false) }
    var writeAllowed by remember { mutableStateOf(false) }
    var hcToday by remember { mutableStateOf<Long?>(null) }
    var ownVirtualToday by remember { mutableStateOf<Long?>(null) }
    var virtualPending by remember { mutableStateOf(estimateLedger.pending().size) }
    var physicalPending by remember { mutableStateOf(physicalLedger.pending().size) }
    var feedback by remember { mutableStateOf("") }
    var activityAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED)
    }

    fun startSensor() {
        runCatching {
            ContextCompat.startForegroundService(context, PhysicalStepTrackingService.command(
                context, PhysicalStepTrackingService.ACTION_START,
            ))
        }.onFailure {
            feedback = "啟動手機計步失敗：" + (it.message ?: "請檢查背景權限")
            onMessage(feedback)
        }
    }

    val activityRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        activityAllowed = granted
        if (granted) startSensor()
        else {
            feedback = "未取得手機計步感測器權限"
            onMessage(feedback)
        }
    }
    val hcRequest = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ ->
        scope.launch {
            readAllowed = HealthConnectRealSteps.hasReadPermission(context)
            writeAllowed = HealthConnectStepSync.hasWritePermission(context)
            if (readAllowed) {
                hcToday = HealthConnectRealSteps.todayAggregate(context)
                ownVirtualToday = HealthConnectStepSync.todayOwnVirtualSteps(context)
            } else {
                hcToday = null
                ownVirtualToday = null
            }
            if (awaitingWritePermission) {
                awaitingWritePermission = false
                if (writeAllowed) {
                    virtualOptIn = true
                    estimateLedger.setOptIn(true)
                    EstimatedStepsSyncWorker.enqueue(context)
                    feedback = "已啟用虛擬估算同步"
                } else {
                    virtualOptIn = false
                    estimateLedger.setOptIn(false)
                    feedback = "未授權寫入；其他功能不受影響"
                }
            } else {
                if (writeAllowed && virtualOptIn) EstimatedStepsSyncWorker.enqueue(context)
                feedback = "權限已更新"
            }
            if (writeAllowed && physicalOptIn && !native)
                PhysicalStepsSyncWorker.enqueue(context)
        }
    }

    suspend fun refresh() {
        readAllowed = HealthConnectRealSteps.hasReadPermission(context)
        writeAllowed = HealthConnectStepSync.hasWritePermission(context)
        hcToday = if (readAllowed) HealthConnectRealSteps.todayAggregate(context) else null
        ownVirtualToday = if (readAllowed) HealthConnectStepSync.todayOwnVirtualSteps(context)
            else null
        virtualPending = estimateLedger.pending().size
        physicalPending = physicalLedger.pending().size
    }

    LaunchedEffect(available, queueRevision, lastVirtualSync) {
        while (true) {
            refresh()
            delay(60_000L)
        }
    }

    fun openHealthConnectAccess() {
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:com.google.android.apps.healthdata"))
        }
        runCatching { context.startActivity(intent) }.onFailure {
            feedback = "無法開啟權限管理，請至系統設定搜尋 Health Connect"
        }
    }

    if (confirmDiscard != null) {
        val discardKind = confirmDiscard
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("清除未同步紀錄？") },
            text = { Text("只刪除本機待同步的" +
                (if (discardKind == "sensor") "手機感測器步數" else "虛擬估算步數") +
                "，不會刪除 Health Connect 已寫入的資料。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (discardKind == "sensor") {
                            physicalLedger.discardPending()
                            physicalPending = 0
                        } else {
                            estimateLedger.discardPending()
                            virtualPending = 0
                        }
                    }.onSuccess {
                        feedback = "已清除未同步紀錄"
                        onMessage(feedback)
                    }.onFailure {
                        feedback = "清除失敗：" + it.message
                    }
                    confirmDiscard = null
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) {
                Text("取消")
            } },
        )
    }

    if (confirmVirtualSync) {
        AlertDialog(
            onDismissRequest = { confirmVirtualSync = false },
            title = { Text("同步模擬行走的估算步數？") },
            text = { Text("PikminBee 會把模擬路程換算的步數，以「手動輸入」標記寫入" +
                " Health Connect；這不是手機實際感測的運動。只需要寫入步數權限，" +
                "不需要同時授權讀取。你可以隨時關閉同步。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmVirtualSync = false
                    if (writeAllowed) {
                        virtualOptIn = true
                        estimateLedger.setOptIn(true)
                        EstimatedStepsSyncWorker.enqueue(context)
                        feedback = "已啟用虛擬估算同步"
                    } else if (available) {
                        awaitingWritePermission = true
                        hcRequest.launch(setOf(HealthConnectStepSync.WRITE_STEPS))
                    } else {
                        feedback = "此裝置無法使用 Health Connect"
                    }
                }) { Text("同意並開啟") }
            },
            dismissButton = {
                TextButton(onClick = { confirmVirtualSync = false }) { Text("暫不開啟") }
            },
        )
    }

    val syncLabel = HealthConnectUiPolicy.syncLabel(
        available, virtualOptIn, writeAllowed, virtualPending,
        lastVirtualSync?.note?.contains("失敗") == true,
    )

    MapGlass(dark, Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Health Connect", fontSize = 17.sp,
                    color = palette.ink, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = palette.soft,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("選用", fontSize = 11.sp, color = palette.muted,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                    }
                    if (expanded) {
                        IconButton(
                            onClick = { scope.launch { refresh() } },
                            modifier = Modifier.size(44.dp).semantics {
                                contentDescription = "更新今日步數和同步狀態"
                            },
                        ) { NaosActionIcon(NaosActionGlyph.REFRESH, palette.ink) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthStepMetric("今日總步數", HealthConnectUiPolicy.todayValue(readAllowed, hcToday),
                    if (readAllowed) "Health Connect 彙總" else "尚未授權讀取",
                    palette, Modifier.weight(1f))
                HealthStepMetric("今日已寫入估算", HealthConnectUiPolicy.todayValue(readAllowed, ownVirtualToday),
                    if (readAllowed) "PikminBee 手動紀錄" else "需讀取權限才能驗證",
                    palette, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("虛擬步數同步", fontSize = 12.sp, color = palette.muted)
                Text(syncLabel, fontSize = 12.sp, color = palette.ink,
                    fontWeight = FontWeight.SemiBold)
            }

            if (!expanded) {
                TextButton(onClick = { expanded = true }) {
                    Text("查看與管理", color = palette.accent)
                }
            } else {
                Text("兩個數字來自不同統計方式，不可直接相加。",
                    color = palette.muted, fontSize = 11.sp)
                HorizontalDivider(color = palette.stroke)

                Text("功能與權限", fontSize = 15.sp,
                    color = palette.ink, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("查看今日步數", fontSize = 14.sp, color = palette.ink)
                        Text(if (readAllowed) "已允許讀取" else "需要時才要求讀取授權",
                            fontSize = 11.sp, color = palette.muted)
                    }
                    if (!readAllowed && available) {
                        Button(onClick = {
                            hcRequest.launch(setOf(HealthConnectRealSteps.READ_STEPS))
                        }) { Text("授權讀取", fontSize = 12.sp) }
                    }
                }

                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("同步虛擬估算", fontSize = 14.sp, color = palette.ink)
                        Text("以手動紀錄寫入；不是實際運動",
                            fontSize = 11.sp, color = palette.muted)
                    }
                    Switch(
                        checked = virtualOptIn,
                        enabled = available,
                        onCheckedChange = { enable ->
                            if (enable) {
                                confirmVirtualSync = true
                            } else {
                                virtualOptIn = false
                                estimateLedger.setOptIn(false)
                                feedback = "已停止同步，既有紀錄仍保留"
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "同步虛擬估算步數"
                        },
                    )
                }
                if (virtualOptIn && available && !writeAllowed) {
                    Button(onClick = {
                        hcRequest.launch(setOf(HealthConnectStepSync.WRITE_STEPS))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("重新授權寫入步數")
                    }
                }

                HorizontalDivider(color = palette.stroke)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("待同步紀錄", color = palette.ink, fontSize = 13.sp)
                        Text("$virtualPending 筆", color = palette.muted, fontSize = 12.sp)
                    }
                    if (HealthConnectUiPolicy.canSyncNow(
                            available, virtualOptIn, writeAllowed, virtualPending)) {
                        TextButton(onClick = {
                            scope.launch {
                                val result = HealthConnectStepSync.syncPending(context)
                                virtualPending = result.remaining
                                feedback = result.note
                                onMessage(result.note)
                            }
                        }) { Text("立即同步") }
                    }
                }
                if (walkPhase == WalkPhase.MOVING || walkPhase == WalkPhase.PAUSED) {
                    Text((if (walkPhase == WalkPhase.PAUSED) "已暫停" else "行走中") +
                        " · 本次估算 $walkSteps 步",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = palette.ink)
                }
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "收起進階資訊" else "進階資訊與權限管理")
                }
                if (showAdvanced) {
                    HorizontalDivider(color = palette.stroke)
                    Text("資料明細", color = palette.ink, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    HealthDetailLine("本機歷次估算", "${estimates.estimatedSteps} 步", palette)
                    HealthDetailLine("實體步數來源", when (source) {
                        RealStepSource.HC_ON_DEVICE -> "Android Health Connect"
                        RealStepSource.PHONE_STEP_SENSOR -> "手機計步感測器"
                        RealStepSource.UNAVAILABLE -> "尚無可用來源"
                    }, palette)
                    HealthDetailLine("讀取權限", if (readAllowed) "已授權" else "未授權", palette)
                    HealthDetailLine("寫入權限", if (writeAllowed) "已授權" else "未授權", palette)
                    lastVirtualSync?.let {
                        Text("最近同步：${it.note}", color = palette.muted, fontSize = 12.sp)
                    }
                    if (feedback.isNotBlank()) {
                        Text(feedback, color = palette.muted, fontSize = 12.sp)
                    }
                    if (virtualPending > 0) {
                        TextButton(onClick = { confirmDiscard = "virtual" }) {
                            Text("清除未同步虛擬紀錄")
                        }
                    }

                    if (source == RealStepSource.PHONE_STEP_SENSOR) {
                        HorizontalDivider(color = palette.stroke)
                        Text("本機實際計步（進階）", color = palette.ink,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("本機今日 ${physicalLedger.todaysCount()} 步",
                            color = palette.ink, fontSize = 13.sp)
                        Text(sensor.note, color = palette.muted, fontSize = 11.sp)
                        Button(onClick = {
                            if (sensor.tracking) {
                                context.startService(PhysicalStepTrackingService.command(
                                    context, PhysicalStepTrackingService.ACTION_STOP))
                            } else if (!activityAllowed) {
                                activityRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            } else startSensor()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (sensor.tracking) "停止手機實際計步" else "開始手機實際計步")
                        }
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("同步手機感測器紀錄",
                                color = palette.ink, fontSize = 13.sp,
                                modifier = Modifier.weight(1f))
                            Switch(checked = physicalOptIn, onCheckedChange = {
                                physicalOptIn = it
                                physicalLedger.setOptIn(it)
                                if (it && writeAllowed)
                                    PhysicalStepsSyncWorker.enqueue(context)
                                onMessage(if (it) "已選擇同步實際計步" else "已停止感測步數同步")
                            })
                        }
                        Text("待同步感測器紀錄：$physicalPending 筆",
                            color = palette.muted, fontSize = 12.sp)
                        if (physicalPending > 0) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val result = com.naos.pikminbee.health.PhysicalStepSync
                                            .syncPending(context)
                                        physicalPending = result.remaining
                                        feedback = result.note
                                        onMessage(result.note)
                                    }
                                },
                                enabled = physicalOptIn && writeAllowed && available,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("同步感測器紀錄") }
                            TextButton(onClick = { confirmDiscard = "sensor" }) {
                                Text("清除未同步感測器紀錄")
                            }
                        }
                    } else if (source == RealStepSource.UNAVAILABLE) {
                        Text("未偵測到計步來源；可檢查手錶或其他健康 App 的資料。",
                            color = palette.muted, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = ::openHealthConnectAccess,
                        enabled = available,
                    ) { Text("管理／撤銷 Health Connect 權限") }
                    Text("關閉同步不會刪除已寫入 Health Connect 的紀錄。",
                        color = palette.muted, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    expanded = false
                    showAdvanced = false
                }) { Text("收起設定") }
            }
        }
    }
}

@Composable
private fun HealthStepMetric(
    label: String,
    value: String,
    hint: String,
    palette: NaosPalette,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = palette.soft,
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 11.sp, color = palette.muted, maxLines = 1)
            Text(value, fontSize = 22.sp, color = palette.ink,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text(hint, fontSize = 10.sp, color = palette.muted, maxLines = 2)
        }
    }
}

@Composable
private fun HealthDetailLine(label: String, value: String, palette: NaosPalette) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top) {
        Text(label, color = palette.muted, fontSize = 12.sp)
        Text(value, color = palette.ink, fontSize = 12.sp)
    }
}
