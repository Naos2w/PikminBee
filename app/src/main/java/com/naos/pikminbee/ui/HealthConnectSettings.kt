package com.naos.pikminbee.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.health.connect.client.PermissionController
import com.naos.pikminbee.health.EstimatedStepLedger
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.health.EstimatedStepsSyncWorker
import com.naos.pikminbee.health.HealthConnectStepSync
import kotlinx.coroutines.launch

/** Deliberately opt-in. Simulated route steps never masquerade as measured walking. */
@Composable
fun HealthConnectSettings(dark: Boolean, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val ledger = remember(context) { EstimatedStepLedger(context) }
    val scope = rememberCoroutineScope()
    val estimates by EstimatedWalkStatus.state.collectAsState()
    var optIn by remember { mutableStateOf(ledger.optIn()) }
    var permissionGranted by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(ledger.pending().size) }
    var details by remember { mutableStateOf("尚未連線 Health Connect") }
    val foreground = if (dark) Color(0xFFF3F7FF) else Color(0xFF18263A)
    val muted = if (dark) Color(0xFFC6D3E6) else Color(0xFF52627A)
    val available = remember(context) { HealthConnectStepSync.installed(context) }

    val request = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grants ->
        permissionGranted = HealthConnectStepSync.WRITE_STEPS in grants
        details = if (permissionGranted) "步數寫入權限已授予" else "步數寫入尚未授權"
        if (permissionGranted && optIn) EstimatedStepsSyncWorker.enqueue(context)
    }

    LaunchedEffect(available) {
        permissionGranted = HealthConnectStepSync.hasWritePermission(context)
        details = if (!available) "Health Connect 尚未安裝或需要更新"
            else if (permissionGranted) "已授權寫入步數" else "尚未授權寫入步數"
    }

    MapGlass(dark, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("♡ Health Connect", fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, color = foreground)
            Text("模擬路徑估算步數：" + estimates.estimatedSteps + " 步",
                fontSize = 14.sp, color = foreground)
            Text("待同步的虛擬步行紀錄：" + pending + " 筆",
                fontSize = 12.sp, color = muted)
            Text("虛擬位置行走不等於手機感測到的實際運動；預估步數不會自動變成真實健康資料。",
                fontSize = 12.sp, color = muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("同步虛擬路徑估算步數", fontSize = 13.sp, color = foreground)
                    Text("明確選擇後才會寫入，標記為「手動輸入」；可能與其他步數來源重疊。",
                        fontSize = 11.sp, color = muted)
                }
                Switch(checked = optIn, onCheckedChange = {
                    optIn = it
                    ledger.setOptIn(it)
                    if (it && permissionGranted) EstimatedStepsSyncWorker.enqueue(context)
                    onMessage(if (it) "已選擇同步虛擬估算步數，仍需 Health Connect 授權"
                        else "已關閉虛擬估算步數同步")
                })
            }
            Text(details, fontSize = 12.sp, color = muted)
            Button(
                enabled = available,
                onClick = { request.launch(setOf(HealthConnectStepSync.WRITE_STEPS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (permissionGranted) "重新確認 Health Connect 權限" else "授權寫入步數") }
            Button(
                enabled = optIn && available && permissionGranted && pending > 0,
                onClick = {
                    scope.launch {
                        val result = HealthConnectStepSync.syncPending(context)
                        pending = result.remaining
                        details = result.note
                        onMessage(result.note)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("同步待處理的估算步數") }
            Button(
                enabled = pending > 0,
                onClick = {
                    runCatching { ledger.discardPending() }.onSuccess {
                        pending = 0
                        details = "尚未同步的虛擬估算步數已從本機清除"
                        onMessage(details)
                    }.onFailure { onMessage("清除失敗：" + it.message) }
                }, modifier = Modifier.fillMaxWidth(),
            ) { Text("清除未同步的虛擬步數") }
            Text("此功能不會直接讀取手機或手錶的實際步數，也不會將 Health Connect 資料上傳到個人伺服器。",
                fontSize = 11.sp, color = muted)
        }
    }
}
