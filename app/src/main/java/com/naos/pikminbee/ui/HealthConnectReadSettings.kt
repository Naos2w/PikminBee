package com.naos.pikminbee.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naos.pikminbee.health.EstimatedStepLedger
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.health.HealthConnectRealSteps
import com.naos.pikminbee.health.HealthDisplayPreferences
import com.naos.pikminbee.health.PhysicalStepLedger
import com.naos.pikminbee.health.PhysicalStepSync
import com.naos.pikminbee.health.PhysicalStepTrackingService
import com.naos.pikminbee.health.PhysicalStepsStatus
import com.naos.pikminbee.health.RealSensorSyncWorker
import com.naos.pikminbee.health.RealStepSource
import com.naos.pikminbee.health.RealStepSourcePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** Restore the independent, consent-based real step features without exporting route estimates. */
@Composable
fun HealthConnectReadSettings(foreground: Color, muted: Color) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val ledger = remember(context) { PhysicalStepLedger(context) }
    val estimates = remember(context) { EstimatedStepLedger(context) }
    val displayPrefs = remember(context) { HealthDisplayPreferences(context) }
    val sensor by PhysicalStepsStatus.state.collectAsState()
    val estimate by EstimatedWalkStatus.state.collectAsState()
    var status by remember { mutableStateOf(HealthConnectRealSteps.status(context)) }
    var result by remember { mutableStateOf<HealthConnectRealSteps.Result?>(null) }
    var readAllowed by remember { mutableStateOf(false) }
    var writeAllowed by remember { mutableStateOf(false) }
    var activityAllowed by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var job by remember { mutableStateOf<Job?>(null) }
    var loading by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncEnabled by remember { mutableStateOf(ledger.optIn()) }
    var overlayEnabled by remember { mutableStateOf(displayPrefs.showOverlaySteps()) }
    var pending by remember { mutableStateOf(ledger.pendingCount()) }
    var recorded by remember { mutableStateOf(ledger.todaysCount()) }
    var syncNote by remember { mutableStateOf(ledger.lastSyncNote()) }
    var feedback by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    var legacyPending by remember { mutableStateOf(estimates.pending().size) }
    var legacySynced by remember { mutableStateOf(estimates.all().count { it.synced }) }
    val stored = context.getSharedPreferences("pikminbee_estimated_steps", Context.MODE_PRIVATE)
    val storedCount = runCatching { stored.getLong("estimated_steps_total", 0L) }.getOrDefault(0L)
    val totalEstimate = maxOf(0L, storedCount, estimate.estimatedSteps)
    val native = HealthConnectRealSteps.nativeDeviceStepsSupported()
    val hasSensor = remember(context) {
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
    val source = RealStepSourcePolicy.select(native, hasSensor)
    fun activityPermissionGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    fun clearRead() {
        job?.cancel()
        job = null
        loading = false
        result = null
    }
    fun refreshLedger() {
        pending = ledger.pendingCount()
        recorded = ledger.todaysCount()
        syncNote = ledger.lastSyncNote()
        syncEnabled = ledger.optIn()
        legacyPending = estimates.pending().size
        legacySynced = estimates.all().count { it.synced }
    }
    fun read() {
        clearRead()
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        loading = true
        job = scope.launch {
            try {
                val value = HealthConnectRealSteps.todayAggregate(context)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) result = value
            } finally { loading = false }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                resumed = false
                clearRead()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); clearRead() }
    }
    LaunchedEffect(resumed) {
        while (resumed) {
            status = HealthConnectRealSteps.status(context)
            readAllowed = HealthConnectRealSteps.hasPermission(context, HealthConnectRealSteps.READ_STEPS)
            writeAllowed = HealthConnectRealSteps.hasPermission(context, HealthConnectRealSteps.WRITE_STEPS)
            activityAllowed = activityPermissionGranted()
            if (!readAllowed && result is HealthConnectRealSteps.Result.Available) clearRead()
            refreshLedger()
            delay(5_000L)
        }
    }
    val hcRequest = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (HealthConnectRealSteps.READ_STEPS in granted) { readAllowed = true; read() }
        if (HealthConnectRealSteps.WRITE_STEPS in granted) {
            writeAllowed = true
            feedback = "已授權寫入；仍需主動開啟真實感測步數同步。"
        }
    }
    val activityRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activityAllowed = granted
        feedback = if (granted) "已授權身體活動；請按開始計步。" else "未授權，未開始計步。"
    }
    fun number(value: Long) = NumberFormat.getIntegerInstance().format(value)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("今日步數（HC 彙總）", color = foreground, fontWeight = FontWeight.SemiBold)
        val count = (result as? HealthConnectRealSteps.Result.Available)?.count
        Text(if (count != null) number(count) + " 步" else "— 步",
            color = foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("HC 彙總可能包含手機、手錶、其他 App 或手動紀錄；不與本機感測或路線估算相加。", color = muted, fontSize = 13.sp)
        Text(when (result) {
            HealthConnectRealSteps.Result.PermissionRequired -> "尚未授予讀取權限或已撤銷。"
            HealthConnectRealSteps.Result.ProviderUnavailable -> "Health Connect 尚未提供服務。"
            HealthConnectRealSteps.Result.ReadFailed -> "查詢失敗，請稍後重試。"
            is HealthConnectRealSteps.Result.Available -> "已讀取本次彙總；離開此頁不保留數值。"
            null -> if (loading) "正在查詢…" else "授權後可重新讀取今日步數。"
        }, color = muted, fontSize = 12.sp)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            Button(enabled = !loading, onClick = {
                runCatching { hcRequest.launch(setOf(HealthConnectRealSteps.READ_STEPS)) }
                    .onFailure { feedback = "無法開啟讀取授權。" }
            }) { Text(if (readAllowed) "管理讀取授權" else "授權讀取步數") }
            TextButton(enabled = !loading && readAllowed, onClick = ::read) { Text("重新讀取今日步數") }
        }
        TextButton(onClick = {
            clearRead()
            runCatching { context.startActivity(HealthConnectRealSteps.settingsIntent()) }
                .onFailure { feedback = "目前無法開啟 Health Connect 設定。" }
        }) { Text("管理／撤銷 Health Connect 權限") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("懸浮視窗顯示今日步數", modifier = Modifier.weight(1f), color = foreground)
            Switch(checked = overlayEnabled, onCheckedChange = {
                displayPrefs.setShowOverlaySteps(it); overlayEnabled = it
            })
        }
        Text("只在展開懸浮窗時更新；若 Android 不允許 HC 查詢，顯示獨立本機計步或無資料，不額外要求背景讀取。", color = muted, fontSize = 12.sp)
        HorizontalDivider()
        Text("真實手機計步", color = foreground, fontWeight = FontWeight.SemiBold)
        Text(when (source) {
            RealStepSource.HC_ON_DEVICE -> "系統 Health Connect 原生計步（避免重複記錄）"
            RealStepSource.PHONE_STEP_SENSOR -> "手機硬體步數感測器"
            RealStepSource.UNAVAILABLE -> "裝置未提供可用步數感測來源"
        }, color = muted, fontSize = 13.sp)
        Text("本機今日記錄：${number(recorded.coerceAtLeast(0L))} 步", color = foreground)
        if (source == RealStepSource.PHONE_STEP_SENSOR) {
            Text(sensor.note, color = muted, fontSize = 12.sp)
            Button(onClick = {
                if (sensor.tracking) context.stopService(PhysicalStepTrackingService.command(context, PhysicalStepTrackingService.ACTION_STOP))
                else if (!activityPermissionGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    activityRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                else runCatching { ContextCompat.startForegroundService(context,
                    PhysicalStepTrackingService.command(context, PhysicalStepTrackingService.ACTION_START)) }
                    .onFailure { feedback = "無法啟動手機計步。" }
            }) {
                Text(if (sensor.tracking) "停止手機實際計步" else if (!activityAllowed) "授權身體活動" else "開始手機實際計步")
            }
            Text("僅在你主動開始後記錄，使用可停止的前景服務，不會自行重啟。", color = muted, fontSize = 12.sp)
        }
        if (source == RealStepSource.PHONE_STEP_SENSOR && status == HealthConnectClient.SDK_AVAILABLE) {
            TextButton(onClick = {
                runCatching { hcRequest.launch(setOf(HealthConnectRealSteps.WRITE_STEPS)) }
                    .onFailure { feedback = "無法開啟寫入授權。" }
            }) { Text(if (writeAllowed) "管理寫入授權" else "授權寫入真實步數") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("同步真實手機感測器紀錄", modifier = Modifier.weight(1f), color = foreground)
                Switch(checked = syncEnabled, enabled = writeAllowed || syncEnabled, onCheckedChange = { next ->
                    if (next && !writeAllowed) return@Switch
                    ledger.setOptIn(next)
                    syncEnabled = next
                    if (next) runCatching { RealSensorSyncWorker.enqueue(context) }
                    else runCatching { RealSensorSyncWorker.cancel(context) }
                })
            }
            Text("開啟後自動同步新版本的真實感測器紀錄，可隨時關閉；不重傳舊版或模擬紀錄。", color = muted, fontSize = 12.sp)
            TextButton(enabled = syncEnabled && writeAllowed && pending > 0 && !syncBusy, onClick = {
                syncBusy = true
                scope.launch {
                    try {
                        feedback = PhysicalStepSync.syncPending(context).note
                        refreshLedger()
                    } finally { syncBusy = false }
                }
            }) { Text(if (syncBusy) "同步中…" else "立即同步真實紀錄") }
        }
        Text("待同步感測器紀錄：$pending 筆", color = foreground, fontSize = 13.sp)
        Text("最近同步：$syncNote", color = muted, fontSize = 12.sp)
        TextButton(enabled = pending > 0 && !sensor.tracking && !syncEnabled && !syncBusy,
            onClick = { confirmDiscard = "sensor" }) { Text("清除未同步感測器紀錄") }
        HorizontalDivider()
        Text("本機路線估算（非實際運動）", color = foreground, fontWeight = FontWeight.SemiBold)
        Text("歷次累計估算：${number(totalEstimate)} 步", color = foreground)
        Text("舊版估算紀錄：$legacyPending 筆未同步／$legacySynced 筆歷史已同步；不再寫入 HC。", color = muted, fontSize = 12.sp)
        TextButton(enabled = legacyPending > 0, onClick = { confirmDiscard = "estimate" }) {
            Text("清除舊版未同步估算紀錄")
        }
        Text("舊版感測紀錄：${ledger.legacyRecordCount()} 筆；舊版待同步 ${ledger.legacyPendingCount()} 筆。保留於本機，不會自動重傳或清除。", color = muted, fontSize = 12.sp)
        if (feedback.isNotBlank()) Text(feedback, color = muted, fontSize = 12.sp)
    }
    confirmDiscard?.let { kind ->
        AlertDialog(onDismissRequest = { confirmDiscard = null },
            title = { Text("清除尚未同步的本機紀錄？") },
            text = { Text("只清除選定類型的未同步紀錄。既有 HC 資料及其他歷史紀錄不會刪除。") },
            confirmButton = { TextButton(onClick = {
                confirmDiscard = null
                scope.launch {
                    val ok = if (kind == "sensor") PhysicalStepSync.discardPending(context)
                        else withContext(Dispatchers.IO) { runCatching { estimates.discardPending() }.isSuccess }
                    feedback = if (ok) "已清除指定未同步紀錄。" else "無法清除；請停止計步與同步後再試。"
                    refreshLedger()
                }
            }) { Text("確認清除") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("取消") } })
    }
}
