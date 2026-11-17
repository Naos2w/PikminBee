package com.naos.pikminbee.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.naos.pikminbee.location.LocationCheckpoint
import com.naos.pikminbee.location.MockLocationService
import com.naos.pikminbee.location.SessionJournal

/**
 * User-visible recovery and realistic HyperOS guidance.
 * Never requests unrestricted battery optimisation exceptions automatically.
 */
@Composable
fun BackgroundSessionPanel(
    isMockActive: Boolean,
    checkpoint: LocationCheckpoint?,
    foreground: Color,
    muted: Color,
    onMessage: (String) -> Unit,
    onAskLocationPermission: () -> Unit,
) {
    val context = LocalContext.current
    val journal = remember(context) { SessionJournal(context) }
    var expanded by remember { mutableStateOf(false) }
    val isHyperOsFamily = remember {
        val vendor = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        vendor.contains("xiaomi") || vendor.contains("poco") || vendor.contains("redmi")
    }
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onMessage(if (granted) "已開啟 PikminBee 通知權限"
            else "未允許通知：模擬定位不會因此自動停止，但通知控制可能無法顯示")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起背景執行設定" else "背景執行與恢復")
        }
        if (!expanded) return@Column

        Text(
            "PikminBee 使用 Android 定位前景服務，背景執行期間仍有系統限制。" +
                " 清除最近使用的任務不等同強制停止；強制停止後不能由 App 自行重新開機。",
            color = muted, fontSize = 12.sp,
        )
        if (checkpoint != null) {
            Text(
                "上次保存位置：${checkpoint.coordinate.label()}",
                color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            )
            if (!isMockActive) {
                Button(onClick = {
                    if (com.naos.pikminbee.location.CheckpointCodec.decode(
                            com.naos.pikminbee.location.CheckpointCodec.encode(checkpoint),
                            System.currentTimeMillis(),
                        ) == null
                    ) {
                        journal.clear()
                        onMessage("上次位置已超過 12 小時或時間無效，請重新選擇位置")
                    } else if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        onAskLocationPermission()
                    } else {
                        runCatching {
                            ContextCompat.startForegroundService(
                                context,
                                MockLocationService.teleportIntent(context, checkpoint.coordinate),
                            )
                        }.onSuccess {
                            onMessage("已要求恢復上次位置；請確認開發者選項已選 PikminBee")
                        }.onFailure {
                            onMessage("無法恢復定位：${it.message ?: "背景服務受限制"}")
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("手動回到上次位置")
                }
                OutlinedButton(onClick = {
                    journal.clear()
                    onMessage("已清除上次位置，不影響其他收藏路線")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("清除上次位置")
                }
            }
        } else {
            Text("尚未保存最近的模擬位置。", color = muted, fontSize = 12.sp)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            OutlinedButton(onClick = {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("允許定位通知與快捷操作")
            }
        }

        if (isHyperOsFamily) {
            Text(
                "HyperOS／POCO：可自行檢查「應用程式電池使用限制」、多工畫面的鎖定，" +
                    "以及裝置提供的背景啟動設定。各版本名稱與可用項目可能不同。",
                color = muted, fontSize = 12.sp,
            )
        } else {
            Text("可在系統應用程式設定中查看通知與電池背景限制。", color = muted, fontSize = 12.sp)
        }
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }.onFailure { onMessage("無法開啟設定：${it.message}") }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("開啟 Android 應用程式設定")
        }
        Text(
            "系統回收後若自動恢復成功，只會停留在最後位置，不會自動繼續尋花或重開網頁控制。",
            color = muted, fontSize = 11.sp,
        )
    }
}
