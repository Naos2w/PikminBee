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
            text = "Health Connect 與步數來源"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "今日步數彙總可能來自手機、手錶、其他 App 或手動紀錄。讀取及寫入分別授權。只有不具系統原生計步的手機，才提供手動啟動的實際感測器計步；另行同意後同步新收集的手機感測器紀錄。\n\n路線估算並非硬體感測或實際運動。首次明確授權路線估算寫入後，啟動路徑即可在行走期間依時間自動分段寫入手動估算紀錄，無須另行開關；可撤銷 Health Connect 寫入權限。不重新傳送舊紀錄，不將健康資料傳送至 Web、廣告或其他服務。健康頁開啟且已授權讀取時自動刷新。懸浮統計預設顯示，可獨立關閉；若系統不允許讀取就顯示不可用或本機感測器計步。可隨時停止計步、關閉實際感測同步及撤銷 Health Connect 權限。"
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
