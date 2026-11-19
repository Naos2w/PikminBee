package com.naos.pikminbee.health

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Local, human-readable Health Connect permission rationale and data-use disclosure. */
class HealthPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(64), px(24), px(24))
            setBackgroundColor(android.graphics.Color.rgb(246, 249, 254))
        }
        fun line(text: String, size: Float) {
            wrapper.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(android.graphics.Color.rgb(31, 51, 81))
                setPadding(0, px(10), 0, px(10))
            })
        }
        line("PikminBee 與 Health Connect", 22f)
        line("本功能只在您授予步數寫入權限，並主動開啟「同步虛擬路徑估算步數」後，寫入由模擬路徑距離換算的步數。", 15f)
        line("虛擬步數不是手機或手錶測得的真實步行；寫入時會標示為手動輸入，可能與其他健康資料來源重疊。", 14f)
        line("路線、估算步數與同步狀態保存在本機；本功能不會把健康資料傳送到自家伺服器。您可隨時撤銷 Health Connect 權限或在 App 關閉同步。", 14f)
        val close = TextView(this).apply {
            text = "返回"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.rgb(51, 118, 199))
            setOnClickListener { finish() }
            minimumHeight = px(50)
        }
        wrapper.addView(close)
        setContentView(wrapper)
    }
}
