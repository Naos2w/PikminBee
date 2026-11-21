package com.naos.pikminbee.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.naos.pikminbee.health.EstimatedStepLedger
import com.naos.pikminbee.location.LocationStatus
import com.naos.pikminbee.domain.WalkPhase
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native Health Connect real steps and virtual-route estimates must not be
 * treated as the same activity. No permission request or HC write on entry.
 */
@Composable
fun HealthConnectSettings(dark: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val estimateLedger = remember(context) { EstimatedStepLedger(context) }
    val physicalLedger = remember(context) { PhysicalStepLedger(context) }
    val scope = rememberCoroutineScope()
    val estimates by EstimatedWalkStatus.state.collectAsState()
    val sensor by PhysicalStepsStatus.state.collectAsState()
    val walkPhase by LocationStatus.phase.collectAsState()
    val walkSteps by LocationStatus.walkEstimatedSteps.collectAsState()
    val queueRevision by EstimatedStepLedger.changes.collectAsState()
    val lastVirtualSync by HealthConnectStepSync.lastSyncResult.collectAsState()

    val foreground = if (dark) Color(0xFFF3F7FF) else Color(0xFF18263A)
    val muted = if (dark) Color(0xFFC6D3E6) else Color(0xFF52627A)
    val available = remember(context) { HealthConnectStepSync.installed(context) }
    val hasHardware = remember(context) {
        context.getSystemService(SensorManager::class.java)
            .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
    val native = remember(available) {
        available && HealthConnectRealSteps.nativeDeviceStepsSupported()
    }
    val source = RealStepSourcePolicy.select(native, hasHardware)

    var virtualOptIn by remember { mutableStateOf(estimateLedger.optIn()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSensorAdvanced by rememberSaveable { mutableStateOf(false) }
    var confirmVirtualSync by remember { mutableStateOf(false) }
    var awaitingWritePermission by remember { mutableStateOf(false) }
    var physicalOptIn by remember { mutableStateOf(physicalLedger.optIn()) }
    var readAllowed by remember { mutableStateOf(false) }
    var writeAllowed by remember { mutableStateOf(false) }
    var hcToday by remember { mutableStateOf<Long?>(null) }
    var ownVirtualToday by remember { mutableStateOf<Long?>(null) }
    var virtualPending by remember { mutableStateOf(estimateLedger.pending().size) }
    var physicalPending by remember { mutableStateOf(physicalLedger.pending().size) }
    var feedback by remember { mutableStateOf("尚未檢查 Health Connect 授權") }
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    var activityAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED)
    }

    fun startSensor() {
        runCatching {
            ContextCompat.startForegroundService(context, PhysicalStepTrackingService.command(
                context, PhysicalStepTrackingService.ACTION_START
            ))
        }.onFailure {
            feedback = "啟動手機計步失敗：" + (it.message ?: "請檢查背景權限")
            onMessage(feedback)
        }
    }

    val activityRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        activityAllowed = granted
        if (granted) startSensor()
        else {
            feedback = "沒有 ACTIVITY_RECOGNITION 授權，無法使用本機感測器"
            onMessage(feedback)
        }
    }
    val hcRequest = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        scope.launch {
            readAllowed = HealthConnectRealSteps.hasReadPermission(context)
            writeAllowed = HealthConnectStepSync.hasWritePermission(context)
            if (readAllowed) {
                hcToday = HealthConnectRealSteps.todayAggregate(context)
                ownVirtualToday = HealthConnectStepSync.todayOwnVirtualSteps(context)
            }
            if (awaitingWritePermission) {
                awaitingWritePermission = false
                if (writeAllowed) {
                    virtualOptIn = true
                    estimateLedger.setOptIn(true)
                    EstimatedStepsSyncWorker.enqueue(context)
                    feedback = "已允許寫入手動估算步數"
                } else {
                    virtualOptIn = false
                    estimateLedger.setOptIn(false)
                    feedback = "尚未授權寫入，仍可正常使用地圖與行走"
                }
            } else {
                if (writeAllowed && virtualOptIn) EstimatedStepsSyncWorker.enqueue(context)
                feedback = "Health Connect 權限已更新"
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
        feedback = if (!available) "這台裝置目前無法使用 Health Connect"
            else if (readAllowed) "已讀取 Health Connect 今日彙總步數"
            else "尚未取得 READ_STEPS，無法讀取今日步數"
    }
    // A worker can complete while this page is open; refresh only on queue or
    // sync-result changes, not every GPS tick.
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
        val type = confirmDiscard
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("清除尚未同步的紀錄？") },
            text = { Text("只刪除本機等待同步的" +
                (if (type == "sensor") "實際感測器步數" else "虛擬估算步數") +
                "，不會刪除已寫入 Health Connect 的資料。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (type == "sensor") {
                            physicalLedger.discardPending()
                            physicalPending = 0
                        } else {
                            estimateLedger.discardPending()
                            virtualPending = 0
                        }
                    }.onSuccess {
                        feedback = "已清除尚未同步的本機紀錄"
                        onMessage(feedback)
                    }.onFailure {
                        feedback = "清除失敗：" + it.message
                    }
                    confirmDiscard = null
                }) { Text("確定清除") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("取消") } },
        )
    }

    if (confirmVirtualSync) {
        AlertDialog(
            onDismissRequest = { confirmVirtualSync = false },
            title = { Text("同步虛擬估算步數？") },
            text = {
                Text("選擇開啟後，PikminBee 才會把模擬路線換算的步數以「手動輸入」" +
                    "寫入 Health Connect。這不是手機實際感測到的運動。" +
                    "只需 WRITE_STEPS，不必授予 READ_STEPS。可隨時關閉同步。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmVirtualSync = false
                    if (writeAllowed) {
                        virtualOptIn = true
                        estimateLedger.setOptIn(true)
                        EstimatedStepsSyncWorker.enqueue(context)
                        feedback = "已開啟手動估算同步"
                    } else if (available) {
                        awaitingWritePermission = true
                        hcRequest.launch(setOf(HealthConnectStepSync.WRITE_STEPS))
                    } else {
                        feedback = "此裝置無法使用 Health Connect，仍可使用本機估算"
                    }
                }) { Text("同意並繼續") }
            },
            dismissButton = {
                TextButton(onClick = { confirmVirtualSync = false }) { Text("先不啟用") }
            },
        )
    }

    if (!expanded) {
        MapGlass(dark, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Health Connect · 選用", fontSize = 17.sp,
                    color = foreground, fontWeight = FontWeight.SemiBold)
                Text("不連結也能使用地圖、瞬移、路徑與本機步數估算。",
                    fontSize = 13.sp, color = muted)
                Text(
                    when {
                        !available -> "此裝置暫無可用的 Health Connect"
                        virtualOptIn && writeAllowed -> "已啟用虛擬估算步數同步"
                        readAllowed -> "已允許讀取今日步數"
                        else -> "未啟用 · 不會傳送虛擬步數"
                    },
                    fontSize = 12.sp, color = foreground,
                )
                Button(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (available) "設定 Health Connect" else "查看本機步數") }
            }
        }
    } else {
    MapGlass(dark, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Health Connect · 連結設定", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = foreground)
                TextButton(onClick = { expanded = false }) { Text("收起") }
                IconButton(
                    onClick = { scope.launch { refresh() } },
                    modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "更新今日步數與同步狀態" },
                ) { NaosActionIcon(NaosActionGlyph.REFRESH, foreground) }
            }

            Text("步驟 1 · 讀取今日步數（選用 READ_STEPS）",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            Text("只想查看今日步數才需要讀取權限；不影響模擬定位。",
                fontSize = 11.sp, color = muted)
            Text("今日 Health Connect 彙總步數：" +
                (hcToday?.toString() ?: "尚未取得"),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            Text("跨來源彙總可能去除重疊步數，與虛擬估算不一定相同。",
                fontSize = 11.sp, color = muted)
            Text("目前實體步數來源：" + when (source) {
                RealStepSource.HC_ON_DEVICE -> "Android 原生 Health Connect 計步"
                RealStepSource.PHONE_STEP_SENSOR -> "手機 TYPE_STEP_COUNTER 感測器"
                RealStepSource.UNAVAILABLE -> "尚無可用的本機計步來源"
            }, fontSize = 12.sp, color = foreground)

            if (available) {
                if (!readAllowed) {
                    Button(onClick = {
                        hcRequest.launch(setOf(HealthConnectRealSteps.READ_STEPS))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("允許讀取今日步數")
                    }
                } else {
                    Text("已允許讀取今日步數", color = foreground, fontSize = 12.sp)
                }
                Text(if (native)
                    "真實行走由系統記錄；模擬 GPS 不會觸發計步感測器。"
                    else "此裝置不符合 Android 原生計步條件；可使用手機計步感測器相容模式。",
                    fontSize = 11.sp, color = muted)

            } else {
                Text("Health Connect 不可用；可先在本機檢視感測器步數。",
                    fontSize = 12.sp, color = muted)
            }

            if (source == RealStepSource.PHONE_STEP_SENSOR) {
                TextButton(onClick = { showSensorAdvanced = !showSensorAdvanced }) {
                    Text(if (showSensorAdvanced) "收起本機感測器設定"
                        else "本機實際計步（進階／選用）")
                }
                if (showSensorAdvanced) {
                Text("手機實際感測器步數（本機今日觀察）：" +
                    physicalLedger.todaysCount() + " 步",
                    color = foreground, fontSize = 13.sp)
                Text(sensor.note, fontSize = 11.sp, color = muted)
                Text("舊版 Android 的感測器僅在使用者啟動計步前景服務期間記錄；系統強制停止或裝置重開機可能造成缺口。",
                    fontSize = 11.sp, color = muted)
                Button(onClick = {
                    if (sensor.tracking) {
                        context.startService(PhysicalStepTrackingService.command(context,
                            PhysicalStepTrackingService.ACTION_STOP))
                    } else if (!activityAllowed) {
                        activityRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    } else startSensor()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sensor.tracking) "停止手機實際計步" else "開始手機實際計步")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("同步本機感測器步數", fontSize = 13.sp, color = foreground)
                        Text("僅在沒有系統原生計步時使用，授權後才寫入，避免重複上傳。",
                            fontSize = 11.sp, color = muted)
                    }
                    Switch(checked = physicalOptIn, onCheckedChange = {
                        physicalOptIn = it
                        physicalLedger.setOptIn(it)
                        if (it && writeAllowed) PhysicalStepsSyncWorker.enqueue(context)
                        onMessage(if (it) "已選擇同步真實手機感測步數" else "已關閉感測步數同步")
                    })
                }
                Text("待同步感測器紀錄：" + physicalPending + " 筆",
                    color = muted, fontSize = 12.sp)
                if (physicalPending > 0) {
                    Button(
                        enabled = physicalOptIn && writeAllowed && available,
                        onClick = {
                            scope.launch {
                                val result = com.naos.pikminbee.health.PhysicalStepSync.syncPending(context)
                                physicalPending = result.remaining
                                feedback = result.note
                                onMessage(result.note)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("同步待處理的感測器步數") }
                    TextButton(onClick = { confirmDiscard = "sensor" },
                        modifier = Modifier.fillMaxWidth()) { Text("清除未同步感測步數") }
                }
                }
            } else if (source == RealStepSource.UNAVAILABLE) {
                Text("未偵測到手機硬體計步器，也未提供內建 HC 計步；請確認手錶或其他健康 App 是否已向 Health Connect 提供資料。",
                    fontSize = 12.sp, color = muted)
            }

            Text("本機虛擬行走累計估算：" + estimates.estimatedSteps + " 步",
                fontSize = 14.sp, color = foreground)
            Text("這是歷次模擬路程的累計，不是今日 Health Connect 步數。",
                fontSize = 11.sp, color = muted)
            Text("PikminBee 今日已寫入虛擬估算：" +
                (ownVirtualToday?.toString() ?: "尚未取得") + " 步",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            Text("此處只計算 PikminBee 寫入的手動估算紀錄。" +
                "Health Connect 彙總會處理重疊來源，不保證等量增加。",
                fontSize = 11.sp, color = muted)
            if (walkPhase == WalkPhase.MOVING || walkPhase == WalkPhase.PAUSED) {
                Text(
                    (if (walkPhase == WalkPhase.PAUSED) "已暫停" else "行走中") +
                        " · 本次估算 ${walkSteps} 步",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
                Text("啟用同步後定期保存；暫停與停止會保存該段估算，背景寫入可能延遲。",
                    fontSize = 11.sp, color = muted)
            }
            Text("步驟 2 · 寫入虛擬估算（選用 WRITE_STEPS）",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("同步虛擬路徑估算步數", fontSize = 13.sp, color = foreground)
                    Text("明確開啟後才同步手動估算，不視為實際運動。",
                        fontSize = 11.sp, color = muted)
                }
                Switch(checked = virtualOptIn, enabled = available, onCheckedChange = { enable ->
                    if (enable) {
                        // Explicit rationale and scoped permission before opting in.
                        confirmVirtualSync = true
                    } else {
                        virtualOptIn = false
                        estimateLedger.setOptIn(false)
                        feedback = "已停止同步；Health Connect 裡已寫入的紀錄不會自動刪除"
                    }
                })
            }

            Text("寫入步數授權：" + if (writeAllowed) "已授權" else "尚未授權",
                fontSize = 12.sp, color = muted)
            if (available && virtualOptIn && !writeAllowed) {
                Button(onClick = {
                    hcRequest.launch(setOf(HealthConnectStepSync.WRITE_STEPS))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("重新授權寫入步數")
                }
            }
            Text("待同步虛擬步行紀錄：" + virtualPending + " 筆",
                fontSize = 12.sp, color = muted)
            lastVirtualSync?.let { result ->
                Text("上次寫入結果：" + result.note,
                    fontSize = 12.sp, color = muted)
            }
            if (virtualPending > 0) {
                Button(
                    enabled = virtualOptIn && available && writeAllowed,
                    onClick = {
                        scope.launch {
                            val result = HealthConnectStepSync.syncPending(context)
                            virtualPending = result.remaining
                            feedback = result.note
                            onMessage(result.note)
                        }
                    }, modifier = Modifier.fillMaxWidth(),
                ) { Text("同步待處理的虛擬步數") }
                TextButton(onClick = { confirmDiscard = "virtual" },
                    modifier = Modifier.fillMaxWidth()) { Text("清除未同步虛擬步數") }
            }
            TextButton(onClick = ::openHealthConnectAccess, enabled = available) {
                Text("管理或撤銷 Health Connect 權限")
            }
            Text(feedback, fontSize = 11.sp, color = muted)
            Text("這是選用功能；不授權也可使用路線。關閉同步不會刪除已寫入紀錄。",
                fontSize = 11.sp, color = muted)
        }
    }
    }
}
