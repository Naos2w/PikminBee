package com.naos.pikminbee.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import androidx.compose.ui.graphics.toArgb
import android.graphics.PixelFormat
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import com.naos.pikminbee.health.HealthConnectRealSteps
import com.naos.pikminbee.health.PhysicalStepLedger
import com.naos.pikminbee.health.PhysicalStepsStatus
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.widget.ScrollView
import com.naos.pikminbee.health.EstimatedWalkStatus
import android.widget.TextView
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.data.PreparedRouteState
import com.naos.pikminbee.data.MapAppearance
import android.content.res.Configuration
import com.naos.pikminbee.domain.WalkMode
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.RouteSurfaceStatusPolicy
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.data.WalkSpeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Overlay owned by the GPS foreground service, not a second independent location engine. */
class FloatingControls(
    private val context: Context,
    private val sendAction: (String) -> Unit,
    private val onStartRoute: () -> Unit,
    private val openMap: () -> Unit,
    private val chooseVirtualPoint: () -> Unit,
    private val onSpeedSelected: (WalkSpeedPreset) -> Unit,
) {
    companion object {
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val STOP_WALK = "stop_walk"
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
    private val dailyReadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dailyReadJob: Job? = null
    private val dailyStepsCache = FloatingDailyStepsCache()

    private val stepRefresh = object : Runnable {
        override fun run() {
            updateStepSummary()
            requestDailySteps()
            refreshQuick()
            if (root != null) stepsHandler.postDelayed(this, 5_000L)
        }
    }

    private fun refreshDailyPeriod(): FloatingDayKey {
        val period = FloatingDayKey.now()
        // Do not reuse a Health Connect aggregate across a midnight or a time-zone
        // change on the same date; drop the old async query before it finishes.
        if (dailyStepsCache.ensurePeriod(period)) {
            dailyReadJob?.cancel()
            dailyReadJob = null
        }
        return period
    }

    /** Never query Health Connect on each 5-second overlay refresh. */
    private fun requestDailySteps(force: Boolean = false) {
        if (root == null || !expanded) return
        val period = refreshDailyPeriod()
        if (dailyReadJob?.isActive == true) {
            if (!force) return
            dailyReadJob?.cancel()
            dailyReadJob = null
        }
        val token = dailyStepsCache.begin(
            period, SystemClock.elapsedRealtime(), force,
        ) ?: return
        // A newly opened overlay first hides its potentially stale aggregate.
        // The sensor fallback stays available while Health Connect is being read.
        updateStepSummary()
        dailyReadJob = dailyReadScope.launch {
            // hasReadPermission() and aggregate() remain on Dispatchers.IO. A
            // null read means denied/revoked/unavailable, never a total of zero.
            val aggregate = HealthConnectRealSteps.todayAggregate(context)
            dailyStepsCache.complete(
                token, period, FloatingDayKey.now(),
                SystemClock.elapsedRealtime(), aggregate,
            )
            updateStepSummary()
        }
    }

    private fun updateStepSummary() {
        val period = refreshDailyPeriod()
        val today = period.date
        val physical = PhysicalStepsStatus.state.value
        // A legacy sensor records only the period when local tracking was active.
        val currentLocal = if (physical.date == today &&
            (physical.tracking || physical.todaySteps > 0L)) physical.todaySteps
            else PhysicalStepLedger(context).todaysCount().takeIf { it > 0L }
        val hasWalk = lastMode != null && lastPhase in
            listOf(WalkPhase.MOVING, WalkPhase.PAUSED, WalkPhase.PARKED)
        val currentWalk = com.naos.pikminbee.location.LocationStatus.walkEstimatedSteps.value
        val display = FloatingDailyStepsPolicy.render(
            dailyStepsCache.currentValue(period, SystemClock.elapsedRealtime()),
            currentLocal,
            currentWalk.takeIf { hasWalk },
        )
        quickSteps?.text = display
        quickSteps?.contentDescription = display
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

    /** Opaque, light-weight matte surfaces. No backdrop sampling or blur. */
    private fun glass() = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        setColor(colors().surface.toArgb())
        setStroke(dp(1).coerceAtLeast(1), colors().stroke.toArgb())
    }

    /** The original outline bee with a visible but inexpensive circular rim. */
    private fun beeGlassRim() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(colors().surface.toArgb())
        setStroke(dp(2).coerceAtLeast(1), colors().stroke.toArgb())
    }

    private fun softButtonBackground() = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(colors().soft.toArgb())
        // No repeated strokes or glowing/neon borders.
    }

    /** Active GPS uses a solid accent, a CONTRASTING rim and a tiny dot.
     *  Both use the approved theme palette; no glowing or animated effects. */
    private fun actionBackground(which: OverlayQuickAction, enabled: Boolean): Drawable {
        val activeGps = OverlayQuickPolicy.isGpsHighlighted(
            which, com.naos.pikminbee.location.LocationStatus.mockActive.value)
        val palette = colors()
        val color = when (which) {
            OverlayQuickAction.PRIMARY -> BeeActionColors.primary(lastPhase)
            OverlayQuickAction.STOP -> BeeActionColors.stop(enabled)
            else -> null
        }
        val base = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color ?: if (activeGps) palette.accent.toArgb() else palette.soft.toArgb())
            // Using the SAME rim and fill colour made the selected state hard to see.
            if (activeGps) setStroke(dp(2).coerceAtLeast(1), palette.onAccent.toArgb())
        }
        if (!activeGps) return base
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.onAccent.toArgb())
        }
        return LayerDrawable(arrayOf(base, dot)).apply {
            setLayerGravity(1, Gravity.TOP or Gravity.END)
            setLayerWidth(1, dp(8))
            setLayerHeight(1, dp(8))
            setLayerInset(1, 0, dp(5), dp(5), 0)
        }
    }

    /** Main and overlay use the same prepared road, never an unreviewed draft. */
    private fun performQuickAction() {
        when (OverlayQuickPolicy.primary(lastPhase)) {
            OverlayPrimaryState.PAUSE -> sendAction(PAUSE)
            OverlayPrimaryState.RESUME -> sendAction(RESUME)
            OverlayPrimaryState.START -> {
                val prefs = AppPreferences(context)
                val routeReady = PreparedRouteState.route.value?.stops ==
                    prefs.routeDraft() && prefs.routeDraft().isNotEmpty()
                // The overlay no longer starts new flower routes. Keep legacy
                // running walks controllable with Pause / Resume / Stop only.
                val requested = if (routeReady) WalkMode.ROUTE else null
                val preview = requested?.let { PreparedRouteState.get(it) }
                when {
                    requested == null -> {
                        Toast.makeText(context, "請先在主 App 規劃並預覽道路",
                            Toast.LENGTH_SHORT).show()
                        openMap()
                    }
                    preview?.requiresSnapConfirmation == true -> {
                        Toast.makeText(context, "吸附距離較大，請在主 App 確認",
                            Toast.LENGTH_SHORT).show()
                        openMap()
                    }
                    else -> onStartRoute()
                }
            }
        }
    }

    /** Both GPS icons are clickable. A missing old virtual fix opens explicit map
     *  selection instead of leaving a grey, non-working GPS button. */
    private fun clickGps(action: OverlayQuickAction) {
        val mockActive = com.naos.pikminbee.location.LocationStatus.mockActive.value
        val hasSaved = AppPreferences(context).lastVirtualPosition() != null
        when (OverlayQuickPolicy.gpsPress(action, mockActive, hasSaved)) {
            OverlayGpsPress.ALREADY_SELECTED ->
                Toast.makeText(context,
                    if (mockActive) "目前使用虛擬 GPS" else "目前使用真實 GPS 模式",
                    Toast.LENGTH_SHORT).show()
            OverlayGpsPress.SWITCH_REAL -> sendAction(RESTORE_REAL)
            OverlayGpsPress.SWITCH_VIRTUAL -> sendAction(RESTORE_VIRTUAL)
            OverlayGpsPress.SELECT_VIRTUAL_POINT -> {
                Toast.makeText(context, "尚未有可恢復的虛擬位置，請先在瞬移頁選點",
                    Toast.LENGTH_LONG).show()
                chooseVirtualPoint()
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
            val activeGps = OverlayQuickPolicy.isGpsHighlighted(
                which, com.naos.pikminbee.location.LocationStatus.mockActive.value)
            val ink = when {
                !enabled -> colors().muted.toArgb()
                which == OverlayQuickAction.PRIMARY -> BeeActionColors.ON_BRIGHT
                which == OverlayQuickAction.STOP -> BeeActionColors.ON_DANGER
                activeGps -> colors().onAccent.toArgb()
                else -> colors().ink.toArgb()
            }
            val paint = colors()
            val fill = when (which) {
                OverlayQuickAction.PRIMARY -> BeeActionColors.primary(lastPhase)
                OverlayQuickAction.STOP -> BeeActionColors.stop(enabled)
                else -> null
            } ?: paint.soft.toArgb()
            val ripple = paint.ink.copy(alpha = .12f).toArgb()
            val renderKey = "$icon:$ink:$fill:$ripple:$enabled:$activeGps"
            if (tag != renderKey) {
                setImageDrawable(NaosOverlayActionIcon(icon, ink, dp(24)))
                background = RippleDrawable(
                    ColorStateList.valueOf(ripple),
                    actionBackground(which, enabled), null,
                )
                tag = renderKey
            }
            isSelected = activeGps
            contentDescription = when (which) {
                OverlayQuickAction.REAL_GPS, OverlayQuickAction.VIRTUAL_GPS ->
                    label + if (activeGps) " · 目前使用此 GPS 模式" else " · 未啟用"
                else -> label
            }
            isEnabled = enabled
            alpha = when {
                !enabled -> .42f
                activeGps -> 1f
                which == OverlayQuickAction.REAL_GPS ||
                    which == OverlayQuickAction.VIRTUAL_GPS -> .72f
                else -> 1f
            }
        }
    }

    private fun refreshQuick() {
        if (root == null) return
        val prefs = AppPreferences(context)
        val primary = OverlayQuickPolicy.primary(lastPhase)
        val routeReady = PreparedRouteState.route.value?.stops == prefs.routeDraft() &&
            prefs.routeDraft().isNotEmpty()
        val flowerReady = PreparedRouteState.flowers.value?.stops == prefs.flowerDraft() &&
            prefs.flowerDraft().isNotEmpty()
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
            lastPhase == WalkPhase.PARKED ->
                RouteSurfaceStatusPolicy.parkedOverlaySummary(lastStatus, lastMode)
            lastStatus.contains("失敗") || lastStatus.contains("無法") ||
                lastStatus.contains("請先") || lastStatus.contains("請在") ||
                lastStatus.contains("找不到") || lastStatus.contains("中斷") ||
                lastStatus.contains("確認") -> lastStatus.take(32)
            visibleMode != null -> "待命 · ${if (visibleMode == WalkMode.FLOWERS) "尋花" else "路徑"}已就緒"
            routeReady && flowerReady -> "待命 · 請在主 App 選擇模式"
            else -> "待命 · 請在主 App 預覽道路"
        }
        val progress = com.naos.pikminbee.location.LocationStatus.progress.value
        val journey = if (lastPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED) &&
            progress.totalMetres > 0.0) {
            " · ${progress.activeLegLabel}（共 ${progress.waypointCount} 點）" +
                " · ${(progress.fraction * 100).toInt()}%"
        } else ""
        val selectedProfile = WalkSpeedState.profile.value
        val applied = com.naos.pikminbee.location.LocationStatus.appliedSpeedKmh.value
        val speedLabel = if (applied > 0.0 &&
            lastPhase in listOf(WalkPhase.MOVING, WalkPhase.PAUSED)) {
            val prefix = if (selectedProfile.mode == WalkSpeedMode.RANGE) "區間 · " else ""
            prefix + WalkSpeedProfile.number(applied) + " km/h"
        } else selectedProfile.display
        quickSummary?.text = "$label · $speedLabel$journey"
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
        iconUpdate(OverlayQuickAction.REAL_GPS, OverlayGlyph.REAL,
            "切換真實 GPS 模式，停止模擬定位及目前行走")
        iconUpdate(OverlayQuickAction.VIRTUAL_GPS, OverlayGlyph.VIRTUAL,
            if (prefs.lastVirtualPosition() != null ||
                com.naos.pikminbee.location.LocationStatus.mockActive.value)
                "切換到上次虛擬位置，不會自動繼續行走"
            else "尚無上次虛擬位置，點選以開啟地圖選點")
        // Both icons stay tappable: without a saved virtual point we guide the
        // user to choose one instead of swallowing touches on a disabled view.
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
            contentDescription = "點一下收合懸浮控制；長按關閉懸浮視窗，不停止行走"
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleExpanded() }
            setOnLongClickListener { sendAction(HIDE); true }
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
            text = "今日步數 —（尚未取得）"
            textSize = 11f
            setTextColor(colors().muted.toArgb())
            setPadding(dp(9), dp(1), dp(9), dp(7))
            maxLines = 2
        }
        quickSteps = stepsLine
        actions.addView(stepsLine)

        val buttons = (OverlayQuickPolicy.topRow + OverlayQuickPolicy.bottomRow).map { which ->
            when (which) {
                OverlayQuickAction.PRIMARY ->
                    Triple(which, OverlayGlyph.PLAY, "開始／暫停／繼續")
                OverlayQuickAction.STOP ->
                    Triple(which, OverlayGlyph.STOP, "停止行走")
                OverlayQuickAction.REAL_GPS ->
                    Triple(which, OverlayGlyph.REAL, "還原真實定位")
                OverlayQuickAction.VIRTUAL_GPS ->
                    Triple(which, OverlayGlyph.VIRTUAL, "恢復虛擬定位")
                OverlayQuickAction.SPEED ->
                    Triple(which, OverlayGlyph.SPEED, "切換三種速度預設；自訂與區間速度請在主畫面設定")
                OverlayQuickAction.MAP ->
                    Triple(which, OverlayGlyph.MAP, "打開地圖")
                OverlayQuickAction.CLOSE ->
                    Triple(which, OverlayGlyph.CLOSE, "關閉懸浮")
            }
        }
        buttons.chunked(4).forEach { group ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { (which, glyph, label) ->
                row.addView(iconAction(which, glyph, label) {
                    when (which) {
                        OverlayQuickAction.PRIMARY -> performQuickAction()
                        OverlayQuickAction.STOP -> sendAction(STOP_WALK)
                        OverlayQuickAction.REAL_GPS -> clickGps(OverlayQuickAction.REAL_GPS)
                        OverlayQuickAction.VIRTUAL_GPS -> clickGps(OverlayQuickAction.VIRTUAL_GPS)
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
            elevation = if (expanded) dp(6).toFloat() else 0f
            if (expanded) setPadding(dp(7), dp(5), dp(7), dp(5))
            else setPadding(0, 0, 0, 0)
        }
        panel?.animate()?.cancel()
        panel?.visibility = if (expanded) View.VISIBLE else View.GONE
        beeButton?.visibility = if (expanded) View.GONE else View.VISIBLE
        if (expanded) {
            // Opening the panel always revalidates HC permission and today's
            // aggregate, rather than briefly presenting an old private value.
            dailyReadJob?.cancel()
            dailyReadJob = null
            dailyStepsCache.invalidate()
            updateStepSummary()
            panel?.alpha = 0f
            panel?.translationY = dp(10).toFloat()
            panel?.animate()?.alpha(1f)?.translationY(0f)
                ?.setDuration(NaosDesignSystem.FAST_MS.toLong())?.start()
            refreshQuick()
            requestDailySteps(force = true)
        } else {
            // A collapsed overlay need not retain a cached health total.
            dailyReadJob?.cancel()
            dailyReadJob = null
            dailyStepsCache.invalidate()
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
        dailyReadJob?.cancel()
        dailyStepsCache.invalidate()
        dailyReadScope.cancel()
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
