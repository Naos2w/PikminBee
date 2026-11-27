package com.naos.pikminbee.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
fun HealthConnectReadSettings(palette: NaosPalette) {
    val foreground = palette.ink
    val muted = palette.muted
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val ledger = remember(context) { PhysicalStepLedger(context) }
    val estimates = remember(context) { EstimatedStepLedger(context) }
    val displayPrefs = remember(context) { HealthDisplayPreferences(context) }
    val sensor by PhysicalStepsStatus.state.collectAsState()
    val estimate by EstimatedWalkStatus.state.collectAsState()
    val routeEstimate by com.naos.pikminbee.location.LocationStatus.walkEstimatedSteps.collectAsState()
    val routePhase by com.naos.pikminbee.location.LocationStatus.phase.collectAsState()
    val routeMode by com.naos.pikminbee.location.LocationStatus.mode.collectAsState()
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(HealthConnectRealSteps.status(context)) }
    var result by remember { mutableStateOf<HealthConnectRealSteps.Result?>(null) }
    var readAllowed by remember { mutableStateOf(false) }
    var writeAllowed by remember { mutableStateOf(false) }
    var activityAllowed by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var job by remember { mutableStateOf<Job?>(null) }
    val readCache = remember { FloatingDailyStepsCache() }
    var readGeneration by remember { mutableStateOf(0L) }
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
        readGeneration++
        job?.cancel()
        job = null
        readCache.invalidate()
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
    fun read(force: Boolean = true) {
        if (!readAllowed || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val day = FloatingDayKey.now()
        if (readCache.ensurePeriod(day)) clearRead()
        if (job?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (result is HealthConnectRealSteps.Result.Available && readCache.currentValue(day, now) == null)
            result = null
        val ticket = readCache.begin(day, now, force) ?: return
        if (force) result = null
        val generation = ++readGeneration
        loading = true
        job = scope.launch {
            try {
                val value = HealthConnectRealSteps.todayAggregate(context)
                val count = (value as? HealthConnectRealSteps.Result.Available)?.count
                if (readGeneration == generation && readAllowed &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    readCache.complete(ticket, day, FloatingDayKey.now(), SystemClock.elapsedRealtime(), count)) {
                    result = value
                }
            } finally { if (readGeneration == generation) loading = false }
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
            if (!resumed || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) break
            if (readAllowed) read(force = false) else clearRead()
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
    val providerAvailable = status == HealthConnectClient.SDK_AVAILABLE
    val canWrite = source == RealStepSource.PHONE_STEP_SENSOR && providerAvailable
    val count = (result as? HealthConnectRealSteps.Result.Available)?.count
    val daily = HealthDashboardPolicy.dailyMetric(count,
        recorded.takeIf { it > 0L || sensor.tracking }, source)
    val currentRoute = routeEstimate.takeIf {
        routeMode != null && routePhase in listOf(
            com.naos.pikminbee.domain.WalkPhase.MOVING,
            com.naos.pikminbee.domain.WalkPhase.PAUSED,
            com.naos.pikminbee.domain.WalkPhase.PARKED)
    }
    fun requestHealthPermission(permission: String) {
        runCatching { hcRequest.launch(setOf(permission)) }
            .onFailure { feedback = "無法開啟 Health Connect 授權。" }
    }
    fun toggleTracking() {
        if (sensor.tracking) {
            context.stopService(PhysicalStepTrackingService.command(context, PhysicalStepTrackingService.ACTION_STOP))
        } else if (source == RealStepSource.PHONE_STEP_SENSOR) {
            if (!activityPermissionGranted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                activityRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else runCatching { ContextCompat.startForegroundService(context,
                PhysicalStepTrackingService.command(context, PhysicalStepTrackingService.ACTION_START)) }
                .onFailure { feedback = "無法啟動手機計步。" }
        }
    }
    fun changeSync(next: Boolean) {
        if (next && (!writeAllowed || !canWrite)) return
        ledger.setOptIn(next)
        syncEnabled = next
        if (next) runCatching { RealSensorSyncWorker.enqueue(context) }
        else runCatching { RealSensorSyncWorker.cancel(context) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HealthCard(palette) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(daily.title, color = muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(enabled = !loading && providerAvailable && readAllowed, onClick = { read() }) {
                    NaosActionIcon(NaosActionGlyph.REFRESH, if (readAllowed) palette.accent else muted,
                        Modifier.semantics { contentDescription = "重新讀取今日步數" })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(daily.count?.let(::number) ?: "—", color = foreground,
                    fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("步", color = muted, fontSize = 14.sp)
            }
            Text(daily.sourceLabel, color = palette.accent, fontSize = 12.sp)
            val note = when (result) {
                HealthConnectRealSteps.Result.PermissionRequired -> "尚未授權讀取"
                HealthConnectRealSteps.Result.ProviderUnavailable -> "Health Connect 不可用"
                HealthConnectRealSteps.Result.ReadFailed -> "查詢失敗，請重試"
                is HealthConnectRealSteps.Result.Available -> "已更新 · 頁面開啟時自動刷新"
                null -> if (loading) "讀取中…" else if (readAllowed) "準備更新" else "授權後可查詢 HC 彙總"
            }
            Text(note, color = muted, fontSize = 12.sp)
            if (!readAllowed && providerAvailable) TextButton(
                onClick = { requestHealthPermission(HealthConnectRealSteps.READ_STEPS) }) { Text("授權讀取") }
        }

        Text("計步與同步", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        HealthCard(palette) {
            HealthRow(palette, NaosActionGlyph.STEPS, "步數來源", HealthDashboardPolicy.sourceLabel(source)) {
                if (source == RealStepSource.PHONE_STEP_SENSOR || sensor.tracking) {
                    TextButton(onClick = ::toggleTracking) {
                        Text(if (sensor.tracking) "停止" else if (!activityAllowed) "授權" else "開始")
                    }
                }
            }
            if (source == RealStepSource.PHONE_STEP_SENSOR || sensor.tracking)
                Text(sensor.note, color = muted, fontSize = 11.sp)
            HorizontalDivider(color = palette.stroke)
            HealthRow(palette, NaosActionGlyph.REFRESH, "健康資料同步",
                HealthDashboardPolicy.syncLabel(source, syncEnabled, writeAllowed, providerAvailable, pending, syncBusy)) {
                if (canWrite || syncEnabled) {
                    Switch(checked = syncEnabled,
                        enabled = syncEnabled || (canWrite && writeAllowed),
                        onCheckedChange = ::changeSync)
                }
            }
            if (canWrite && !writeAllowed) TextButton(onClick = { detail = "permissions" }) {
                Text("設定寫入權限")
            }
            HorizontalDivider(color = palette.stroke)
            HealthRow(palette, NaosActionGlyph.MORE, "懸浮視窗步數", "預設顯示 · 展開時更新") {
                Switch(checked = overlayEnabled, onCheckedChange = {
                    displayPrefs.setShowOverlaySteps(it); overlayEnabled = it
                })
            }
        }

        Text("路線統計", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        HealthCard(palette) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("本次估算", color = muted, fontSize = 12.sp)
                    Text(currentRoute?.let(::number) ?: "—", color = foreground,
                        fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text("步", color = muted, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("歷次累計估算", color = muted, fontSize = 12.sp)
                    Text(number(totalEstimate), color = foreground, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text("步", color = muted, fontSize = 11.sp)
                }
            }
            Text("獨立路線估算，非實際運動，不寫入 HC。", color = muted, fontSize = 11.sp)
        }
        HealthCard(palette) {
            HealthNavigationRow(palette, NaosActionGlyph.DONE, "權限管理") { detail = "permissions" }
            HorizontalDivider(color = palette.stroke)
            HealthNavigationRow(palette, NaosActionGlyph.CLOCK, "同步紀錄") { detail = "sync" }
            HorizontalDivider(color = palette.stroke)
            HealthNavigationRow(palette, NaosActionGlyph.MORE, "進階資料管理") { detail = "advanced" }
        }
        if (feedback.isNotBlank()) Text(feedback, color = muted, fontSize = 12.sp)
    }

    if (detail != null && confirmDiscard == null) {
        val page = detail
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = palette.surface,
            title = { Text(when (page) {
                "permissions" -> "權限管理"
                "sync" -> "同步紀錄"
                else -> "進階資料管理"
            }, color = foreground) },
            text = {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (page) {
                        "permissions" -> {
                            Text("Health Connect 彙總包含不同來源；不與本機計步或路線估算相加。", color = muted)
                            Text("讀取權限 · " + if (readAllowed) "已授權" else "未授權", color = foreground)
                            TextButton(enabled = providerAvailable, onClick = { requestHealthPermission(HealthConnectRealSteps.READ_STEPS) }) {
                                Text("管理讀取授權")
                            }
                            if (source == RealStepSource.PHONE_STEP_SENSOR || sensor.tracking) {
                                Text("身體活動 · " + if (activityAllowed) "已授權" else "未授權", color = foreground)
                                if (!activityAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                    TextButton(onClick = { activityRequest.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) {
                                        Text("授權身體活動")
                                    }
                                Text("手機感測器只在主動開始後記錄；可隨時停止。", color = muted)
                            }
                            if (canWrite || writeAllowed || syncEnabled) {
                                Text("寫入權限 · " + if (writeAllowed) "已授權" else "未授權", color = foreground)
                                TextButton(enabled = canWrite, onClick = { requestHealthPermission(HealthConnectRealSteps.WRITE_STEPS) }) {
                                    Text("管理真實步數寫入授權")
                                }
                                Text("只同步新收集的真實感測器紀錄，與模擬估算分開。", color = muted)
                            }
                            TextButton(onClick = {
                                clearRead()
                                runCatching { context.startActivity(HealthConnectRealSteps.settingsIntent()) }
                                    .onFailure { feedback = "目前無法開啟 Health Connect 設定。" }
                            }) { Text("管理／撤銷 Health Connect 權限") }
                            Text("懸浮統計只在展開時更新；系統不允許讀取時顯示本機步數或無資料，不要求背景讀取。", color = muted)
                        }
                        "sync" -> {
                            Text("本機今日記錄：${number(recorded.coerceAtLeast(0L))} 步", color = foreground)
                            Text("待同步感測器紀錄：$pending 筆", color = foreground)
                            Text("最近同步：$syncNote", color = muted)
                            TextButton(enabled = canWrite && syncEnabled && writeAllowed && pending > 0 && !syncBusy, onClick = {
                                syncBusy = true
                                scope.launch {
                                    try {
                                        feedback = PhysicalStepSync.syncPending(context).note
                                        refreshLedger()
                                        if (readAllowed) read()
                                    } finally { syncBusy = false }
                                }
                            }) { Text(if (syncBusy) "同步中…" else "立即同步真實紀錄") }
                            Text("只傳送新版本的實際感測紀錄。舊資料保留且不重傳。", color = muted)
                        }
                        else -> {
                            Text("待同步感測器紀錄：$pending 筆", color = foreground)
                            TextButton(enabled = pending > 0 && !sensor.tracking && !syncEnabled && !syncBusy,
                                onClick = { confirmDiscard = "sensor" }) { Text("清除未同步感測器紀錄") }
                            HorizontalDivider(color = palette.stroke)
                            Text("舊版估算紀錄：$legacyPending 筆未同步／$legacySynced 筆歷史已同步；不再寫入 HC。", color = muted)
                            TextButton(enabled = legacyPending > 0, onClick = { confirmDiscard = "estimate" }) {
                                Text("清除舊版未同步估算紀錄")
                            }
                            ledger.legacyTodaysCount()?.let { previous ->
                                Text("舊版今日感測紀錄：${number(previous)} 步（獨立歷史統計）", color = foreground)
                            }
                            Text("舊版感測紀錄：${ledger.legacyRecordCount()} 筆；舊版待同步 ${ledger.legacyPendingCount()} 筆。保留於本機，不會自動重傳或清除。", color = muted)
                        }
                    }
                    if (feedback.isNotBlank()) Text(feedback, color = muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("關閉") } },
        )
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

@Composable
private fun HealthCard(palette: NaosPalette, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = palette.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun HealthRow(palette: NaosPalette, glyph: NaosActionGlyph, title: String, subtitle: String,
                      action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NaosActionIcon(glyph, palette.accent)
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = palette.muted, fontSize = 12.sp)
        }
        action()
    }
}

@Composable
private fun HealthNavigationRow(palette: NaosPalette, glyph: NaosActionGlyph, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = palette.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 48.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NaosActionIcon(glyph, palette.accent)
            Text(title, color = palette.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("›", color = palette.muted, fontSize = 24.sp)
        }
    }
}
