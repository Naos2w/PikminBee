package com.naos.pikminbee.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naos.pikminbee.health.HealthConnectRealSteps
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun HealthConnectReadSettings(foreground: Color, muted: Color) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(HealthConnectRealSteps.status(context)) }
    var result by remember { mutableStateOf<HealthConnectRealSteps.Result?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var loading by remember { mutableStateOf(false) }
    fun clear() {
        job?.cancel()
        job = null
        loading = false
        result = null
    }
    fun read() {
        clear()
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
            if (event == Lifecycle.Event.ON_RESUME) status = HealthConnectRealSteps.status(context)
            else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) clear()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); clear() }
    }
    val request = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        // No silent read on a later app launch. A successful explicit permission action
        // allows this foreground request, or the user can tap refresh after returning.
        if (HealthConnectRealSteps.READ_STEPS in granted) read()
        else result = HealthConnectRealSteps.Result.PermissionRequired
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("今日步數（HC 彙總）", color = foreground, fontWeight = FontWeight.SemiBold)
        val count = (result as? HealthConnectRealSteps.Result.Available)?.count
        Text(if (count != null) NumberFormat.getIntegerInstance().format(count) + " 步"
             else "— 步", color = foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("僅讀取 Health Connect 的彙總資料，可能包含其他來源或手動紀錄。與路線估算完全分開，不寫入健康紀錄。", color = muted, fontSize = 13.sp)
        val note = when (result) {
            HealthConnectRealSteps.Result.PermissionRequired -> "尚未授予步數讀取權限或已撤銷。"
            HealthConnectRealSteps.Result.ProviderUnavailable -> "Health Connect 尚未提供服務。"
            HealthConnectRealSteps.Result.ReadFailed -> "查詢失敗，請稍後重試。"
            is HealthConnectRealSteps.Result.Available -> "已讀取本次彙總；離開此頁不保留數值。"
            null -> if (loading) "正在查詢…" else "請先授權，再讀取今日步數。"
        }
        Text(note, color = muted, fontSize = 12.sp)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            Button(enabled = !loading, onClick = {
                runCatching { request.launch(setOf(HealthConnectRealSteps.READ_STEPS)) }
                    .onFailure { result = HealthConnectRealSteps.Result.ReadFailed }
            }) { Text("授權讀取步數") }
            TextButton(enabled = !loading, onClick = ::read) { Text("重新讀取今日步數") }
        } else Text("請確認手機支援 Health Connect，並完成系統或提供者更新。", color = muted)
        TextButton(onClick = {
            clear()
            runCatching { context.startActivity(HealthConnectRealSteps.settingsIntent()) }
                .onFailure { result = HealthConnectRealSteps.Result.ProviderUnavailable }
        }) { Text("管理 Health Connect 權限") }
    }
}
