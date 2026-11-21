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
import com.naos.pikminbee.lan.LanControlService

/** Remote control is off by default, never silently enabled on Wi-Fi connection. */
@Composable
fun WifiControlPanel(
    @Suppress("UNUSED_PARAMETER") isMockActive: Boolean,
    foreground: Color,
    muted: Color,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val lan by LanStatus.display.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("電腦網頁控制", fontWeight = FontWeight.SemiBold, color = foreground)
        Text(
            if (lan.active) "網頁控制已開啟 · 同一可互通私人區網"
            else "預設關閉。電腦與手機連線到同一私人區網後即可啟用，不必先啟動模擬位置。",
            color = muted, fontSize = 12.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!lan.active) Button(
                onClick = {
                    runCatching { LanControlService.enable(context) }
                        .onFailure { onMessage("無法啟動網頁控制：" + it.message) }
                },
                modifier = Modifier.weight(1f),
            ) { Text("開啟電腦控制", fontSize = 12.sp) }
            else OutlinedButton(
                onClick = {
                    context.startService(LanControlService.command(
                        context, LanControlService.DISABLE))
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
                    context.startService(LanControlService.command(
                        context, LanControlService.APPROVE))
                }, modifier = Modifier.weight(1f)) { Text("核准裝置", fontSize = 12.sp) }
                OutlinedButton(onClick = {
                    context.startService(LanControlService.command(
                        context, LanControlService.DENY))
                }, modifier = Modifier.weight(1f)) { Text("拒絕", fontSize = 12.sp) }
            }
        }
        Text(lan.note, color = muted, fontSize = 12.sp)
        Text("使用電腦瀏覽器開啟上方網址，輸入配對碼後由手機核准。HTTP 未加密，請勿公開 8765 連接埠。",
            color = muted, fontSize = 11.sp)
    }
}
