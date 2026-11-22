package com.naos.pikminbee.ui

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** First-use, replayable three-step explanation; no automatic GPS action. */
@Composable
fun OverlayGuideDialog(onFinished: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val title = listOf("行走控制", "定位與速度", "其他快捷功能")
    val description = listOf(
        "開始、暫停及繼續共用一個按鈕。停止只停止路線，保留目前模擬位置。",
        "真實／虛擬 GPS 目前使用的一側會亮起。點另一顆即可切換；切回真實 GPS 會停止模擬及行走，切回虛擬位置不會自動繼續行走。若尚無可恢復的虛擬位置，按虛擬圖示會引導到地圖選點。亮起代表模式，不保證已取得真實衛星定位。",
        "偵測花需要 Android 畫面擷取同意；地圖會開啟主 App，關閉浮窗不會停止路線。",
    )
    val icons = listOf(
        listOf(OverlayGlyph.PLAY to "開始／暫停", OverlayGlyph.STOP to "停止"),
        listOf(OverlayGlyph.REAL to "真實 GPS", OverlayGlyph.VIRTUAL to "虛擬 GPS",
            OverlayGlyph.SPEED to "切換速度"),
        listOf(OverlayGlyph.FLOWER to "偵測花", OverlayGlyph.MAP to "主 App",
            OverlayGlyph.CLOSE to "關閉浮窗"),
    )
    val p = LocalNaosVisualState.current.palette
    AlertDialog(
        onDismissRequest = onFinished,
        title = { Text("懸浮控制教學 · ${page + 1}/3") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(title[page])
                Text(description[page], fontSize = 13.sp)
                icons[page].forEach { (glyph, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    setImageDrawable(NaosOverlayActionIcon(glyph,
                                        p.ink.toArgb(), (24 * resources.displayMetrics.density).toInt()))
                                }
                            },
                            modifier = Modifier.size(30.dp),
                        )
                        Text(label, fontSize = 14.sp)
                    }
                }
                Text("收合小蜜蜂可以拖曳位置；長按圖示可查看功能名稱。也能在設定頁重新閱讀。",
                    fontSize = 12.sp, color = p.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (page == 2) onFinished() else page++ }) {
                Text(if (page == 2) "完成" else "下一步")
            }
        },
        dismissButton = {
            TextButton(onClick = onFinished) { Text("略過") }
        },
    )
}
