package com.naos.pikminbee.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class BeePermissionStatus(
    val preciseLocation: Boolean,
    val selectedMockApp: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val backgroundUnrestricted: Boolean,
) {
    fun checked(step: Int): Boolean = when (step) {
        0 -> preciseLocation
        1 -> selectedMockApp
        2 -> overlay
        3 -> notifications
        4 -> backgroundUnrestricted
        else -> false
    }

    fun canContinue(step: Int): Boolean = step >= 2 || checked(step)
}

/** A read-only status check: does not create a mock provider or start GPS. */
object BeePermissions {
    fun inspect(context: Context): BeePermissionStatus {
        val op = context.getSystemService(AppOpsManager::class.java)
        val mock = runCatching {
            op.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notification = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val power = context.getSystemService(PowerManager::class.java)
        return BeePermissionStatus(
            preciseLocation = fine,
            selectedMockApp = mock,
            overlay = Settings.canDrawOverlays(context),
            notifications = notification,
            backgroundUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

/**
 * First-launch sequential guide, also accessible later in the menu. Only mock GPS
 * requires precise location + a selected developer-option mock app. A map-only
 * exit is always available; overlay/notifications/battery settings are optional.
 */
@Composable
fun BeePermissionWizard(
    dark: Boolean,
    initial: Boolean,
    onFinish: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var step by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val status = remember(refresh) { BeePermissions.inspect(context) }
    val headings = listOf("精確定位", "選擇模擬位置 App", "懸浮蜜蜂", "通知", "背景與省電設定")
    val details = listOf(
        "使用模擬 GPS 需要精確定位授權。僅瀏覽地圖可略過完整設定。",
        "開啟 Android 開發人員選項，將「選取模擬位置應用程式」設為 PikminBee UAT（或已安裝的 DEV 版）。返回後按重新檢查。",
        "只有需要在其他 App 上方使用蜜蜂控制時，才開啟此權限。",
        "通知讓前景定位的暫停、停止與恢復真實 GPS 指令更方便使用。",
        "HyperOS 可能限制背景服務。可檢視電池及背景設定，但系統強制停止後無法自動重啟。",
    )
    fun open(intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun requestCurrent() {
        when (step) {
            0 -> fineLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
            1 -> open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            2 -> open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")))
            3 -> if (Build.VERSION.SDK_INT >= 33)
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                Settings.EXTRA_APP_PACKAGE, context.packageName,
            ))
            4 -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
        }
    }

    val palette = NaosDesignSystem.palette(LocalNaosVisualState.current.family, dark)
    Column(
        Modifier.fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("權限檢查", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = if (initial) onFinish else onExit) {
                Text(if (initial) "先略過 →" else "返回地圖 →")
            }
        }
        Text(
            if (initial) "依序完成需要的權限，其他項目可略過。"
            else "檢查裝置權限，不會自動啟動定位。",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        LinearProgressIndicator(progress = { (step + 1f) / 5f }, modifier = Modifier.fillMaxWidth())
        MapGlass(dark, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("步驟 ${step + 1}/5　${headings[step]}", fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(details[step], fontSize = 14.sp)
                Text(
                    if (status.checked(step)) "✓ 已檢查通過"
                    else if (step < 2) "尚未確認 · 模擬定位必要"
                    else "尚未開啟 · 可選擇略過",
                    color = if (status.checked(step))
                        androidx.compose.ui.graphics.Color(0xFF75D9B0)
                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = ::requestCurrent,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Text(if (step == 0) "授予精確定位" else "開啟 Android 設定")
                }
                OutlinedButton(onClick = { refresh++ },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("重新檢查")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (step > 0) step-- else onExit() }) {
                Text(if (step > 0) "上一步" else "返回")
            }
            Button(
                onClick = { if (step == 4) onFinish() else step++ },
                enabled = status.canContinue(step),
            ) {
                Text(if (step == 4) "完成設定" else if (step >= 2 && !status.checked(step))
                    "略過並繼續" else "下一步")
            }
        }
        // The primary skip/exit control stays visible above the step list, even
        // on POCO screens with larger fonts; re-entry is available in the drawer.
        Text("模擬位置 App 的選擇由 Android 管理；這裡僅檢查設定狀態，不會繞過權限。",
            fontSize = 12.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
