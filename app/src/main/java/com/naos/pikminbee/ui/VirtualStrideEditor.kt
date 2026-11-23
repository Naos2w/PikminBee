package com.naos.pikminbee.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naos.pikminbee.health.VirtualStridePolicy
import java.util.Locale

@Composable
fun VirtualStrideEditor(
    strideMetres: Double,
    onApply: (Double) -> Unit,
    onMessage: (String) -> Unit,
    muted: Color,
) {
    var cmText by remember(strideMetres) {
        mutableStateOf(String.format(Locale.US, "%.1f", strideMetres * 100.0)
            .removeSuffix(".0"))
    }
    Text("虛擬估計步幅（公分）", fontSize = 14.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = cmText, onValueChange = { cmText = it.take(5) },
            singleLine = true,
            label = { Text("40–120 cm") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            val cm = cmText.toDoubleOrNull()
            val metres = if (cm == null) Double.NaN else cm / 100.0
            if (!VirtualStridePolicy.valid(metres))
                onMessage("步幅須介於 40–120 公分")
            else onApply(metres)
        }) { Text("設定") }
    }
    Text("只影響下一次虛擬行走的估計步數；已記錄的歷史步數不會重算，也不是感測器實測。",
        color = muted, fontSize = 11.sp)
}
