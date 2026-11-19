package com.naos.pikminbee.ui

import android.content.Context
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.PixelFormat
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import com.naos.pikminbee.health.PhysicalStepsStatus
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.EditText
import android.text.InputType
import com.naos.pikminbee.location.OverlayMapSelection
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.domain.Coordinate
import com.naos.pikminbee.domain.RoutePointDraft
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

    private enum class Mode(val display: String) {
        TELEPORT("瞬移"), ROUTE("路徑"), FLOWERS("尋花"), SAVED("收藏")
    }
    private var selectedMode = Mode.TELEPORT
    private var modeContent: LinearLayout? = null
    private var modeTabs: LinearLayout? = null
    private var stepsSummary: TextView? = null
    private var beeButton: TextView? = null
    private var quickSummary: TextView? = null
    private var quickPrimary: TextView? = null
    private var quickSecondary: TextView? = null
    private var advancedSection: LinearLayout? = null
    private var advancedVisible: Boolean = false
    private var lastPhase: WalkPhase = WalkPhase.IDLE
    private var lastMode: WalkMode? = null

    private fun showMessage(text: String) {
        stateLabel?.text = text
    }

    private val windows = context.getSystemService(WindowManager::class.java)
    private val stepsHandler = Handler(Looper.getMainLooper())
    private val stepRefresh = object : Runnable {
        override fun run() {
            updateStepSummary()
            if (root != null) stepsHandler.postDelayed(this, 5_000L)
        }
    }
    private fun updateStepSummary() {
        val virtualSteps = EstimatedWalkStatus.state.value.estimatedSteps
        val physical = PhysicalStepsStatus.state.value
        stepsSummary?.text = if (physical.tracking || physical.todaySteps > 0) {
            "⌁ 虛擬估算 $virtualSteps 步  ·  本機實際感測 ${physical.todaySteps} 步｜♡ HC 在設定"
        } else "⌁ 虛擬估算 $virtualSteps 步 · ♡ Health Connect 在設定"
    }
    private var root: LinearLayout? = null
    private var panel: ScrollView? = null
    private var stateLabel: TextView? = null
    private var pauseAction: TextView? = null
    private var stopAction: TextView? = null
    private val speedButtons = linkedMapOf<WalkSpeedPreset, TextView>()
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
        val availableHeight = (height - dp(36) - dp(28) - dp(72))
            .coerceAtMost(dp(if (advancedVisible) 610 else 360)).coerceAtLeast(dp(100))
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

    /** Redraw the overlay in place after light/dark/system mode changes.
     * Never dismiss the view, reset the selected tab or alter GPS state.
     */
    fun refreshAppearance() {
        val holder = root ?: return
        holder.background = glass()
        val foreground = if (isDark()) Color.rgb(241, 247, 255)
            else Color.rgb(29, 48, 78)
        fun visit(view: View) {
            if (view is TextView) {
                view.setTextColor(foreground)
                when {
                    view is EditText -> {
                        view.setHintTextColor(if (isDark()) Color.LTGRAY else Color.GRAY)
                        view.background = softButtonBackground()
                    }
                    view.hasOnClickListeners() && view !== stepsSummary ->
                        view.background = softButtonBackground()
                    else -> view.background = null
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(holder)
        refreshTabs()
        renderMode()
        speedButtons.forEach { (option, button) ->
            val selected = option == currentSpeed
            button.tag = selected
            button.setTextColor(if (selected) colors().onAccent.toArgb() else foreground)
            button.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (selected) colors().accent.toArgb()
                    else colors().soft.toArgb())
            }
        }
        refreshQuick()
        holder.invalidate()
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

    private fun colors(): NaosPalette = NaosDesignSystem.palette(
        AppPreferences(context).naosThemeFamily(), isDark())

    /** Cross-app overlays cannot sample another application's secure pixels. */
    private fun glass() = GradientDrawable().apply {
        cornerRadius = dp(26).toFloat()
        val p = colors()
        setColor(p.surface.copy(alpha = if (isDark()) .91f else .88f).toArgb())
        setStroke(dp(1), p.stroke.toArgb())
    }

    private fun softButtonBackground() = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(colors().soft.toArgb())
        // No repeated strokes or glowing/neon borders.
    }

    private fun action(label: String, click: () -> Unit): TextView = TextView(context).apply {
        text = label
        background = softButtonBackground()
        textSize = 14f
        setTextColor(colors().ink.toArgb())
        gravity = Gravity.CENTER
        minWidth = dp(66)
        minHeight = dp(48)
        setPadding(dp(9), dp(7), dp(9), dp(7))
        setOnClickListener { click() }
    }


    /** Four semantic vector tabs; no Unicode/emoji icon substitutions. */
    private fun refreshTabs() {
        val bar = modeTabs ?: return
        bar.removeAllViews()
        val palette = colors()
        Mode.entries.forEachIndexed { index, mode ->
            val selected = mode == selectedMode
            val ink = if (selected) palette.onAccent.toArgb() else palette.ink.toArgb()
            val tab = action(mode.display) {
                selectedMode = mode
                refreshTabs()
                renderMode()
                refreshQuick()
            }.apply {
                minWidth = dp(74)
                minHeight = dp(56)
                textSize = 12f
                setTextColor(ink)
                compoundDrawablePadding = dp(3)
                setCompoundDrawablesWithIntrinsicBounds(
                    null, NaosOverlayModeIcon(index, ink, dp(22)), null, null)
                background = GradientDrawable().apply {
                    cornerRadius = dp(15).toFloat()
                    setColor(if (selected) palette.accent.toArgb() else palette.soft.toArgb())
                }
                contentDescription = "懸浮切換到" + mode.display
            }
            bar.addView(tab, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginEnd = dp(3)
            })
        }
    }

    private fun renderMode() {
        val content = modeContent ?: return
        content.removeAllViews()
        val prefs = AppPreferences(context)
        fun heading(name: String) {
            content.addView(action(name) { }.apply {
                textSize = 12f
                minHeight = dp(31)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = null
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener(null)
            })
        }
        fun line(vararg views: View) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            views.forEachIndexed { index, view ->
                row.addView(view, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT,
                    if (views.size >= 3 && index == 0) 2.4f else 1f,
                ).apply {
                    marginEnd = dp(5)
                    topMargin = dp(4)
                })
            }
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
                ) { }.apply {
                    setOnClickListener(null)
                    textSize = 11f
                    background = null
                    minHeight = dp(29)
                })
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
                        else if (mode == WalkMode.ROUTE) {
                            val result = RoutePointDraft.select(
                                stops,
                                com.naos.pikminbee.location.LocationStatus.selected.value
                                    ?: OverlayMapSelection.deviceFix.value,
                                point,
                            )
                            if (result.outcome == RoutePointDraft.Outcome.ADDED) {
                                prefs.saveRouteDraft(result.stops)
                                renderMode()
                            } else showMessage("路點未新增：已存在、過近或超過 14 點")
                        } else if (stops.size >= 14) showMessage("最多 14 個花點")
                        else {
                            prefs.saveFlowerDraft(stops + point)
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

    /** The first level of the overlay performs the primary action for each tab.
     * Old full controls remain accessible as a second level when needed.
     */
    private fun performQuickAction() {
        val walking = lastPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)
        when (selectedMode) {
            Mode.TELEPORT -> {
                val picked = OverlayMapSelection.picked.value
                if (picked == null) {
                    showMessage("請先在地圖選擇位置，或展開詳細操作輸入經緯度")
                    advancedVisible = true
                    advancedSection?.visibility = View.VISIBLE
                    panel?.post { fitExpandedPanelToScreen() }
                } else onTeleport(picked)
            }
            Mode.ROUTE, Mode.FLOWERS -> {
                val requested = if (selectedMode == Mode.ROUTE) WalkMode.ROUTE
                    else WalkMode.FLOWERS
                if (walking && lastMode == requested) {
                    sendAction(if (lastPhase == WalkPhase.PAUSED) RESUME else PAUSE)
                } else onStartRouteOrFlowers(requested)
            }
            Mode.SAVED -> {
                advancedVisible = true
                advancedSection?.visibility = View.VISIBLE
                panel?.post { fitExpandedPanelToScreen() }
            }
        }
    }

    private fun refreshQuick() {
        val prefs = AppPreferences(context)
        val active = lastPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)
        val requested = when (selectedMode) {
            Mode.ROUTE -> WalkMode.ROUTE
            Mode.FLOWERS -> WalkMode.FLOWERS
            else -> null
        }
        val sameWalk = active && requested != null && lastMode == requested
        quickSummary?.text = when (selectedMode) {
            Mode.TELEPORT -> "瞬移 · " +
                (OverlayMapSelection.picked.value?.let { "已選擇 ${it.label()}" }
                    ?: "預設模式 · 請點地圖或輸入座標")
            Mode.ROUTE -> "路徑 · ${prefs.routeDraft().size} 個目的地" +
                if (sameWalk) (if (lastPhase == WalkPhase.PAUSED) " · 已暫停" else " · 行走中") else ""
            Mode.FLOWERS -> "尋花 · ${prefs.flowerDraft().size} 個候選花點" +
                if (sameWalk) " · 巡迴中" else ""
            Mode.SAVED -> "收藏 · 地點與已儲存路線"
        }
        quickPrimary?.apply {
            text = when {
                sameWalk -> if (lastPhase == WalkPhase.PAUSED) "繼續" else "暫停"
                selectedMode == Mode.TELEPORT -> "立即瞬移"
                selectedMode == Mode.ROUTE -> "開始路徑"
                selectedMode == Mode.FLOWERS -> "開始尋花"
                else -> "選擇收藏"
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(colors().accent.toArgb())
            }
            setTextColor(colors().onAccent.toArgb())
        }
        quickSecondary?.text = if (active) "停止" else "打開地圖"
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
        beeButton = bee
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val description = action("PikminBee\n懸浮快速操作 · Regular") { onOpenSettings() }.apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = null
        }
        header.addView(description, LinearLayout.LayoutParams(0, dp(66), 1f))
        header.addView(action("×") { toggleExpanded() }.apply {
            textSize = 23f
            background = null
            minWidth = dp(48)
            contentDescription = "收合浮窗"
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        actions.addView(header)

        val modeBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(modeBar)
        val summary = action("瞬移 · 預設模式") { }.apply {
            background = softButtonBackground()
            gravity = Gravity.CENTER_VERTICAL
            textSize = 13f
            setOnClickListener(null)
            minHeight = dp(65)
        }
        quickSummary = summary
        actions.addView(summary)
        val quickRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val primary = action("選取位置後瞬移") { performQuickAction() }.apply {
            minHeight = dp(51)
        }
        val secondary = action("打開地圖") {
            if (lastPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED))
                sendAction(STOP_WALK)
            else openMap()
        }.apply { minHeight = dp(51) }
        quickPrimary = primary
        quickSecondary = secondary
        quickRow.addView(primary, LinearLayout.LayoutParams(0, dp(51), 1f).apply {
            marginEnd = dp(5)
        })
        quickRow.addView(secondary, LinearLayout.LayoutParams(0, dp(51), 1f))
        actions.addView(quickRow)

        val advanced = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        advancedSection = advanced
        actions.addView(action("⌃ 展開詳細操作") {
            advancedVisible = !advancedVisible
            advanced.visibility = if (advancedVisible) View.VISIBLE else View.GONE
            panel?.post { fitExpandedPanelToScreen() }
        }.apply {
            background = softButtonBackground()
            textSize = 12f
            contentDescription = "展開或收起完整的模式控制"
        })
        actions.addView(advanced)

        val modePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        advanced.addView(modePanel)
        modeTabs = modeBar
        modeContent = modePanel
        refreshTabs()
        renderMode()
        refreshQuick()
        val speedHeading = action("行走速度") { }.apply {
            textSize = 12f
            background = null
            minHeight = dp(29)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener(null)
        }
        advanced.addView(speedHeading)
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
        advanced.addView(speedRow)
        val steps = action("⌁ 模擬行走估算 0 步 · 非真實感測") { onOpenSettings() }.apply {
            textSize = 11f
            background = null
            contentDescription = "估算步數，Health Connect 設定請至主 App"
        }
        stepsSummary = steps
        val row1 = LinearLayout(context)
        val pause = action("暫停") { sendAction(if (pauseAction?.text == "繼續") RESUME else PAUSE) }
        val stop = action("停止行走") { sendAction(STOP_WALK) }
        row1.addView(pause)
        row1.addView(stop)
        advanced.addView(row1)
        advanced.addView(steps)
        val row2 = LinearLayout(context)
        row2.addView(action("地圖") { openMap() })
        row2.addView(action("隱藏") { sendAction(HIDE) })
        advanced.addView(row2)
        val row3 = LinearLayout(context)
        row3.addView(action("恢復真實 GPS") { sendAction(RESTORE_REAL) })
        advanced.addView(row3)
        advanced.addView(action("Health Connect 與其他設定請到主 App") { onOpenSettings() })
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
        stateLabel = summary
        pauseAction = pause
        stopAction = stop
        params = layout
        expanded = false
        stepsHandler.removeCallbacks(stepRefresh)
        stepsHandler.post(stepRefresh)
        container.post { keepVisible(container, layout) }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        advancedVisible = false
        advancedSection?.visibility = View.GONE
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        beeButton?.visibility = if (expanded) View.GONE else View.VISIBLE
        if (expanded) refreshQuick()
        params?.let { placement ->
            placement.width = if (expanded)
                minOf(screenSize().first - dp(24), dp(350)).coerceAtLeast(dp(220))
                else WindowManager.LayoutParams.WRAP_CONTENT
            if (expanded) fitExpandedPanelToScreen()
            placement.flags = if (expanded)
                placement.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            else placement.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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
        lastPhase = phase
        lastMode = mode
        val walking = phase == WalkPhase.MOVING || phase == WalkPhase.PAUSED
        pauseAction?.text = if (phase == WalkPhase.PAUSED) "繼續" else "暫停"
        pauseAction?.isEnabled = walking
        pauseAction?.alpha = if (walking) 1.0f else 0.4f

        currentSpeed = speedPreset
        speedButtons.forEach { (option, button) ->
            val selected = option == speedPreset
            if (button.tag != selected) {
                button.tag = selected
                button.setTextColor(if (selected) colors().onAccent.toArgb()
                    else colors().ink.toArgb())
                button.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) colors().accent.toArgb()
                        else colors().soft.toArgb())
                }
                button.contentDescription = "速度：${option.display}" +
                    if (selected) "，目前選擇" else ""
            }
        }

        stopAction?.text = if (planningFlowers) "取消規劃" else "停止行走"
        stopAction?.isEnabled = walking || planningFlowers
        stopAction?.alpha = if (walking || planningFlowers) 1f else .4f
        updateStepSummary()
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
        refreshQuick()
    }

    fun dismiss() {
        stepsHandler.removeCallbacks(stepRefresh)
        root?.let { runCatching { windows.removeView(it) } }
        root = null
        panel = null
        beeButton = null
        quickSummary = null
        quickPrimary = null
        quickSecondary = null
        advancedSection = null
        advancedVisible = false
        lastPhase = WalkPhase.IDLE
        lastMode = null
        stateLabel = null
        modeContent = null
        modeTabs = null
        stepsSummary = null
        pauseAction = null
        stopAction = null
        speedButtons.clear()
        currentSpeed = WalkSpeedPreset.DEFAULT
        params = null
        expanded = false
    }
}
