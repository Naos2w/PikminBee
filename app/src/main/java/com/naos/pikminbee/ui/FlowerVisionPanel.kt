package com.naos.pikminbee.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.naos.pikminbee.vision.BloomCaptureService
import com.naos.pikminbee.vision.BloomDetector
import com.naos.pikminbee.vision.BloomImageLoader
import com.naos.pikminbee.vision.BloomVisionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An explicitly consented, read-only image recognition preview.
 * Preview coordinates are PIXELS, not GPS stops; no automatic route insertion.
 */
@Composable
fun FlowerVisionPanel(
    foreground: Color,
    muted: Color,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val work = rememberCoroutineScope()
    val vision by BloomVisionState.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { outcome ->
        if (outcome.resultCode == Activity.RESULT_OK && outcome.data != null) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    BloomCaptureService.startIntent(
                        context, outcome.resultCode, outcome.data!!
                    ),
                )
            }.onFailure {
                onMessage("無法啟動畫面分享：" + (it.message ?: "背景服務受到限制"))
            }
        } else {
            onMessage("未授權畫面擷取，不會讀取螢幕")
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) work.launch {
            try {
                val frame = withContext(Dispatchers.IO) {
                    BloomImageLoader.fromDocument {
                        context.contentResolver.openInputStream(uri)
                    }
                }
                val result = withContext(Dispatchers.Default) { BloomDetector.analyse(frame) }
                BloomVisionState.state.value = BloomVisionState.Snapshot(
                    phase = BloomVisionState.Phase.IDLE,
                    message = if (result.mostlyBlank) result.note else
                        "測試圖片找到 " + result.hits.size + " 個候選形狀；需實機圖片驗證",
                    result = result,
                    origin = "使用者自行選取的圖片",
                )
            } catch (e: Exception) {
                onMessage("圖片辨識失敗：" + (e.message ?: "不支援此檔案"))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起畫面花朵辨識" else "🌸 畫面花朵辨識（實驗功能）")
        }
        if (!expanded) return@Column
        Text(
            "只會分析你自己選取的圖片，或每次經 Android 系統授權的畫面分享。" +
                " 不擷取遊戲帳密、不注入觸控、不儲存螢幕影像，也不嘗試繞過受保護畫面。",
            color = muted, fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { imageLauncher.launch(arrayOf("image/*")) },
                enabled = vision.phase == BloomVisionState.Phase.IDLE,
                modifier = Modifier.weight(1f),
            ) { Text("分析圖片", fontSize = 12.sp) }

            if (vision.phase == BloomVisionState.Phase.CAPTURING) {
                Button(onClick = {
                    context.startService(BloomCaptureService.stopIntent(context))
                }, modifier = Modifier.weight(1f)) {
                    Text("停止畫面分享", fontSize = 12.sp)
                }
            } else {
                Button(onClick = {
                    runCatching {
                        val manager = context.getSystemService(MediaProjectionManager::class.java)
                        captureLauncher.launch(manager.createScreenCaptureIntent())
                    }.onFailure {
                        onMessage("無法開啟系統畫面分享：" + (it.message ?: "不支援"))
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text("授權畫面辨識", fontSize = 12.sp)
                }
            }
        }
        Text(vision.message, color = muted, fontSize = 12.sp)
        if (vision.result != null) {
            val result = vision.result!!
            Text("來源：" + (vision.origin ?: "未知") +
                " · " + result.width + "×" + result.height +
                " · 候選 " + result.hits.size + " 個",
                color = foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Canvas(
                Modifier.fillMaxWidth().height(155.dp)
                    .background(Color(0xFF172A39), RoundedCornerShape(12.dp)).padding(8.dp),
            ) {
                drawRect(Color(0xFF33475B), style = Stroke(width = 1.4f))
                for (hit in result.hits.take(40)) {
                    val centre = Offset(
                        hit.centreX.toFloat() / result.width * size.width,
                        hit.centreY.toFloat() / result.height * size.height,
                    )
                    drawCircle(
                        color = if (hit.appearance == BloomDetector.Appearance.COLOURED)
                            Color(0xFFFF88D8) else Color(0xFFFFEFC0),
                        radius = 6.dp.toPx(), center = centre,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            Text("上圖僅標示相對螢幕位置，並非實際遊戲截圖或經緯度。",
                color = muted, fontSize = 11.sp)
            result.hits.take(5).forEachIndexed { idx, hit ->
                Text(
                    "${idx + 1}. 像素 (${hit.centreX}, ${hit.centreY}) · " +
                        (if (hit.appearance == BloomDetector.Appearance.COLOURED) "彩色" else "淺色") +
                        "花朵候選 · 影像特徵分數 " + (hit.score * 100).toInt() + "%",
                    color = muted, fontSize = 11.sp,
                )
            }
        }
        Text(
            "目前尚未建立遊戲地圖像素→GPS 的可信校正，也沒有真實圖片的準確率報告；" +
                "因此不能把辨識結果自動加入尋花路線。這部分留待整合 UAT。",
            color = muted, fontSize = 11.sp,
        )
    }
}
