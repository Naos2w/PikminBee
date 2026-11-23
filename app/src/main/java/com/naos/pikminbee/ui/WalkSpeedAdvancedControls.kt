package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.naos.pikminbee.data.AppPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.domain.WalkSpeedProfile

/** Compact extension of the existing map-sheet speed chips; no separate GPS controller. */
@Composable
fun WalkSpeedAdvancedControls(
    profile: WalkSpeedProfile,
    onSelected: (WalkSpeedProfile) -> Unit,
    onMessage: (String) -> Unit,
    foreground: Color,
    muted: Color,
) {
    val context = LocalContext.current
    val savedOptions = remember(context) { AppPreferences(context) }
    var editing by remember(profile) { mutableStateOf(profile.mode != WalkSpeedMode.PRESET) }
    var selectedMode by remember(profile) {
        mutableStateOf(if (profile.mode == WalkSpeedMode.RANGE) WalkSpeedMode.RANGE else WalkSpeedMode.FIXED)
    }
    var fixedText by remember(profile) {
        mutableStateOf(WalkSpeedProfile.number(
            if (profile.mode == WalkSpeedMode.FIXED) profile.fixedKmh
            else savedOptions.lastCustomSpeedKmh()))
    }
    var minText by remember(profile) {
        mutableStateOf(WalkSpeedProfile.number(
            if (profile.mode == WalkSpeedMode.RANGE) profile.minKmh
            else savedOptions.lastRangeMinKmh()))
    }
    var maxText by remember(profile) {
        mutableStateOf(WalkSpeedProfile.number(
            if (profile.mode == WalkSpeedMode.RANGE) profile.maxKmh
            else savedOptions.lastRangeMaxKmh()))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = editing && selectedMode == WalkSpeedMode.FIXED,
                onClick = { editing = true; selectedMode = WalkSpeedMode.FIXED },
                label = { Text("自訂速度", fontSize = 12.sp) },
            )
            FilterChip(
                selected = editing && selectedMode == WalkSpeedMode.RANGE,
                onClick = { editing = true; selectedMode = WalkSpeedMode.RANGE },
                label = { Text("速度區間", fontSize = 12.sp) },
            )
        }
        if (editing) {
            if (selectedMode == WalkSpeedMode.FIXED) {
                OutlinedTextField(
                    value = fixedText,
                    onValueChange = { fixedText = it.take(8) },
                    label = { Text("速度（km/h）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it.take(8) },
                        label = { Text("最低 km/h") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it.take(8) },
                        label = { Text("最高 km/h") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text("範圍 0.8–20 km/h；變速不重啟行程。區間值僅在路段交界抽樣，不保證遊戲種花結果。",
                color = muted, fontSize = 11.sp)
            Button(onClick = {
                val result = runCatching {
                    if (selectedMode == WalkSpeedMode.FIXED)
                        WalkSpeedProfile.fixed(fixedText.toDouble(), profile.preset)
                    else WalkSpeedProfile.range(minText.toDouble(), maxText.toDouble(), profile.preset)
                }
                result.fold(
                    onSuccess = onSelected,
                    onFailure = { onMessage(it.message ?: "請輸入有效的 km/h 數值") },
                )
            }) { Text("套用速度") }
        }
        if (profile.mode != WalkSpeedMode.PRESET) {
            Text("目前選擇：" + profile.display, color = foreground, fontSize = 12.sp)
        }
    }
}
