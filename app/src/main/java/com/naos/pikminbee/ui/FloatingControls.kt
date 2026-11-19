package com.naos.pikminbee.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.EditText
import android.text.InputType
import com.naos.pikminbee.location.OverlayMapSelection
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.data.SavedRouteState
import android.widget.TextView
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.MapAppearance
import android.content.res.Configuration
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedPreset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Overlay owned by the GPS foreground service, not a second independent location engine. */
class FloatingControls(
    private val context: Context,
    private val sendAction: (String) -> Unit,
    private val onTeleport: (Coordinate) -> Unit,
    private val onStartRoute: () -> Unit,
    private val onOpenSettings: () -> Unit,
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

    private enum class Mode(val display: String) { TELEPORT("瞬移"), ROUTE("路徑"), FLOWERS("尋花"), SAVED("收藏") }
    private var selectedMode = Mode.ROUTE
    private var modeContent: LinearLayout? = null
    private var modeTabs: LinearLayout? = null
    private var stepsSummary: TextView? = null
    private var recentPosition: Coordinate? = null

    private fun showMessage(text: String) {
        stateLabel?.text = text
    }

    private val windows = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var panel: ScrollView? = null
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
    private val placementPrefs = context.getSharedPreferences(
        "pikminbee_floating_placement", Context.MODE_PRIVATE
    )
    private val density: Float get() = context.resources.displayMetrics.density

    private fun dp(value: Int): Int = (density * value).toInt()

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windows.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0)
                return bounds.width() to bounds.height()
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun savePlacement(layout: WindowManager.LayoutParams) {
        placementPrefs.edit()
            .putInt("x_from_right_dp", (layout.x / density).roundToInt())
            .putInt("y_from_top_dp", (layout.y / density).roundToInt())
            .apply()
    }

    private fun bounded(viewWidth: Int, viewHeight: Int, proposed: OverlayPlacement): OverlayPlacement {
        val (width, height) = screenSize()
        return proposed.clamp(
            screenWidth = width, screenHeight = height,
            panelWidth = viewWidth.coerceAtLeast(0), panelHeight = viewHeight.coerceAtLeast(0),
            horizontalMargin = dp(10), topMargin = dp(36), bottomMargin = dp(28),
        )
    }

    private fun keepVisible(view: View, layout: WindowManager.LayoutParams) {
        if (view.width <= 0 || view.height <= 0) return
        val now = OverlayPlacement(layout.x, layout.y)
        val adjusted = bounded(view.width, view.height, now)
        if (adjusted != now) {
            layout.x = adjusted.x
            layout.y = adjusted.y
            runCatching { windows.updateViewLayout(view, layout) }
            savePlacement(layout)
        }
    }

    private fun fitExpandedPanelToScreen() {
        val scroll = panel ?: return
        val child = scroll.getChildAt(0) ?: return
        val (width, height) = screenSize()
        val availableWidth = (width - dp(20)).coerceAtLeast(dp(120))
        val availableHeight = (height - dp(36) - dp(28) - dp(72)).coerceAtLeast(dp(100))
        child.measure(
            View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val targetHeight = child.measuredHeight.coerceAtMost(availableHeight)
        val current = scroll.layoutParams as? LinearLayout.LayoutParams ?: return
        if (current.height != targetHeight) {
            current.height = targetHeight
            scroll.layoutParams = current
        }
    }

    /** Called on orientation change; never creates a service or resumes walking. */
    fun onScreenChanged() {
        params?.let { placement ->
            root?.let { view ->
                view.post {
                    if (expanded) fitExpandedPanelToScreen()
                    keepVisible(view, placement)
                }
            }
        }
    }

    private fun isDark(): Boolean = when (AppPreferences(context).appearance()) {
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
        MapAppearance.SYSTEM -> context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun glass() = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(if (isDark()) Color.argb(220, 32, 52, 81)
            else Color.argb(231, 242, 248, 255))
        setStroke(dp(1), if (isDark()) Color.argb(180, 215, 235, 255)
            else Color.argb(225, 255, 255, 255))
    }

    private fun action(label: String, click: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(if (isDark()) Color.rgb(241, 247, 255) else Color.rgb(29, 48, 78))
        gravity = Gravity.CENTER
        minWidth = dp(66)
        minHeight = dp(48)
        setPadding(dp(9), dp(7), dp(9), dp(7))
        setOnClickListener { click() }
    }


    /** All four tabs are independent of whichever walking mode is currently active. */
    private fun refreshTabs() {
        val bar = modeTabs ?: return
        bar.removeAllViews()
        Mode.entries.forEach { mode ->
            val tab = action(mode.display) {
                selectedMode = mode
                refreshTabs()
                renderMode()
            }.apply {
                minWidth = dp(74)
                textSize = 12f
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (mode == selectedMode) Color.rgb(55, 118, 199)
                        else if (isDark()) Color.rgb(43, 66, 98) else Color.rgb(233, 242, 254))
                }
                if (mode == selectedMode) setTextColor(Color.WHITE)
                contentDescription = "懸浮切換到" + mode.display
            }
            bar.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
    }

    private fun renderMode() {
        val content = modeContent ?: return
        content.removeAllViews()
        val prefs = AppPreferences(context)
        fun heading(name: String) {
            content.addView(action(name) { }.apply {
                textSize = 12f
                setOnClickListener(null)
            })
        }
        fun line(vararg views: View) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            views.forEach { row.addView(it,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
            content.addView(row)
        }
        fun pointFromMap(): Coordinate? = OverlayMapSelection.picked.value
        when (selectedMode) {
            Mode.TELEPORT -> {
                heading("⌖ 輸入座標／選擇位置，只有按瞬移才會改變 GPS")
                val input = EditText(context).apply {
                    textSize = 12f
                    inputType = InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                    hint = "25.033000, 121.565400"
                    setTextColor(if (isDark()) Color.WHITE else Color.rgb(30, 51, 79))
                    setHintTextColor(Color.GRAY)
                    setText(pointFromMap()?.label() ?: "")
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                }
                content.addView(input)
                line(
                    action("選取座標") {
                        val coordinate = Coordinate.parseCombined(input.text.toString())
                        if (coordinate != null) {
                            OverlayMapSelection.picked.value = coordinate
                            showMessage("已選取座標；尚未瞬移")
                        } else showMessage("座標格式錯誤，請輸入緯度, 經度")
                    },
                    action("瞬移") {
                        val point = Coordinate.parseCombined(input.text.toString())
                            ?: pointFromMap()
                        if (point == null) showMessage("請先提供有效的座標")
                        else onTeleport(point)
                    },
                )
                content.addView(action("☆ 收藏所選位置") {
                    val point = Coordinate.parseCombined(input.text.toString()) ?: pointFromMap()
                    if (point == null) showMessage("尚無選取位置")
                    else {
                        prefs.addPlace("浮窗收藏 " + (prefs.places().size + 1), point)
                        showMessage("已新增收藏位置")
                    }
                })
            }
            Mode.ROUTE, Mode.FLOWERS -> {
                val mode = if (selectedMode == Mode.ROUTE) WalkMode.ROUTE else WalkMode.FLOWERS
                val stops = if (mode == WalkMode.ROUTE) prefs.routeDraft() else prefs.flowerDraft()
                heading("從目前位置起步 · 不會跳到第一個路點")
                heading("路點順序 · " + stops.size + " 個目的地")
                content.addView(action("● 起點：" +
                    (com.naos.pikminbee.location.LocationStatus.selected.value
                        ?: OverlayMapSelection.deviceFix.value)?.label().orEmpty()
                ) { }.apply { setOnClickListener(null); textSize = 11f })
                stops.forEachIndexed { index, point ->
                    line(
                        action((index + 1).toString() + ". " + point.label()) {
                            OverlayMapSelection.picked.value = point
                        }.apply { textSize = 10f },
                        action("↑") {
                            if (index > 0) {
                                val next = stops.toMutableList()
                                val old = next.removeAt(index)
                                next.add(index - 1, old)
                                if (mode == WalkMode.ROUTE) prefs.saveRouteDraft(next)
                                else prefs.saveFlowerDraft(next)
                                renderMode()
                            }
                        }.apply { minWidth = dp(30) },
                        action("×") {
                            val next = stops.filterIndexed { i, _ -> i != index }
                            if (mode == WalkMode.ROUTE) prefs.saveRouteDraft(next)
                            else prefs.saveFlowerDraft(next)
                            renderMode()
                        }.apply { minWidth = dp(30) },
                    )
                }
                line(
                    action("＋ 加入選取路點") {
                        val point = pointFromMap()
                        if (point == null) showMessage("請先在地圖選取路點")
                        else if (stops.size >= 14) showMessage("最多新增 14 個路點")
                        else {
                            val next = stops + point
                            if (mode == WalkMode.ROUTE) prefs.saveRouteDraft(next)
                            else prefs.saveFlowerDraft(next)
                            renderMode()
                        }
                    },
                    action("↶ 復原") {
                        if (stops.isNotEmpty()) {
                            if (mode == WalkMode.ROUTE) prefs.saveRouteDraft(stops.dropLast(1))
                            else prefs.saveFlowerDraft(stops.dropLast(1))
                            renderMode()
                        }
                    },
                )
                line(
                    action("▶ 開始" + selectedMode.display) { onStartRouteOrFlowers(mode) },
                    action("☆ 收藏路線") {
                        if (stops.isEmpty()) showMessage("請先新增路點")
                        else runCatching {
                            prefs.addSavedRoute("浮窗" + selectedMode.display, mode, stops)
                        }.onSuccess { showMessage("路線已收藏") }
                            .onFailure { showMessage("儲存失敗：" + it.message) }
                    },
                )
            }
            Mode.SAVED -> {
                heading("☆ 收藏地點 · 在浮窗直接操作")
                prefs.places().take(10).forEach { place ->
                    line(
                        action(place.name) {
                            OverlayMapSelection.picked.value = place.coordinate
                            selectedMode = Mode.TELEPORT
                            refreshTabs()
                            renderMode()
                        },
                        action("瞬移") { onTeleport(place.coordinate) },
                    )
                }
                heading("收藏路線")
                prefs.savedRoutes().take(10).forEach { saved ->
                    line(
                        action(saved.name) {
                            prefs.loadSavedRouteIntoDraft(saved)
                            selectedMode = if (saved.mode == WalkMode.FLOWERS) Mode.FLOWERS
                                else Mode.ROUTE
                            refreshTabs()
                            renderMode()
                        },
                        action("開始") {
                            prefs.loadSavedRouteIntoDraft(saved)
                            onStartRouteOrFlowers(saved.mode)
                        },
                    )
                }
            }
        }
        panel?.post { if (expanded) fitExpandedPanelToScreen() }
    }

    private fun onStartRouteOrFlowers(mode: WalkMode) {
        if (mode == WalkMode.ROUTE) onStartRoute()
        else sendAction(START_FLOWERS)
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
        }
        val modeBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val modePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        actions.addView(modeBar)
        actions.addView(modePanel)
        modeTabs = modeBar
        modeContent = modePanel
        refreshTabs()
        renderMode()
        val description = action("全功能控制中心 · 待機") { }
        description.setOnClickListener(null)
        description.textSize = 11f
        actions.addView(description, 0)
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
        val steps = action("⌁ 模擬行走估算 0 步 · 非真實感測") { onOpenSettings() }.apply {
            textSize = 11f
            contentDescription = "估算步數，Health Connect 設定請至主 App"
        }
        actions.addView(steps)
        stepsSummary = steps
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
        actions.addView(action("♡ Health Connect 與其他設定請到主 App") { onOpenSettings() })
        val scroll = ScrollView(context).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            isFillViewport = false
            addView(actions)
        }
        container.addView(scroll)
        val stored = OverlayPlacement.fromStoredDp(
            placementPrefs.getInt("x_from_right_dp", OverlayPlacement.DEFAULT_X_DP),
            placementPrefs.getInt("y_from_top_dp", OverlayPlacement.DEFAULT_Y_DP),
        )
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(stored.x)
            y = dp(stored.y)
        }
        container.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            keepVisible(view, layout)
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
                        val next = bounded(
                            container.width, container.height,
                            OverlayPlacement((startX - dx).toInt(), (startY + dy).toInt()),
                        )
                        layout.x = next.x
                        layout.y = next.y
                        runCatching { windows.updateViewLayout(container, layout) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        savePlacement(layout)
                        true
                    } else {
                        view.performClick()
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) savePlacement(layout)
                    true
                }
                else -> false
            }
        }
        windows.addView(container, layout)
        root = container
        panel = scroll
        stateLabel = description
        pauseAction = pause
        stopAction = stop
        flowerAction = startFlowers
        flowerSummary = summary
        defaultSpeedAction = setDefault
        params = layout
        expanded = false
        container.post { keepVisible(container, layout) }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) fitExpandedPanelToScreen()
        params?.let { placement ->
            placement.flags = if (expanded)
                placement.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            else placement.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            root?.let { view ->
                if (!expanded) context.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                runCatching { windows.updateViewLayout(view, placement) }
                view.post { keepVisible(view, placement) }
            }
        }
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
                    setColor(if (selected) if (isDark()) Color.rgb(52, 103, 177)
                        else Color.rgb(207, 227, 255) else Color.TRANSPARENT)
                    setStroke(dp(1), if (selected) Color.rgb(106, 168, 247)
                        else if (isDark()) Color.rgb(165, 185, 212) else Color.rgb(105, 136, 181))
                }
                button.contentDescription = "速度：${option.display}" +
                    if (selected) "，目前選擇" else ""
            }
        }

        stopAction?.text = if (planningFlowers) "取消規劃" else "停止行走"
        stopAction?.isEnabled = walking || planningFlowers
        stopAction?.alpha = if (walking || planningFlowers) 1f else .4f
        stepsSummary?.text = "⌁ 模擬行走估算 " +
            EstimatedWalkStatus.state.value.estimatedSteps + " 步 · 非真實感測"
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
        modeContent = null
        modeTabs = null
        stepsSummary = null
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
