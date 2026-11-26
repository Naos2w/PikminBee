package com.naos.pikminbee.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var confirmRealShare by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("電腦網頁控制", fontWeight = FontWeight.SemiBold, color = foreground)
        Text(lan.connection.label + if (lan.connection.hasSession)
            " · 剩餘 " + lan.connection.countdown else "",
            color = foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (lan.connection.device.isNotBlank()) Text(lan.connection.device, color = muted, fontSize = 12.sp)
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

            if (lan.active) Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lan.address))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { onMessage("無法開啟瀏覽器：" + (it.message ?: "未找到瀏覽器")) }
                },
                modifier = Modifier.weight(1f),
            ) { Text("在瀏覽器開啟", fontSize = 12.sp) }
        }
        if (lan.active) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("網址：${lan.address}", color = foreground, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("PikminBee URL", lan.address))
                    onMessage("已複製 Wi-Fi 控制網址")
                }) {
                    Canvas(Modifier.size(18.dp)) {
                        val unit = size.minDimension
                        val stroke = Stroke(width = unit * .095f)
                        val radius = CornerRadius(unit * .1f)
                        drawRoundRect(foreground, topLeft = Offset(unit * .30f, unit * .08f),
                            size = Size(unit * .62f, unit * .66f),
                            cornerRadius = radius, style = stroke)
                        drawRoundRect(foreground, topLeft = Offset(unit * .08f, unit * .30f),
                            size = Size(unit * .62f, unit * .62f),
                            cornerRadius = radius, style = stroke)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("複製", fontSize = 12.sp, color = foreground)
                }
            }
            if (lan.connection.showPin) Text("配對碼：${lan.pairingPin}", color = foreground,
                fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (lan.connection.hasSession) OutlinedButton(onClick = {
                context.startService(LanControlService.command(context, LanControlService.DISCONNECT))
            }, modifier = Modifier.fillMaxWidth()) { Text("中斷瀏覽器連線") }
        }
        if (lan.active) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (lan.physicalSharingAllowed) "真實位置分享：已同意（僅限這次配對）"
                     else "真實位置分享：未授權", color = muted,
                     fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (lan.physicalSharingAllowed) {
                    OutlinedButton(onClick = {
                        context.startService(LanControlService.command(
                            context, LanControlService.REVOKE_PHYSICAL))
                    }) { Text("撤銷分享", fontSize = 12.sp) }
                } else {
                    OutlinedButton(enabled = lan.connection.hasSession, onClick = { confirmRealShare = true }) {
                        Text("允許分享", fontSize = 12.sp)
                    }
                }
            }
            Text("僅提供經驗證、非模擬的最後真實位置，最多保留定位後 30 分鐘。" +
                "模擬 GPS 期間僅顯示歷史快照；分享不會自動啟動定位或行走。",
                color = muted, fontSize = 11.sp)
        }
        if (lan.pendingDevice.isNotBlank() && lan.connection.approvalId.isNotBlank()) {
            val approvalId = lan.connection.approvalId
            var shareOnApproval by remember(approvalId) {
                mutableStateOf(false)
            }
            Text("待核准連線／續期：${lan.pendingDevice}",
                color = foreground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = shareOnApproval, onCheckedChange = { shareOnApproval = it })
                Text("分享最近真實位置（選填）", color = foreground, fontSize = 12.sp)
            }
            Text("僅限本次核准的瀏覽器，快照最多 30 分鐘；私人 Wi-Fi 的 HTTP 未加密。",
                color = muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startService(LanControlService.approveCommand(
                        context, approvalId, shareOnApproval))
                }, modifier = Modifier.weight(1f)) {
                    Text(if (shareOnApproval) "核准並分享" else if (lan.connection.renewal) "核准續期" else "核准裝置",
                        fontSize = 12.sp)
                }
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
    if (confirmRealShare) {
        AlertDialog(
            onDismissRequest = { confirmRealShare = false },
            title = { Text("允許網頁查看最後真實 GPS？") },
            text = { Text("只對目前已核准配對的瀏覽器分享最近經驗證的非模擬座標，" +
                "最多 30 分鐘（由定位時間起算），可隨時撤銷。" +
                "私人 Wi-Fi 的 HTTP 傳輸未加密，其他可監聽此網路的裝置可能看見座標。" +
                "請只在可信私人網路使用。這不會切換 GPS 或自動啟動定位。") },
            confirmButton = {
                Button(onClick = {
                    confirmRealShare = false
                    context.startService(LanControlService.command(
                        context, LanControlService.SHARE_PHYSICAL))
                }) { Text("同意分享") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRealShare = false }) { Text("取消") }
            },
        )
    }
}
