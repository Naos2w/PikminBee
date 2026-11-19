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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
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
    var physicalOptIn by remember { mutableStateOf(physicalLedger.optIn()) }
    var readAllowed by remember { mutableStateOf(false) }
    var writeAllowed by remember { mutableStateOf(false) }
    var hcToday by remember { mutableStateOf<Long?>(null) }
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
            if (readAllowed) hcToday = HealthConnectRealSteps.todayAggregate(context)
            if (writeAllowed) {
                if (virtualOptIn) EstimatedStepsSyncWorker.enqueue(context)
                if (physicalOptIn && !native) PhysicalStepsSyncWorker.enqueue(context)
            }
            feedback = "Health Connect 授權已重新確認"
        }
    }

    suspend fun refresh() {
        readAllowed = HealthConnectRealSteps.hasReadPermission(context)
        writeAllowed = HealthConnectStepSync.hasWritePermission(context)
        hcToday = if (readAllowed) HealthConnectRealSteps.todayAggregate(context) else null
        virtualPending = estimateLedger.pending().size
        physicalPending = physicalLedger.pending().size
        feedback = if (!available) "這台裝置目前無法使用 Health Connect"
            else if (readAllowed) "已讀取 Health Connect 今日彙總步數"
            else "尚未取得 READ_STEPS，無法讀取今日步數"
    }
    LaunchedEffect(available) { refresh() }

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

    MapGlass(dark, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("♡ Health Connect · 步數", fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, color = foreground)

            Text("今日 Health Connect 彙總步數：" +
                (hcToday?.toString() ?: "尚未取得"),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
            Text("此數字是 Health Connect 的跨來源彙總，可能包含其他 App 及使用者授權的手動虛擬估算紀錄，不能直接當成手機感測器專屬步數。",
                fontSize = 11.sp, color = muted)
            Text("目前實體步數來源：" + when (source) {
                RealStepSource.HC_ON_DEVICE -> "Android 原生 Health Connect 計步"
                RealStepSource.PHONE_STEP_SENSOR -> "手機 TYPE_STEP_COUNTER 感測器"
                RealStepSource.UNAVAILABLE -> "尚無可用的本機計步來源"
            }, fontSize = 12.sp, color = foreground)

            if (available) {
                Button(onClick = {
                    hcRequest.launch(setOf(HealthConnectRealSteps.READ_STEPS))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (readAllowed) "重新確認讀取步數權限" else "授權 READ_STEPS／啟用系統計步")
                }
                Text(if (native)
                    "此裝置支援系統原生計步：授予 READ_STEPS 後，Health Connect 可自行記錄手機行走步數，不另外重複上傳感測器資料。"
                    else "此裝置不符合 Android 原生計步條件；可使用手機計步感測器相容模式。",
                    fontSize = 11.sp, color = muted)
                Button(onClick = { scope.launch { refresh() } },
                    modifier = Modifier.fillMaxWidth()) { Text("更新今日步數與權限狀態") }
            } else {
                Text("Health Connect 不可用；可先在本機檢視感測器步數。",
                    fontSize = 12.sp, color = muted)
            }

            if (source == RealStepSource.PHONE_STEP_SENSOR) {
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
            } else if (source == RealStepSource.UNAVAILABLE) {
                Text("未偵測到手機硬體計步器，也未提供內建 HC 計步；請確認手錶或其他健康 App 是否已向 Health Connect 提供資料。",
                    fontSize = 12.sp, color = muted)
            }

            Text("模擬路徑／尋花累計估算：" + estimates.estimatedSteps + " 步",
                fontSize = 14.sp, color = foreground)
            Text("虛擬步數由路程與預設步幅換算，不等於真的走路；不會與手機感測步數混在一起。",
                fontSize = 11.sp, color = muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("同步虛擬路徑估算步數", fontSize = 13.sp, color = foreground)
                    Text("預設關閉。明確同意後才以手動輸入標示寫入，可能與其他來源重疊。",
                        fontSize = 11.sp, color = muted)
                }
                Switch(checked = virtualOptIn, onCheckedChange = {
                    virtualOptIn = it
                    estimateLedger.setOptIn(it)
                    if (it && writeAllowed) EstimatedStepsSyncWorker.enqueue(context)
                    onMessage(if (it) "已選擇同步虛擬估算步數，仍須確認 HC 寫入權限"
                        else "已停止虛擬步數同步")
                })
            }

            Text("寫入步數授權：" + if (writeAllowed) "已授權" else "尚未授權",
                fontSize = 12.sp, color = muted)
            if (available) {
                Button(onClick = {
                    hcRequest.launch(setOf(HealthConnectStepSync.WRITE_STEPS))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (writeAllowed) "重新確認 WRITE_STEPS 權限" else "授權 Health Connect 寫入步數")
                }
            }
            Text("待同步虛擬步行紀錄：" + virtualPending + " 筆",
                fontSize = 12.sp, color = muted)
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
            Text(feedback, fontSize = 11.sp, color = muted)
            Text("沒有寫入授權或未明確啟用同步時，PikminBee 不會向 Health Connect 寫入步數；也不會將任何健康資料傳至個人伺服器。",
                fontSize = 11.sp, color = muted)
        }
    }
}
