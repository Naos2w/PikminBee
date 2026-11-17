package com.naos.pikminbee.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.naos.pikminbee.lan.LanStatus
import com.naos.pikminbee.location.MockLocationService

/** Remote control is off by default, never silently enabled on Wi-Fi connection. */
@Composable
fun WifiControlPanel(
    isMockActive: Boolean,
    foreground: Color,
    muted: Color,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val lan by LanStatus.display.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Wi-Fi 網頁控制", fontWeight = FontWeight.SemiBold, color = foreground)
        Text(
            if (lan.active) "已連線到私人 Wi-Fi · 只限同一區網"
            else "預設關閉。先啟動模擬位置，再透過私人 Wi-Fi 連線。",
            color = muted, fontSize = 12.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!lan.active) Button(
                onClick = {
                    context.startService(MockLocationService.command(
                        context, MockLocationService.ACTION_ENABLE_LAN))
                },
                enabled = isMockActive,
                modifier = Modifier.weight(1f),
            ) { Text("開啟網頁控制", fontSize = 12.sp) }
            else OutlinedButton(
                onClick = {
                    context.startService(MockLocationService.command(
                        context, MockLocationService.ACTION_DISABLE_LAN))
                },
                modifier = Modifier.weight(1f),
            ) { Text("關閉並撤銷配對", fontSize = 12.sp) }

            if (lan.active) TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("PikminBee URL", lan.address))
                onMessage("已複製 Wi-Fi 控制網址")
            }, modifier = Modifier.weight(1f)) { Text("複製網址", fontSize = 12.sp) }
        }
        if (lan.active) {
            Text("網址：${lan.address}", color = foreground, fontSize = 12.sp)
            Text("配對碼：${lan.pairingPin}", color = foreground,
                fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        if (lan.pendingDevice.isNotBlank()) {
            Text("新裝置要求連線：${lan.pendingDevice}",
                color = foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startService(MockLocationService.command(
                        context, MockLocationService.ACTION_APPROVE_LAN))
                }, modifier = Modifier.weight(1f)) { Text("核准裝置", fontSize = 12.sp) }
                OutlinedButton(onClick = {
                    context.startService(MockLocationService.command(
                        context, MockLocationService.ACTION_DENY_LAN))
                }, modifier = Modifier.weight(1f)) { Text("拒絕", fontSize = 12.sp) }
            }
        }
        Text(lan.note, color = muted, fontSize = 12.sp)
        Text("安全提醒：區域網路 HTTP 未加密，只能在可信任的私人 Wi-Fi 使用。",
            color = muted, fontSize = 11.sp)
    }
}
