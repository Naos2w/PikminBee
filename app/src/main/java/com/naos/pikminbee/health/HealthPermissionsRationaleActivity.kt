package com.naos.pikminbee.health

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button

/** Explicit explanation for Android/Health Connect's permission usage screen. */
class HealthPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        content.addView(TextView(this).apply {
            text = "Health Connect 步數（唯讀）"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "授權後，只有你在 App 內主動查詢時才讀取今日步數彙總。資料可能來自手機、手錶、其他 App 或手動紀錄，並非本 App 驗證的實際運動。\n\n不寫入健康資料、不同步模擬步數、不在背景讀取、不傳送至 Web 或其他服務、不儲存查詢結果。路線估算與 Health Connect 完全分開。可在 Health Connect 隨時撤銷權限。"
            textSize = 16f
            setPadding(0, pad, 0, pad)
        })
        content.addView(Button(this).apply {
            text = "返回"
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
