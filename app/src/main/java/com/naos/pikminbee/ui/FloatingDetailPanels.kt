package com.naos.pikminbee.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.naos.pikminbee.data.AppPreferences
import com.naos.pikminbee.domain.WalkPhase
import com.naos.pikminbee.domain.WalkSpeedMode
import com.naos.pikminbee.domain.WalkSpeedPreset
import com.naos.pikminbee.domain.WalkSpeedProfile
import com.naos.pikminbee.lan.LanDisplay

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Shared native widgets for the compact, non-scrolling detail surfaces. */
internal class OverlayDetailWidgets(
    private val context: Context,
    private val palette: () -> NaosPalette,
) {
    private val text = mutableListOf<TextView>()
    private val actions = mutableListOf<TextView>()
    private val icons = mutableListOf<Pair<ImageView, OverlayGlyph>>()
    fun dp(n: Int) = (context.resources.displayMetrics.density * n).toInt()

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    fun label(size: Float = 12f, lines: Int = 2) = TextView(context).apply {
        textSize = size
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(5), dp(2), dp(5), dp(2))
        setTextColor(palette().ink.toArgb())
    }.also { text += it }

    fun button(title: String, size: Float = 12f, click: () -> Unit) =
        label(size, 2).apply {
            text = title
            contentDescription = title.replace('\n', ' ')
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }.also { actions += it; paintButton(it) }

    fun icon(glyph: OverlayGlyph, title: String, click: () -> Unit) =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = title
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }.also {
            icons += it to glyph
            paintIcon(it, glyph)
        }

    fun addWeighted(row: LinearLayout, view: View) {
        row.addView(view, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(3) })
    }
    fun addIcon(row: LinearLayout, view: View) =
        row.addView(view, LinearLayout.LayoutParams(dp(48), dp(48)))
    fun select(view: TextView, selected: Boolean) {
        if (view.isSelected == selected) return
        view.isSelected = selected
        paintButton(view)
    }
    fun background(selected: Boolean = false) = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(if (selected) palette().accent.toArgb() else palette().soft.toArgb())
    }
    private fun paintButton(view: TextView) {
        view.background = background(view.isSelected)
        view.setTextColor(if (view.isSelected) palette().onAccent.toArgb() else palette().ink.toArgb())
    }
    private fun paintIcon(view: ImageView, glyph: OverlayGlyph) {
        view.setImageDrawable(NaosOverlayActionIcon(glyph, palette().ink.toArgb(), dp(22)))
        view.background = background()
    }
    fun repaint() {
        text.forEach { it.setTextColor(palette().ink.toArgb()) }
        actions.forEach(::paintButton)
        icons.forEach { (view, glyph) -> paintIcon(view, glyph) }
    }
}

private fun View.visible(show: Boolean) {
    val next = if (show) View.VISIBLE else View.GONE
    if (visibility != next) visibility = next
}
private fun TextView.updateText(value: String) {
    if (text.toString() != value) text = value
}

/** Display is derived from confirmed shared profile, not the last tap. */
internal class FloatingSpeedPanel(
    private val context: Context,
    private val palette: () -> NaosPalette,
    private val submit: (WalkSpeedProfile) -> Boolean,
    private val requestInput: (EditText?) -> Unit,
    private val resize: () -> Unit,
) {
    private val widgets = OverlayDetailWidgets(context, palette)
    val root = widgets.column().apply { setPadding(widgets.dp(2), widgets.dp(2), widgets.dp(2), widgets.dp(4)) }
    private val current = widgets.label(14f).apply { setTypeface(typeface, Typeface.BOLD) }
    private val presets = linkedMapOf<WalkSpeedPreset, TextView>()
    private val custom: TextView
    private val editorRow = widgets.row()
    private val editor = EditText(context).apply {
        textSize = 14f
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        imeOptions = EditorInfo.IME_ACTION_DONE
        minHeight = widgets.dp(48)
        hint = "km/h"
        contentDescription = "自訂速度（km/h）"
        setPadding(widgets.dp(8), 0, widgets.dp(8), 0)
        isFocusableInTouchMode = false
        isFocusable = false
        setOnClickListener { requestInput(this) }
    }
    private var profile = WalkSpeedProfile.preset(WalkSpeedPreset.DEFAULT)
    private var customOpen = false

    init {
        root.addView(current)
        val choices = widgets.row()
        WalkSpeedPreset.entries.forEach { preset ->
            val button = widgets.button(preset.label + "\n" +
                WalkSpeedProfile.number(preset.kilometresPerHour), 12f) {
                closeEditor()
                submit(WalkSpeedProfile.preset(preset))
            }
            presets[preset] = button
            widgets.addWeighted(choices, button)
        }
        custom = widgets.button("自訂") { toggleEditor() }
        widgets.addWeighted(choices, custom)
        root.addView(choices)
        widgets.addWeighted(editorRow, editor)
        val apply = widgets.button("套用") { applyCustom() }
        editorRow.addView(apply, LinearLayout.LayoutParams(widgets.dp(64), WRAP))
        editor.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { applyCustom(); true } else false
        }
        root.addView(editorRow)
        editorRow.visible(false)
        root.visible(false)
        repaint()
    }

    fun render(confirmed: WalkSpeedProfile, applied: Double, phase: WalkPhase) {
        profile = confirmed
        val appliedLabel = com.naos.pikminbee.domain.RouteSurfaceStatusPolicy
            .appliedRouteSpeedLabel(phase, applied)
        current.updateText("目前：${appliedLabel ?: confirmed.display}")
        presets.forEach { (preset, button) ->
            widgets.select(button, confirmed.mode == WalkSpeedMode.PRESET && confirmed.preset == preset)
        }
        widgets.select(custom, confirmed.mode == WalkSpeedMode.FIXED)
    }

    private fun toggleEditor() {
        if (customOpen) { closeEditor(); return }
        customOpen = true
        val initial = if (profile.mode == WalkSpeedMode.FIXED) profile.fixedKmh
            else AppPreferences(context).lastCustomSpeedKmh()
        editor.setText(WalkSpeedProfile.number(initial))
        editor.error = null
        editorRow.visible(true)
        resize()
        // Typing requires a second explicit tap on the input field.
    }

    private fun applyCustom() {
        val next = WalkSpeedProfile.tryFixed(editor.text.toString())
        if (next == null) {
            editor.error = "請輸入有效速度（km/h）"
            return
        }
        if (submit(next)) closeEditor()
    }

    fun closeEditor() {
        requestInput(null)
        if (!customOpen) return
        customOpen = false
        editorRow.visible(false)
        editor.error = null
        resize()
    }

    fun repaint() {
        widgets.repaint()
        editor.setTextColor(palette().ink.toArgb())
        editor.setHintTextColor(palette().muted.toArgb())
        editor.background = widgets.background()
    }
}

/** Mutually exclusive pairing, approval and session views; never expose credentials in URLs. */
internal class FloatingWifiPanel(
    context: Context,
    private val palette: () -> NaosPalette,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onCopy: () -> Unit,
    onBrowser: () -> Unit,
    onApprove: (String, Boolean) -> Unit,
    onDeny: () -> Unit,
    onDisconnect: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    private val widgets = OverlayDetailWidgets(context, palette)
    val root = widgets.column().apply { setPadding(widgets.dp(2), widgets.dp(2), widgets.dp(2), widgets.dp(4)) }
    private val summary = widgets.label(14f).apply { setTypeface(typeface, Typeface.BOLD) }
    private val power = widgets.icon(OverlayGlyph.POWER, "開啟或關閉 Wi-Fi") {
        if (active) onDisable() else onEnable()
    }
    private val addressRow = widgets.row()
    private val address = widgets.label(12f)
    private val pin = widgets.label(23f, 1).apply {
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = .12f
    }
    private val browser = widgets.button("開啟瀏覽器") { onBrowser() }
    private val device = widgets.label(12f)
    private var approvalId = ""
    private var renewal = false
    private val shareChoice = CheckBox(context).apply {
        text = "分享最近真實位置（選填）"
        textSize = 12f
        minHeight = widgets.dp(48)
        setTextColor(palette().ink.toArgb())
        buttonTintList = ColorStateList.valueOf(palette().accent.toArgb())
    }
    private val sharingNotice = widgets.label(11f, 2).apply {
        text = "僅限本次瀏覽器，快照最多 30 分鐘；HTTP 未加密。"
    }
    private val approvalRow = widgets.row()
    private val reject = widgets.button("拒絕", click = onDeny)
    private val approve = widgets.button("核准") {
        if (approvalId.isNotBlank()) onApprove(approvalId, shareChoice.isChecked)
    }

    private fun updateApprovalButton() {
        approve.updateText(if (shareChoice.isChecked) "核准並分享" else if (renewal) "核准續期" else "核准")
        approve.contentDescription = approve.text
    }
    private val disconnect = widgets.button("中斷連線", click = onDisconnect)
    private val details = widgets.button("在 App 查看", click = onSettings)
    private var active = false
    private var structure: List<Any?> = emptyList()

    init {
        val header = widgets.row()
        widgets.addWeighted(header, summary)
        widgets.addIcon(header, widgets.icon(OverlayGlyph.HELP, "配對說明", onHelp))
        widgets.addIcon(header, power)
        root.addView(header)
        widgets.addWeighted(addressRow, address)
        widgets.addIcon(addressRow, widgets.icon(OverlayGlyph.COPY, "複製網址", onCopy))
        root.addView(addressRow)
        root.addView(pin)
        root.addView(browser)
        root.addView(device)
        root.addView(shareChoice)
        root.addView(sharingNotice)
        shareChoice.setOnCheckedChangeListener { _, _ -> updateApprovalButton() }
        widgets.addWeighted(approvalRow, reject)
        widgets.addWeighted(approvalRow, approve)
        root.addView(approvalRow)
        root.addView(disconnect)
        root.addView(details)
        root.visible(false)
    }

    /** Return true only when the panel may need a new layout, not every countdown tick. */
    fun render(lan: LanDisplay): Boolean {
        active = lan.active
        val state = lan.connection
        val view = OverlayPanelPolicy.wifiView(active, state)
        val validAddress = OverlayPanelPolicy.browserPage(active, lan.address) != null
        val pairing = view == OverlayWifiView.PAIRING
        val approval = view == OverlayWifiView.APPROVAL
        val session = view == OverlayWifiView.SESSION
        val awaiting = view == OverlayWifiView.AWAITING_BROWSER
        val error = view == OverlayWifiView.ERROR
        val deviceText = when {
            approval -> state.pendingDevice
            session -> listOf(state.device, if (lan.physicalSharingAllowed) "位置分享已開啟" else "位置未分享")
                .filter { it.isNotBlank() }.joinToString(" · ")
            awaiting -> "請回原瀏覽器完成連線"
            error -> lan.note
            view == OverlayWifiView.OFF -> "點電源開啟控制"
            else -> ""
        }
        summary.updateText(state.label + if (state.hasSession) " · " + state.countdown else "")
        power.contentDescription = if (active) "關閉 Wi-Fi 並撤銷配對" else "開啟 Wi-Fi"
        power.isEnabled = view != OverlayWifiView.STARTING
        power.alpha = if (power.isEnabled) 1f else .42f
        address.updateText(if (pairing && validAddress) lan.address else "")
        addressRow.visible(pairing && validAddress)
        // A pending request, confirmed session or awaiting-browser state must not show the PIN.
        pin.updateText(if (pairing) lan.pairingPin.chunked(3).joinToString(" ") else "")
        pin.visible(pairing && lan.pairingPin.isNotBlank())
        browser.visible(pairing && validAddress)
        device.updateText(deviceText)
        device.visible(deviceText.isNotBlank())
        renewal = state.renewal
        if (approvalId != state.approvalId) {
            approvalId = state.approvalId
            shareChoice.isChecked = false
        }
        updateApprovalButton()
        shareChoice.visible(approval && approvalId.isNotBlank())
        sharingNotice.visible(approval && approvalId.isNotBlank())
        approvalRow.visible(approval && approvalId.isNotBlank())
        disconnect.visible(session)
        details.visible(error || (pairing && !validAddress))
        val next = listOf(view, active, validAddress, pairing && lan.pairingPin.isNotBlank(),
            if (pairing) lan.address else "", deviceText, state.hasSession, state.label)
        val changed = structure != next
        structure = next
        return changed
    }

    fun repaint() {
        widgets.repaint()
        shareChoice.setTextColor(palette().ink.toArgb())
        shareChoice.buttonTintList = ColorStateList.valueOf(palette().accent.toArgb())
    }
}
