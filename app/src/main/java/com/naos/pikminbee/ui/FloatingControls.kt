package com.naos.pikminbee.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset
import kotlin.math.abs

/** Overlay owned by the GPS foreground service, not a second independent location engine. */
class FloatingControls(
    private val context: Context,
    private val sendAction: (String) -> Unit,
    private val onSpeedSelected: (WalkSpeedPreset) -> Unit,
    private val onSetDefaultSpeed: (WalkSpeedPreset) -> Unit,
    private val openMap: () -> Unit,
) {
    companion object {
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val STOP_WALK = "stop_walk"
        const val START_FLOWERS = "start_flowers"
        const val RESTORE_REAL = "restore_real"
        const val HIDE = "hide"
    }

    private val windows = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var stateLabel: TextView? = null
    private var pauseAction: TextView? = null
    private var stopAction: TextView? = null
    private var flowerAction: TextView? = null
    private var flowerSummary: TextView? = null
    private val speedButtons = linkedMapOf<WalkSpeedPreset, TextView>()
    private var defaultSpeedAction: TextView? = null
    private var currentSpeed: WalkSpeedPreset = WalkSpeedPreset.DEFAULT
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false

    private fun dp(value: Int): Int = (context.resources.displayMetrics.density * value).toInt()

    private fun glass() = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        setColor(Color.argb(226, 26, 40, 60))
        setStroke(dp(1), Color.argb(110, 255, 255, 255))
    }

    private fun action(label: String, click: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        minWidth = dp(66)
        minHeight = dp(48)
        setPadding(dp(9), dp(7), dp(9), dp(7))
        setOnClickListener { click() }
    }

    fun show() {
        if (root != null) return
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            elevation = dp(13).toFloat()
            setPadding(dp(7), dp(5), dp(7), dp(5))
        }
        val bee = action("🐝") { toggleExpanded() }.apply {
            textSize = 24f
            contentDescription = "PikminBee 懸浮視窗，點一下展開"
        }
        container.addView(bee)
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val description = action("PikminBee · 原地停留") { }
        description.setOnClickListener(null)
        description.textSize = 11f
        actions.addView(description)
        val startFlowers = action("🌸 開始尋花") { sendAction(START_FLOWERS) }.apply {
            contentDescription = "從目前模擬位置沿已儲存尋花路點開始循環道路行走"
        }
        actions.addView(startFlowers)
        val summary = action("尋花草稿 0/14 點 · 循環") { }.apply {
            textSize = 11f
            setOnClickListener(null)
            contentDescription = "尋花草稿狀態"
        }
        actions.addView(summary)
        val speedHeading = action("行走速度（行走中也可調整）") { }.apply {
            textSize = 11f
            setOnClickListener(null)
        }
        actions.addView(speedHeading)
        val speedRow = LinearLayout(context)
        WalkSpeedPreset.entries.forEach { preset ->
            val button = action("${preset.label}\n${preset.kilometresPerHour} km/h") {
                onSpeedSelected(preset)
            }.apply {
                textSize = 12f
                minWidth = dp(86)
                contentDescription = "速度：${preset.display}"
            }
            speedButtons[preset] = button
            speedRow.addView(button)
        }
        actions.addView(speedRow)
        val setDefault = action("設為下次啟動預設") {
            onSetDefaultSpeed(currentSpeed)
        }.apply {
            textSize = 12f
            contentDescription = "將目前選取速度設為個人預設"
        }
        actions.addView(setDefault)
        val row1 = LinearLayout(context)
        val pause = action("暫停") { sendAction(if (pauseAction?.text == "繼續") RESUME else PAUSE) }
        val stop = action("停止行走") { sendAction(STOP_WALK) }
        row1.addView(pause)
        row1.addView(stop)
        actions.addView(row1)
        val row2 = LinearLayout(context)
        row2.addView(action("地圖") { openMap() })
        row2.addView(action("隱藏") { sendAction(HIDE) })
        actions.addView(row2)
        val row3 = LinearLayout(context)
        row3.addView(action("恢復真實 GPS") { sendAction(RESTORE_REAL) })
        actions.addView(row3)
        container.addView(actions)
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(18)
            y = dp(250)
        }

        var startX = 0
        var startY = 0
        var initialX = 0f
        var initialY = 0f
        var moved = false
        bee.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialX
                    val dy = event.rawY - initialY
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) moved = true
                    if (moved) {
                        layout.x = (startX - dx).toInt().coerceAtLeast(0)
                        layout.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { windows.updateViewLayout(container, layout) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) true else {
                        view.performClick()
                        true
                    }
                }
                else -> false
            }
        }
        windows.addView(container, layout)
        root = container
        panel = actions
        stateLabel = description
        pauseAction = pause
        stopAction = stop
        flowerAction = startFlowers
        flowerSummary = summary
        defaultSpeedAction = setDefault
        params = layout
        expanded = false
    }

    private fun toggleExpanded() {
        expanded = !expanded
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        params?.let { params -> root?.let { windows.updateViewLayout(it, params) } }
    }

    fun update(
        phase: WalkPhase,
        mode: WalkMode?,
        flowerStops: Int,
        speedPreset: WalkSpeedPreset,
        defaultSpeedPreset: WalkSpeedPreset,
        planningFlowers: Boolean,
        status: String,
    ) {
        val walking = phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED
        pauseAction?.text = if (phase == WalkPhase.PAUSED) "繼續" else "暫停"
        pauseAction?.isEnabled = walking
        pauseAction?.alpha = if (walking) 1.0f else 0.4f

        flowerAction?.text = if (planningFlowers) "規劃尋花道路中…" else "🌸 開始尋花"
        val canStartFlower = !walking && !planningFlowers && flowerStops in 1..14
        flowerAction?.isEnabled = canStartFlower
        flowerAction?.alpha = if (canStartFlower) 1f else .4f
        flowerSummary?.text = "尋花草稿 ${flowerStops}/14 點 · 循環 · ${speedPreset.display}"
        currentSpeed = speedPreset
        defaultSpeedAction?.apply {
            text = "預設：${defaultSpeedPreset.display} · 設為目前速度"
            isEnabled = speedPreset != defaultSpeedPreset
            alpha = if (isEnabled) 1f else .45f
            contentDescription = "目前預設 ${defaultSpeedPreset.display}。" +
                if (isEnabled) "點此設為 ${speedPreset.display}" else "目前速度就是預設"
        }
        speedButtons.forEach { (option, button) ->
            val selected = option == speedPreset
            if (button.tag != selected) {
                button.tag = selected
                button.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) Color.rgb(151, 95, 22) else Color.TRANSPARENT)
                    setStroke(dp(1), if (selected) Color.rgb(242, 185, 78) else Color.GRAY)
                }
                button.contentDescription = "速度：${option.display}" +
                    if (selected) "，目前選擇" else ""
            }
        }

        stopAction?.text = if (planningFlowers) "取消規劃" else "停止行走"
        stopAction?.isEnabled = walking || planningFlowers
        stopAction?.alpha = if (walking || planningFlowers) 1f else .4f
        stateLabel?.text = when {
            planningFlowers -> "正在查詢步行道路 · 尚未移動"
            mode == WalkMode.FLOWERS && phase == WalkPhase.MOVING ->
                "尋花巡迴中 · ${speedPreset.kilometresPerHour} km/h"
            phase == WalkPhase.MOVING -> "道路步行中 · ${speedPreset.kilometresPerHour} km/h"
            mode == WalkMode.FLOWERS && phase == WalkPhase.PAUSED -> "尋花已暫停"
            phase == WalkPhase.PAUSED -> "已暫停"
            status.startsWith("尋花") || status.startsWith("道路查詢失敗") -> status.take(55)
            phase == WalkPhase.PARKED -> "已停止 · 原地停留"
            else -> "PikminBee · 原地停留"
        }
    }

    fun dismiss() {
        root?.let { runCatching { windows.removeView(it) } }
        root = null
        panel = null
        stateLabel = null
        pauseAction = null
        stopAction = null
        flowerAction = null
        flowerSummary = null
        speedButtons.clear()
        defaultSpeedAction = null
        currentSpeed = WalkSpeedPreset.DEFAULT
        params = null
        expanded = false
    }
}
