package com.naos.pikminbee.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import androidx.compose.ui.graphics.toArgb
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import com.naos.pikminbee.health.PhysicalStepsStatus
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.widget.ScrollView
import com.naos.pikminbee.health.EstimatedWalkStatus
import com.naos.pikminbee.vision.BloomVisionState
import com.naos.pikminbee.vision.BloomCaptureService
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
    private val onStartRoute: () -> Unit,
    private val onSpeedSelected: (WalkSpeedPreset) -> Unit,
    private val openMap: () -> Unit,
    private val onFlowerVision: () -> Unit,
) {
    companion object {
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val STOP_WALK = "stop_walk"
        const val START_FLOWERS = "start_flowers"
        const val RESTORE_REAL = "restore_real"
        const val RESTORE_VIRTUAL = "restore_virtual"
        const val HIDE = "hide"
    }

    private var beeButton: View? = null
    private var beeIcon: ImageView? = null
    private var headerBeeIcon: ImageView? = null
    private var logoAnimator: AnimatorSet? = null
    private var logoRenderKey: String = ""
    private var quickSummary: TextView? = null
    private var quickSteps: TextView? = null
    private val quickActions = linkedMapOf<OverlayQuickAction, ImageView>()
    private var lastPhase: WalkPhase = WalkPhase.IDLE
    private var lastMode: WalkMode? = null
    private var lastStatus: String = ""
    private var planningRoute = false

    private val windows = context.getSystemService(WindowManager::class.java)
    private val stepsHandler = Handler(Looper.getMainLooper())
    private val stepRefresh = object : Runnable {
        override fun run() {
            updateStepSummary()
            refreshQuick()
            if (root != null) stepsHandler.postDelayed(this, 5_000L)
        }
    }
    private fun updateStepSummary() {
        val virtual = EstimatedWalkStatus.state.value.estimatedSteps
        val physical = PhysicalStepsStatus.state.value
        quickSteps?.text = if (physical.tracking || physical.todaySteps > 0) {
            "虛擬 $virtual 步    實際 ${physical.todaySteps} 步"
        } else "虛擬 $virtual 步    實際 —"
    }
    private var root: LinearLayout? = null
    private var panel: ScrollView? = null
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
            .coerceAtMost(dp(360)).coerceAtLeast(dp(100))
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

    /** Refresh the single-level overlay without altering the active GPS session. */
    fun refreshAppearance() {
        val holder = root ?: return
        holder.background = if (expanded) glass() else null
        val palette = colors()
        fun repaint(view: View) {
            if (view is TextView) view.setTextColor(palette.ink.toArgb())
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) repaint(view.getChildAt(index))
            }
        }
        repaint(holder)
        quickSteps?.setTextColor(palette.muted.toArgb())
        beeIcon?.background = null
        beeButton?.background = beeGlassRim()
        // Re-render semantic actions only after appearance changes, not every step tick.
        quickActions.values.forEach { it.tag = null }
        logoRenderKey = ""
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
    private fun glass() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            colors().surface.copy(alpha = if (isDark()) .86f else .83f).toArgb(),
            colors().soft.copy(alpha = if (isDark()) .79f else .70f).toArgb(),
            colors().surface.copy(alpha = if (isDark()) .88f else .82f).toArgb(),
        ),
    ).apply {
        cornerRadius = dp(26).toFloat()
        setStroke(dp(1).coerceAtLeast(1), colors().stroke.toArgb())
    }

    /** A subtle lens rim around the lone bee logo, not a second status badge. */
    private fun beeGlassRim() = LayerDrawable(arrayOf(
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            colors().surface.copy(alpha = if (isDark()) .67f else .57f).toArgb(),
            colors().soft.copy(alpha = if (isDark()) .56f else .44f).toArgb(),
        )).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1).coerceAtLeast(1), if (isDark())
                Color.argb(210, 245, 252, 255) else Color.argb(210, 255, 255, 255))
        },
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(1).coerceAtLeast(1),
                colors().ink.copy(alpha = if (isDark()) .32f else .38f).toArgb())
        },
    )).apply { setLayerInset(1, dp(2), dp(2), dp(2), dp(2)) }

    private fun softButtonBackground() = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(colors().soft.toArgb())
        // No repeated strokes or glowing/neon borders.
    }

    private fun actionBackground(which: OverlayQuickAction, enabled: Boolean): GradientDrawable {
        val color = when (which) {
            OverlayQuickAction.PRIMARY -> BeeActionColors.primary(lastPhase)
            OverlayQuickAction.STOP -> BeeActionColors.stop(enabled)
            else -> null
        }
        return GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color ?: colors().soft.toArgb())
        }
    }

    private fun onStartRouteOrFlowers(mode: WalkMode) {
        if (mode == WalkMode.ROUTE) onStartRoute()
        else sendAction(START_FLOWERS)
    }

    /** One dynamic start/pause/resume action; never teleport as a side effect. */
    private fun performQuickAction() {
        when (OverlayQuickPolicy.primary(lastPhase)) {
            OverlayPrimaryState.PAUSE -> sendAction(PAUSE)
            OverlayPrimaryState.RESUME -> sendAction(RESUME)
            OverlayPrimaryState.START -> {
                val prefs = AppPreferences(context)
                val route = prefs.routeDraft().isNotEmpty()
                val flowers = prefs.flowerDraft().isNotEmpty()
                val requested = when {
                    lastMode == WalkMode.ROUTE && route -> WalkMode.ROUTE
                    lastMode == WalkMode.FLOWERS && flowers -> WalkMode.FLOWERS
                    route && !flowers -> WalkMode.ROUTE
                    flowers && !route -> WalkMode.FLOWERS
                    else -> null
                }
                if (requested == null) {
                    val message = if (route && flowers) "請先在主 App 選擇路徑或尋花"
                        else "請先在主 App 規劃路線"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    openMap()
                } else onStartRouteOrFlowers(requested)
            }
        }
    }

    private fun iconAction(
        which: OverlayQuickAction, icon: OverlayGlyph, label: String, click: () -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageDrawable(NaosOverlayActionIcon(icon, colors().ink.toArgb(), dp(24)))
        background = RippleDrawable(
            ColorStateList.valueOf(colors().accent.copy(alpha = 0.20f).toArgb()),
            softButtonBackground(), null,
        )
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
        isFocusable = true
        contentDescription = label
        setOnClickListener { click() }
        setOnLongClickListener {
            if (AppPreferences(context).overlayLongPressTips()) {
                Toast.makeText(context, contentDescription, Toast.LENGTH_SHORT).show()
            }
            true
        }
        quickActions[which] = this
    }

    private fun iconUpdate(
        which: OverlayQuickAction, icon: OverlayGlyph, label: String, enabled: Boolean = true,
    ) {
        quickActions[which]?.apply {
            val ink = when {
                !enabled -> colors().muted.toArgb()
                which == OverlayQuickAction.PRIMARY -> BeeActionColors.ON_BRIGHT
                which == OverlayQuickAction.STOP -> BeeActionColors.ON_DANGER
                else -> colors().ink.toArgb()
            }
            val paint = colors()
            val fill = when (which) {
                OverlayQuickAction.PRIMARY -> BeeActionColors.primary(lastPhase)
                OverlayQuickAction.STOP -> BeeActionColors.stop(enabled)
                else -> null
            } ?: paint.soft.toArgb()
            val ripple = paint.ink.copy(alpha = .12f).toArgb()
            val renderKey = "$icon:$ink:$fill:$ripple:$enabled"
            if (tag != renderKey) {
                setImageDrawable(NaosOverlayActionIcon(icon, ink, dp(24)))
                background = RippleDrawable(
                    ColorStateList.valueOf(ripple),
                    actionBackground(which, enabled), null,
                )
                tag = renderKey
            }
            contentDescription = label
            isEnabled = enabled
            alpha = if (enabled) 1f else .42f
        }
    }

    private fun refreshQuick() {
        if (root == null) return
        val prefs = AppPreferences(context)
        val primary = OverlayQuickPolicy.primary(lastPhase)
        val routeReady = prefs.routeDraft().isNotEmpty()
        val flowerReady = prefs.flowerDraft().isNotEmpty()
        val visibleMode = lastMode ?: when {
            routeReady && !flowerReady -> WalkMode.ROUTE
            flowerReady && !routeReady -> WalkMode.FLOWERS
            else -> null
        }
        val label = when {
            planningRoute -> "正在規劃步行道路"
            lastPhase == WalkPhase.MOVING -> if (lastMode == WalkMode.FLOWERS)
                "尋花行走中" else "路徑行走中"
            lastPhase == WalkPhase.PAUSED -> "已暫停"
            lastPhase == WalkPhase.PARKED -> "已停止 · 原地停留"
            lastStatus.contains("失敗") || lastStatus.contains("無法") -> lastStatus.take(25)
            visibleMode != null -> "待命 · ${if (visibleMode == WalkMode.FLOWERS) "尋花" else "路徑"}已就緒"
            routeReady && flowerReady -> "待命 · 請在主 App 選擇模式"
            else -> "待命 · 請先在主 App 規劃"
        }
        val progress = com.naos.pikminbee.location.LocationStatus.progress.value
        val journey = if (lastPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED) &&
            progress.totalMetres > 0.0) {
            val stop = progress.nextWaypoint?.toString() ?: "終點"
            " · $stop/${progress.waypointCount} · ${(progress.fraction * 100).toInt()}%"
        } else ""
        quickSummary?.text = "$label · ${currentSpeed.kilometresPerHour} km/h$journey"
        val primaryLabel = when (primary) {
            OverlayPrimaryState.START -> "開始行走"
            OverlayPrimaryState.PAUSE -> "暫停行走"
            OverlayPrimaryState.RESUME -> "繼續行走"
        }
        iconUpdate(OverlayQuickAction.PRIMARY, when (primary) {
            OverlayPrimaryState.PAUSE -> OverlayGlyph.PAUSE
            OverlayPrimaryState.RESUME -> OverlayGlyph.RESUME
            else -> OverlayGlyph.PLAY
        }, primaryLabel)
        iconUpdate(OverlayQuickAction.STOP, OverlayGlyph.STOP, "停止行走，保持目前位置",
            OverlayQuickPolicy.canStop(lastPhase, planningRoute))
        iconUpdate(OverlayQuickAction.REAL_GPS, OverlayGlyph.REAL, "還原真實 GPS，暫停模擬定位")
        iconUpdate(OverlayQuickAction.VIRTUAL_GPS, OverlayGlyph.VIRTUAL,
            "回到上次虛擬位置，不繼續行走",
            AppPreferences(context).lastVirtualPosition() != null)
        val capturing = BloomVisionState.state.value.phase == BloomVisionState.Phase.CAPTURING
        iconUpdate(OverlayQuickAction.FLOWER_VISION, OverlayGlyph.FLOWER,
            if (capturing) "停止畫面偵測花" else "開啟畫面偵測花並取得系統授權")
        iconUpdate(OverlayQuickAction.SPEED, OverlayGlyph.SPEED,
            "切換行走速度，目前 ${currentSpeed.kilometresPerHour} km/h")
        iconUpdate(OverlayQuickAction.MAP, OverlayGlyph.MAP, "開啟 PikminBee 地圖")
        iconUpdate(OverlayQuickAction.CLOSE, OverlayGlyph.CLOSE, "關閉懸浮視窗，不停止行走")
        refreshBeeLogo()
    }

    /** Logo-only collapsed surface, with status colour and reduced-motion support. */
    private fun refreshBeeLogo() {
        val dark = isDark()
        val renderKey = "$dark:$expanded:$lastPhase"
        if (renderKey == logoRenderKey) return
        logoRenderKey = renderKey
        val tint = BeeLogoPolicy.tint(dark, expanded, lastPhase)
        val glyph = if (lastPhase == WalkPhase.PAUSED)
            OverlayGlyph.BEE_PAUSED else OverlayGlyph.BEE
        beeIcon?.setImageDrawable(NaosOverlayActionIcon(glyph, tint, dp(42)))
        headerBeeIcon?.setImageDrawable(NaosOverlayActionIcon(glyph,
            BeeLogoPolicy.tint(dark, true, lastPhase), dp(30)))
        logoAnimator?.cancel()
        logoAnimator = null
        val bee = beeIcon ?: return
        bee.translationY = 0f
        bee.rotation = 0f
        if (expanded || !ValueAnimator.areAnimatorsEnabled()) return
        val motion = BeeLogoPolicy.motion(lastPhase)
        if (motion == BeeLogoPolicy.Motion.STILL) return
        val floatPixels = if (motion == BeeLogoPolicy.Motion.FLYING) dp(5) else dp(2)
        val time = if (motion == BeeLogoPolicy.Motion.FLYING) 730L else 1450L
        val bob = ObjectAnimator.ofFloat(bee, View.TRANSLATION_Y, 0f, -floatPixels.toFloat())
            .apply {
                duration = time
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        val animations = mutableListOf<android.animation.Animator>(bob)
        if (motion == BeeLogoPolicy.Motion.FLYING) {
            animations.add(ObjectAnimator.ofFloat(bee, View.ROTATION, -6f, 6f).apply {
                duration = time
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            })
        }
        logoAnimator = AnimatorSet().apply { playTogether(animations); start() }
    }

    fun show() {
        if (root != null) return
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // The collapsed bee stays icon-first; its rim is drawn by the 54dp shell.
            background = null
            elevation = 0f
            setPadding(0, 0, 0, 0)
        }
        val bee = ImageView(context).apply {
            setImageDrawable(NaosOverlayActionIcon(OverlayGlyph.BEE, colors().ink.toArgb(), dp(28)))
            background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = "PikminBee 蜜蜂 Logo：點一下展開控制，拖曳移動位置"
            isClickable = true
            isFocusable = true
        }
        val beeShell = FrameLayout(context).apply {
            background = beeGlassRim()
            addView(bee, FrameLayout.LayoutParams(dp(54), dp(54)))
        }
        container.addView(beeShell)
        beeButton = beeShell
        beeIcon = bee
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val description = TextView(context).apply {
            text = "PikminBee · 快捷控制"
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colors().ink.toArgb())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(2), 0)
        }
        header.addView(description, LinearLayout.LayoutParams(0, dp(45), 1f))
        header.addView(ImageView(context).apply {
            setImageDrawable(NaosOverlayActionIcon(OverlayGlyph.BEE,
                BeeLogoPolicy.HONEY, dp(30)))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            contentDescription = "收合懸浮控制，保持目前行走狀態"
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleExpanded() }
            headerBeeIcon = this
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        actions.addView(header)

        val summary = TextView(context).apply {
            text = "待命"
            textSize = 12f
            setTextColor(colors().ink.toArgb())
            setPadding(dp(9), dp(1), dp(9), dp(1))
            minHeight = dp(28)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }
        quickSummary = summary
        actions.addView(summary)
        val stepsLine = TextView(context).apply {
            text = "虛擬 —    實際 —"
            textSize = 11f
            setTextColor(colors().muted.toArgb())
            setPadding(dp(9), dp(1), dp(9), dp(7))
            maxLines = 1
        }
        quickSteps = stepsLine
        actions.addView(stepsLine)

        val buttons = listOf(
            Triple(OverlayQuickAction.PRIMARY, OverlayGlyph.PLAY, "開始／暫停／繼續"),
            Triple(OverlayQuickAction.STOP, OverlayGlyph.STOP, "停止行走"),
            Triple(OverlayQuickAction.REAL_GPS, OverlayGlyph.REAL, "還原真實定位"),
            Triple(OverlayQuickAction.VIRTUAL_GPS, OverlayGlyph.VIRTUAL, "恢復虛擬定位"),
            Triple(OverlayQuickAction.FLOWER_VISION, OverlayGlyph.FLOWER, "畫面偵測花"),
            Triple(OverlayQuickAction.SPEED, OverlayGlyph.SPEED, "切換走路速度"),
            Triple(OverlayQuickAction.MAP, OverlayGlyph.MAP, "打開地圖"),
            Triple(OverlayQuickAction.CLOSE, OverlayGlyph.CLOSE, "關閉懸浮"),
        )
        buttons.chunked(4).forEach { group ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { (which, glyph, label) ->
                row.addView(iconAction(which, glyph, label) {
                    when (which) {
                        OverlayQuickAction.PRIMARY -> performQuickAction()
                        OverlayQuickAction.STOP -> sendAction(STOP_WALK)
                        OverlayQuickAction.REAL_GPS -> sendAction(RESTORE_REAL)
                        OverlayQuickAction.VIRTUAL_GPS -> sendAction(RESTORE_VIRTUAL)
                        OverlayQuickAction.FLOWER_VISION -> {
                            if (BloomVisionState.state.value.phase ==
                                BloomVisionState.Phase.CAPTURING) {
                                context.startService(BloomCaptureService.stopIntent(context))
                            } else onFlowerVision()
                        }
                        OverlayQuickAction.SPEED -> {
                            val next = OverlayQuickPolicy.nextSpeed(currentSpeed)
                            currentSpeed = next
                            onSpeedSelected(next)
                            refreshQuick()
                        }
                        OverlayQuickAction.MAP -> openMap()
                        OverlayQuickAction.CLOSE -> sendAction(HIDE)
                    }
                }, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    marginEnd = dp(4)
                    bottomMargin = dp(5)
                })
            }
            actions.addView(row)
        }

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
        // performClick() needs a real listener; without it taps were silently discarded.
        bee.setOnClickListener { toggleExpanded() }
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
        params = layout
        expanded = false
        stepsHandler.removeCallbacks(stepRefresh)
        stepsHandler.post(stepRefresh)
        container.post { keepVisible(container, layout) }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        root?.apply {
            background = if (expanded) glass() else null
            elevation = if (expanded) dp(13).toFloat() else 0f
            if (expanded) setPadding(dp(7), dp(5), dp(7), dp(5))
            else setPadding(0, 0, 0, 0)
        }
        panel?.animate()?.cancel()
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        beeButton?.visibility = if (expanded) View.GONE else View.VISIBLE
        if (expanded) {
            panel?.alpha = 0f
            panel?.translationY = dp(10).toFloat()
            panel?.animate()?.alpha(1f)?.translationY(0f)
                ?.setDuration(NaosDesignSystem.FAST_MS.toLong())?.start()
            refreshQuick()
        }
        params?.let { placement ->
            placement.width = if (expanded)
                minOf(screenSize().first - dp(24), dp(292)).coerceAtLeast(dp(220))
                else WindowManager.LayoutParams.WRAP_CONTENT
            if (expanded) fitExpandedPanelToScreen()
            // Keep game input focus: no keyboard in the single-level overlay.
            placement.flags = placement.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            root?.let { view ->
                runCatching { windows.updateViewLayout(view, placement) }
                view.post { keepVisible(view, placement) }
            }
        }
        refreshBeeLogo()
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
        lastStatus = status
        planningRoute = planningFlowers
        currentSpeed = speedPreset
        updateStepSummary()
        refreshQuick()
    }

    fun dismiss() {
        stepsHandler.removeCallbacks(stepRefresh)
        logoAnimator?.cancel()
        logoAnimator = null
        logoRenderKey = ""
        root?.let { runCatching { windows.removeView(it) } }
        root = null
        panel = null
        beeButton = null
        quickSummary = null
        quickSteps = null
        beeIcon = null
        headerBeeIcon = null
        quickActions.clear()
        lastPhase = WalkPhase.IDLE
        lastMode = null
        lastStatus = ""
        planningRoute = false
        currentSpeed = WalkSpeedPreset.DEFAULT
        params = null
        expanded = false
    }
}
