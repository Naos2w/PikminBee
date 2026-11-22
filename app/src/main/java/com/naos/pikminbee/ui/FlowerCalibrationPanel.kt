package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.vision.BloomDetector
import com.naos.pikminbee.vision.FlowerPixelCalibration
import com.naos.pikminbee.vision.ImageTapMapper
import java.util.Locale
import kotlin.math.hypot

/**
 * Manual anchor preview for a single user-selected still image.
 * No GPS service, route insertion, screenshot persistence, or auto-location.
 */
@Composable
fun FlowerCalibrationPanel(
    image: BloomDetector.Result,
    previewImage: ImageBitmap,
    documentRevision: Long,
    foreground: Color,
    muted: Color,
    onSaveCandidate: (Coordinate) -> Unit,
) {
    var expanded by remember(documentRevision) { mutableStateOf(false) }
    var pixelX by remember(documentRevision) { mutableStateOf("") }
    var pixelY by remember(documentRevision) { mutableStateOf("") }
    var latitude by remember(documentRevision) { mutableStateOf("") }
    var longitude by remember(documentRevision) { mutableStateOf("") }
    var anchors by remember(documentRevision) {
        mutableStateOf(emptyList<FlowerPixelCalibration.Anchor>())
    }
    var calibration by remember(documentRevision) {
        mutableStateOf<FlowerPixelCalibration.Calibration?>(null)
    }
    var error by remember(documentRevision) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起座標校正" else "地圖像素 → GPS 手動校正（預覽）")
        }
        if (!expanded) return@Column

        Text(
            "在下方圖片點選至少 3 個分散的已知地標，再輸入自行確認的 GPS 座標。" +
                " 點選位置會自動填入縮圖像素，也可以手動修改 X/Y。" +
                " 校正點僅存記憶體，不會加入任何路徑。",
            color = muted, fontSize = 12.sp,
        )
        val x = pixelX.trim().toFloatOrNull()
        val y = pixelY.trim().toFloatOrNull()
        val selectedPixel = if (x != null && y != null && x.isFinite() && y.isFinite() &&
            x in 0f..(image.width - 1).toFloat() &&
            y in 0f..(image.height - 1).toFloat()
        ) ImageTapMapper.Pixel(x, y) else null
        FlowerTapPreview(
            previewImage = previewImage,
            documentRevision = documentRevision,
            anchors = anchors,
            pending = selectedPixel,
            onPixelSelected = { point ->
                pixelX = String.format(Locale.US, "%.1f", point.x)
                pixelY = String.format(Locale.US, "%.1f", point.y)
                error = null
            },
        )
        Text(
            "青色圈＝目前選取地標，黃色圈＝已加入地標。" +
                " 圖片以 Fit 縮放；黑邊點擊不會建立座標。",
            color = muted, fontSize = 11.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pixelX, onValueChange = { pixelX = it },
                label = { Text("像素 X") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = pixelY, onValueChange = { pixelY = it },
                label = { Text("像素 Y") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latitude, onValueChange = { latitude = it },
                label = { Text("緯度") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = longitude, onValueChange = { longitude = it },
                label = { Text("經度") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = anchors.size < 16,
                onClick = {
                    val x = pixelX.trim().toDoubleOrNull()
                    val y = pixelY.trim().toDoubleOrNull()
                    val gps = Coordinate.parse(latitude, longitude)
                    error = when {
                        x == null || y == null || !x.isFinite() || !y.isFinite() ->
                            "請輸入有效的圖片像素 X、Y"
                        x !in 0.0..(image.width - 1).toDouble() ||
                            y !in 0.0..(image.height - 1).toDouble() ->
                            "像素超出目前圖片尺寸"
                        gps == null -> "請輸入有效的緯度、經度"
                        anchors.any { hypot(it.pixelX - x, it.pixelY - y) < 3.0 } ->
                            "這個像素位置已加入，請選另一個地標"
                        else -> null
                    }
                    if (error == null && x != null && y != null && gps != null) {
                        anchors = anchors + FlowerPixelCalibration.Anchor(x, y, gps)
                        calibration = null
                        pixelX = ""
                        pixelY = ""
                    }
                },
            ) { Text("新增對應點 (" + anchors.size + "/16)", fontSize = 12.sp) }

            if (anchors.isNotEmpty()) {
                TextButton(onClick = {
                    anchors = emptyList()
                    calibration = null
                    error = null
                }) { Text("清除全部", fontSize = 12.sp) }
            }
        }
        anchors.forEachIndexed { index, anchor ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (index + 1).toString() + ". (" +
                        anchor.pixelX.toInt() + ", " + anchor.pixelY.toInt() +
                        ") → " + anchor.coordinate.label(),
                    color = muted, fontSize = 11.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    anchors = anchors.filterIndexed { i, _ -> i != index }
                    calibration = null
                }) { Text("移除", fontSize = 11.sp) }
            }
        }
        Button(
            enabled = anchors.size >= 3,
            onClick = {
                runCatching { FlowerPixelCalibration.fit(image.width, image.height, anchors) }
                    .onSuccess {
                        calibration = it
                        error = null
                    }
                    .onFailure {
                        calibration = null
                        error = it.message ?: "校正失敗，請檢查地標位置"
                    }
            },
        ) { Text("試算花朵候選座標（只讀）", fontSize = 12.sp) }

        if (error != null) {
            Text(error ?: "", color = Color(0xFFD97727), fontSize = 12.sp)
        }
        if (calibration != null) {
            val fit = calibration!!
            Text(
                "留一驗證 RMSE " + metres(fit.heldOutRmseMetres) +
                    " 公尺，最大 " + metres(fit.worstHeldOutErrorMetres) + " 公尺",
                color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            )
            Text(
                "以上只是你提供的地標之間的一致性，不代表實際遊戲花朵 GPS 準確度。" +
                    " 地圖平移、縮放、旋轉、傾斜或換一張圖片後必須重新校正。",
                color = muted, fontSize = 11.sp,
            )
            image.hits.take(8).forEachIndexed { index, hit ->
                val preview = fit.preview(hit.centreX.toDouble(), hit.centreY.toDouble())
                if (preview != null) {
                    Text(
                        "候選 " + (index + 1) + "： " + preview.coordinate.label() +
                            if (preview.insideAnchorHull) "（參考點範圍內）"
                            else "（外插，勿作為路點）",
                        color = muted, fontSize = 11.sp,
                    )
                    OutlinedButton(
                        enabled = FlowerCandidateSavePolicy.canStore(
                            preview.insideAnchorHull,
                            fit.heldOutRmseMetres, fit.worstHeldOutErrorMetres),
                        onClick = { onSaveCandidate(preview.coordinate) },
                    ) { Text("存為待確認花點 " + (index + 1), fontSize = 12.sp) }
                }
            }
            Text(
                "僅在參考地標範圍內、誤差符合保守門檻的候選可手動存為「未驗證」花點。" +
                    " 儲存不代表遊戲大花已驗證，之後仍須本人確認，才適合加入尋花路線；" +
                    " 不會自動啟動行走、瞬移或新增路點。",
                color = muted, fontSize = 11.sp,
            )
        }
    }
}

private fun metres(value: Double): String = String.format(Locale.US, "%.1f", value)
