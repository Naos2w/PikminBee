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
            text = "Health Connect 與實際步數"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "今日步數彙總可能來自手機、手錶、其他 App 或手動紀錄。讀取及寫入分別授權。只有不具系統原生計步的手機，才提供手動啟動的實際感測器計步；另行同意後同步新收集的手機感測器紀錄。\n\n不寫入模擬路線估算，不重新傳送舊紀錄，不將健康資料傳送至 Web、廣告或其他服務。懸浮統計可獨立開關；若系統不允許讀取就顯示不可用或本機感測器計步。可隨時停止計步、關閉同步及撤銷 Health Connect 權限。"
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
